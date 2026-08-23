package io.github.solcott.marineapi.example

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Reads NMEA from a serial port, a device node, or the first port that appears to be carrying it.
 *
 * ```
 * ./gradlew :examples:runSerialPortExample                        # scan every port
 * ./gradlew :examples:runSerialPortExample --args="COM3"          # a named port
 * ./gradlew :examples:runSerialPortExample --args="/dev/rfcomm0"  # a device node, no driver
 * ```
 *
 * The point of this one is the adapter. kotlinx-io has no notion of a serial port -- or of a
 * socket, for that matter -- so the library takes a `Source` and leaves opening one to the
 * platform. On the JVM anything with an `InputStream` becomes a `Source` in two calls:
 * ```
 * port.inputStream.asSource().buffered().nmeaSentences()
 * ```
 *
 * The same two calls serve a TCP socket, a named pipe or a process's stdout. Other platforms supply
 * their own `Source`, which is what keeps the reading API off the JVM's IO classes.
 *
 * ### Why a path is accepted as well as a port name
 *
 * A Bluetooth GPS -- a Garmin GLO, say -- speaks the Serial Port Profile, and the OS presents that
 * as an RFCOMM endpoint: `rfcomm bind /dev/rfcomm0 <MAC> 1` on Linux, a virtual COM port on
 * Windows. An RFCOMM endpoint has no line discipline to configure: baud, parity and stop bits are
 * meaningless over a radio link that has already framed the bytes. So on anything with device nodes
 * the driver buys nothing, and a plain [FileInputStream] reads the GPS with no native library in
 * the picture at all.
 *
 * That matters more than it sounds, because the driver is not always loadable: nrjavaserial 5.2.1
 * ships one macOS binary and it is x86_64 only, so on Apple silicon this whole file works *only*
 * through the path branch. macOS will not pair a Garmin GLO in the first place -- Garmin do not
 * support it -- but the same is true of any Bluetooth NMEA source there.
 *
 * On Windows, pairing such a device typically creates several virtual COM ports, incoming as well
 * as outgoing. Name the outgoing one: [findPortWithNmea] has to open each port to find out what is
 * on it, and an incoming port can sit there contributing nothing but a timeout.
 *
 * A real RS-232 GPS is the other case, and that one does need the driver, at [BAUD_RATE] 8N1.
 */
fun main(args: Array<String>) {
  val target = args.firstOrNull()
  val feed =
    when {
      target == null -> findPortWithNmea()?.let { Feed(it.name, it.inputStream) }
      // `exists()` rather than a prefix test: this is the branch for anything the filesystem can
      // open, and which names those are is the platform's business, not this file's. On Windows
      // "COM3" is not a file, so a port name falls through to the driver below.
      File(target).exists() -> Feed(target, FileInputStream(target))
      else -> openPort(target)?.let { Feed(it.name, it.inputStream) }
    }

  if (feed == null) {
    println(target?.let { "Could not open $it." } ?: "No serial port with NMEA data found.")
    return
  }

  println("Reading ${feed.name}; press CTRL-C to stop.")
  feed.stream.use { readNmea(it) }
}

/** What to read, and what to call it on screen. */
private class Feed(val name: String, val stream: InputStream)

/** Prints every sentence on [stream] until it ends or the process is killed. */
private fun readNmea(stream: InputStream) = runBlocking {
  stream
    .asSource()
    .buffered()
    // A serial port blocks between sentences -- at 4800 baud, for most of the time. Reading it
    // on the collecting coroutine would block whatever else that coroutine is doing.
    .nmeaSentences()
    .flowOn(Dispatchers.IO)
    .collect { println(it.toNmeaString()) }
}

/** Opens one port by name, or returns null if there is no such port or it is already in use. */
private fun openPort(name: String): SerialPort? = runCatching {
  CommPortIdentifier.getPortIdentifier(name).openConfigured()
}
  .getOrNull()

/**
 * Opens each serial port in turn and keeps the first whose output parses as NMEA.
 *
 * A port carrying nothing, or carrying something else, looks the same as one that is simply quiet,
 * so the only way to tell is to read and see.
 */
private fun findPortWithNmea(): SerialPort? {
  val identifiers = CommPortIdentifier.getPortIdentifiers()
  while (identifiers.hasMoreElements()) {
    val id = identifiers.nextElement() as CommPortIdentifier
    if (id.portType != CommPortIdentifier.PORT_SERIAL) continue

    val port = runCatching { id.openConfigured() }.getOrNull() ?: continue

    println("Scanning ${port.name}")
    // `first()` cancels the flow as soon as one sentence parses, and returns null when the stream
    // ends first -- which for a live port means the receive timeout expired with nothing on it.
    val carriesNmea = runCatching {
      runBlocking {
        port.inputStream
          .asSource()
          .buffered()
          .nmeaSentences()
          .flowOn(Dispatchers.IO)
          .firstOrNull() != null
      }
    }
      .getOrDefault(false)

    if (carriesNmea) return port
    port.close()
  }
  return null
}

/**
 * Opens this port at [BAUD_RATE] 8N1, reading in whatever has arrived when [READ_TIMEOUT_MS] is up.
 */
private fun CommPortIdentifier.openConfigured(): SerialPort =
  (open("SerialPortExample", OPEN_TIMEOUT_MS) as SerialPort).apply {
    setSerialPortParams(
      BAUD_RATE,
      SerialPort.DATABITS_8,
      SerialPort.STOPBITS_1,
      SerialPort.PARITY_NONE,
    )
    enableReceiveTimeout(READ_TIMEOUT_MS)
    enableReceiveThreshold(0)
  }

private const val BAUD_RATE = 4800
private const val OPEN_TIMEOUT_MS = 30
private const val READ_TIMEOUT_MS = 1000
