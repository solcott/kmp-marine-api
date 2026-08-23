package io.github.solcott.marineapi.example.android.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import io.github.solcott.marineapi.nmea.io.PositionFix
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import java.io.IOException
import java.util.UUID
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.io.asSource
import kotlinx.io.buffered

/*
 * Reading a Bluetooth GPS -- a Garmin GLO, a Dual XGPS, a Bad Elf -- as a flow of fixes.
 *
 * The whole Bluetooth-specific part is one line, and it is the same shape as the one line that
 * makes MainActivity's file picker work:
 *
 *     socket.inputStream.asSource().buffered()
 *
 * These receivers speak the Serial Port Profile, so what arrives is exactly the NMEA 0183 a wired
 * receiver would put on an RS-232 line -- an ordinary byte stream once the socket is open, with no
 * baud rate to agree on because the radio link has already framed it. Everything after asSource()
 * is the common code every other platform in this repository runs.
 */

/**
 * Everything this file logs, under one tag.
 *
 * `adb logcat -s MarineApiGps` is the whole story of a connection: which socket was tried, what the
 * device said it offered, how long it lasted and how much came out of it. A Bluetooth link fails in
 * ways the screen cannot usefully render -- "read failed, socket might closed" is the same message
 * whether the receiver is out of range, switched off, or was never offering SPP -- so the detail
 * goes here and the screen gets a sentence.
 */
internal const val TAG: String = "MarineApiGps"

/**
 * The Serial Port Profile service UUID, which is the same for every device that offers SPP.
 *
 * This is the "well-known" base UUID with the SPP service's 16-bit id at the front, not a Garmin or
 * a vendor identifier, so it is what you connect to on any NMEA receiver of this kind.
 */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * Everything known about a device before connecting to it, for the log.
 *
 * The three facts here are the ones that turn an opaque `IOException` into a diagnosis. A device in
 * [BluetoothDevice.BOND_NONE] was unpaired behind the app's back, and no socket will open to it. A
 * device whose `uuids` do not include [SPP_UUID] is not a serial device at all -- pairing succeeded
 * because pairing always succeeds, and the connect will be refused. `uuids` is null when the system
 * has not yet fetched the record, which is not the same as an empty one and should not read alike.
 */
@SuppressLint("MissingPermission")
internal fun BluetoothDevice.diagnostics(): String {
  val services =
    uuids?.let { records ->
      val offered = records.map { it.uuid }
      if (SPP_UUID in offered) "offers SPP" else "no SPP among ${offered.size} services"
    } ?: "services not yet known"
  return "${name ?: "unnamed"} ($address, ${bondStateName()}, $services)"
}

/** The [BluetoothDevice.getBondState] constant by name; the raw number says nothing in a log. */
@SuppressLint("MissingPermission")
private fun BluetoothDevice.bondStateName(): String =
  when (bondState) {
    BluetoothDevice.BOND_BONDED -> "paired"
    BluetoothDevice.BOND_BONDING -> "pairing"
    BluetoothDevice.BOND_NONE -> "NOT PAIRED"
    else -> "bond state $bondState"
  }

/**
 * Connects to this device over SPP and correlates what it sends into [PositionFix]es.
 *
 * Cold, like every flow in this library: nothing is connected until the flow is collected, and the
 * socket is closed when collection ends. The dispatcher is passed in rather than defaulted, because
 * this app names `Dispatchers.IO` in exactly one place and it is not here -- see `IoDispatcher`.
 *
 * A receiver sends one fix as a burst of sentences, so `positions()` emits once per burst -- ten
 * times a second on a GLO, which is fast enough that the screen should show the latest rather than
 * accumulate.
 *
 * It takes no [BluetoothAdapter], and the missing call is worth a paragraph. The obvious first line
 * here is `adapter.cancelDiscovery()`, because a scan in progress slows an RFCOMM connect right
 * down -- and on API 31 and up that call is guarded by `BLUETOOTH_SCAN`, so it throws
 * `SecurityException` unless the app has asked the user for the right to scan. Adding
 * `BLUETOOTH_SCAN` to the manifest does not fix it: it is a dangerous permission, so a manifest
 * entry only makes it requestable, and it still has to be granted at runtime.
 *
 * Asking would be the wrong trade anyway. This app does not scan -- it connects to what the system
 * already paired -- so the only discovery that could be running belongs to the Settings app, and
 * cancelling that is not this app's business. The connect is a little slower while a scan happens
 * to be running, and the permission list stays at one.
 */
