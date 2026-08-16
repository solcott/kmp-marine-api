package io.github.solcott.marineapi.example

import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Reads NMEA from the first serial port that appears to be carrying it.
 *
 * ```
 * ./gradlew :examples:runSerialPortExample
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
 * Needs a driver on the library path; this module depends on nrjavaserial for that.
 */
fun main() {
  val port = findPortWithNmea()
  if (port == null) {
    println("No serial port with NMEA data found.")
    return
  }

  println("Reading ${port.name}; press CTRL-C to stop.")
  runBlocking {
    port.inputStream
      .asSource()
      .buffered()
      // A serial port blocks between sentences -- at 4800 baud, for most of the time. Reading it
      // on the collecting coroutine would block whatever else that coroutine is doing.
      .nmeaSentences()
      .flowOn(Dispatchers.IO)
      .collect { println(it.toNmeaString()) }
  }
}

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

    val port =
      runCatching {
        (id.open("SerialPortExample", OPEN_TIMEOUT_MS) as SerialPort).apply {
          setSerialPortParams(
            BAUD_RATE,
            SerialPort.DATABITS_8,
            SerialPort.STOPBITS_1,
            SerialPort.PARITY_NONE,
          )
          enableReceiveTimeout(READ_TIMEOUT_MS)
          enableReceiveThreshold(0)
        }
      }
        .getOrNull() ?: continue

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

private const val BAUD_RATE = 4800
private const val OPEN_TIMEOUT_MS = 30
private const val READ_TIMEOUT_MS = 1000
