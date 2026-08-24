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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
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
 * Failures are not caught here. They travel to [GpsRepository]'s caller, which turns them into
 * something the screen can say; this file's job is only to make sure the log knows what happened
 * first. A `try`/`catch` around the `emitAll` would also catch failures thrown by the *collector*,
 * which arrive back up through `emit` -- `onCompletion` observes the ending without intercepting
 * it.
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
  // the read, so the finally has something to run. This cannot become an `onCompletion`: that
  // runs on the coroutine that is blocked, so it could not run until the read it needs to
  // interrupt had already returned.
  currentCoroutineContext().job.invokeOnCompletion {
    // Logged rather than swallowed: a close that fails is how a link that was already gone tends
    // to announce itself, and it is the last thing that happens before the flow disappears.
    runCatching { socket.close() }
      .onFailure { Log.w(TAG, "Closing the socket to $address failed", it) }
  }

  var fixes = 0
  val connected = TimeSource.Monotonic.markNow()
  socket.use {
    emitAll(
      socket.inputStream
        .asSource()
        .buffered()
        .nmeaSentences()
        .positions()
        .onEach { fixes++ }
        // Attached to the read rather than to `nmeaFixes` itself so that the two counters above
        // stay per-collection; hoisting them out of the builder to reach an outer operator would
        // share them across every collection of what is a cold flow.
        .onCompletion { cause -> report(fixes, connected, cause) }
    )
  }
}
  .flowOn(dispatcher)

/**
 * The one place a feed's ending is reported, whichever way it ended.
 *
 * Zero fixes is the interesting case in all three branches and the reason this is logged at all: it
 * is what a device that connected but is not a GPS looks like, and what a receiver with no sky view
 * looks like, and nothing on screen tells those apart.
 */
@SuppressLint("MissingPermission")
private fun BluetoothDevice.report(
  fixes: Int,
  connected: TimeSource.Monotonic.ValueTimeMark,
  cause: Throwable?,
) {
  val ending = "after $fixes fixes in ${connected.elapsedNow()}"
  when (cause) {
    // The stream ended cleanly, which for a receiver means it stopped sending.
    null -> Log.i(TAG, "Feed from $address ended $ending")
    // The screen let go of the flow -- a disconnect, or a tab change. The ordinary ending, and not
    // something to report as a fault; this is the case the old `catch (IOException)` reached only
    // by not being a catch-all.
    is CancellationException -> Log.i(TAG, "Feed from $address cancelled $ending")
    // Everything else: out of range, switched off mid-read, or the permission revoked from
    // Settings while connected. `diagnostics()` rather than the bare address, because the device's
    // bond state and service list are what tell those apart.
    else -> Log.e(TAG, "Read from ${diagnostics()} failed $ending", cause)
  }
}

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
  return attempt("secure") { createRfcommSocketToServiceRecord(SPP_UUID) }
    .recoverCatching { secureFailure ->
      // Warn rather than debug: this is the single most useful line in the log when a device
      // connects on one phone and not another, and it is invisible from the screen.
      Log.w(TAG, "Secure socket to $address refused, retrying insecure", secureFailure)
      attempt("insecure") { createInsecureRfcommSocketToServiceRecord(SPP_UUID) }
        .onFailure {
          // The secure attempt is the one that says why the device refused, so it leads; without
          // it the report would be whatever the second attempt happened to hit.
          it.addSuppressed(secureFailure)
          Log.e(TAG, "Both sockets to ${diagnostics()} failed", it)
        }
        .getOrThrow()
    }
    .getOrThrow()
}

/**
 * One connect attempt: connects the socket [open] hands back, or closes it again and says why.
 *
 * Closing on failure is the part worth keeping honest. A `BluetoothSocket` that failed to connect
 * still holds a file descriptor, and the caller goes straight on to open a second one.
 */
@SuppressLint("MissingPermission")
private fun BluetoothDevice.attempt(
  kind: String,
  open: () -> BluetoothSocket,
): Result<BluetoothSocket> {
  // Outside the catch, matching what a failure here means: the socket could not be created at all,
  // which a second one of a different kind will not fix.
  val socket = open()
  return runCatchingIo { socket.also { it.connect() } }
    .onSuccess { Log.i(TAG, "Connected to $address, $kind socket") }
    .onFailure {
      // Swallowed deliberately, unlike the close in `nmeaFixes`: a close that fails after a
      // connect that failed adds nothing the connect failure has not already said, and it must not
      // replace it as the reported cause.
      runCatching { socket.close() }
    }
}

/**
 * `runCatching`, narrowed to the failure this file is about.
 *
 * Not the stdlib one, which catches `Throwable` -- and the throwable that matters most here is not
 * an `IOException` at all. `BLUETOOTH_CONNECT` can be revoked from Settings between listing the
 * paired devices and connecting to one, and it arrives as a `SecurityException`. That has to
 * propagate, not be answered by retrying on a second socket that will be refused the same way and
 * then logged as "both sockets failed", which blames the radio for a permission problem.
 */
private inline fun <T> runCatchingIo(block: () -> T): Result<T> =
  try {
    Result.success(block())
  } catch (failure: IOException) {
    Result.failure(failure)
  }