@SuppressLint("MissingPermission")
internal fun BluetoothDevice.nmeaFixes(dispatcher: CoroutineDispatcher): Flow<PositionFix> = flow {
  val socket = connectSpp()
  // `use {}` alone is not enough to make this cancellable. Cancelling a coroutine that is
  // blocked inside InputStream.read() does not unblock it -- the read returns when the device
  // sends something, or never, if it has gone out of range -- and the finally block cannot run
  // until it does. Closing the socket from the completion handler is what actually interrupts
  // the read, so the finally has something to run.
  currentCoroutineContext().job.invokeOnCompletion { cause ->
    // Logged rather than swallowed: a close that fails is how a link that was already gone tends
    // to announce itself, and it is the last thing that happens before the flow disappears.
    runCatching { socket.close() }
      .onFailure { Log.w(TAG, "Closing the socket to $address failed", it) }
    if (cause != null) Log.d(TAG, "Feed from $address ended: $cause")
  }

  var fixes = 0
  val connected = TimeSource.Monotonic.markNow()
  try {
    socket.use {
      emitAll(it.inputStream.asSource().buffered().nmeaSentences().positions().onEach { fixes++ })
    }
    // Reaching here means the stream ended cleanly, which for a receiver means it stopped sending.
    // Zero fixes is the interesting case and the reason this is logged at all: it is what a device
    // that connected but is not a GPS looks like, and what a receiver with no sky view looks like,
    // and nothing on screen tells those apart.
    Log.i(
      TAG,
      "Feed from $address ended after $fixes fixes in ${connected.elapsedNow()}",
    )
  } catch (failure: IOException) {
    // Not a catch-all: a CancellationException means the screen let go of the flow, which is the
    // normal way this ends and not something to report as a fault.
    Log.e(
      TAG,
      "Read from ${diagnostics()} failed after $fixes fixes in ${connected.elapsedNow()}",
      failure,
    )
    throw failure
  }
}
  .flowOn(dispatcher)

/**
 * Opens an RFCOMM socket to this device, insecurely if it will not take a secure one.
 *
 * The fallback is not paranoia: an authenticated link needs the pairing to have produced a link
 * key, and some receivers pair with no PIN exchange at all, at which point the secure connect is
 * refused. Both calls are public API -- do not reach for the reflective `createRfcommSocket(1)`
 * that circulates for this, which depends on a private method's channel numbering.
 */
@SuppressLint("MissingPermission")
private fun BluetoothDevice.connectSpp(): BluetoothSocket {
  Log.i(TAG, "Connecting to ${diagnostics()}")
  val secure = createRfcommSocketToServiceRecord(SPP_UUID)
  return try {
    secure.also { it.connect() }.also { Log.i(TAG, "Connected to $address, secure socket") }
  } catch (secureFailure: IOException) {
    secure.close()
    // Warn rather than debug: this is the single most useful line in the log when a device
    // connects on one phone and not another, and it is invisible from the screen.
    Log.w(TAG, "Secure socket to $address refused, retrying insecure", secureFailure)
    val insecure = createInsecureRfcommSocketToServiceRecord(SPP_UUID)
    try {
      insecure.also { it.connect() }.also { Log.i(TAG, "Connected to $address, insecure socket") }
    } catch (insecureFailure: IOException) {
      insecure.close()
      // The secure attempt is the one that says why the device refused, so it leads; without it
      // the report would be whatever the second attempt happened to hit.
      insecureFailure.addSuppressed(secureFailure)
      Log.e(TAG, "Both sockets to ${diagnostics()} failed", insecureFailure)
      throw insecureFailure
    }
  }
}
