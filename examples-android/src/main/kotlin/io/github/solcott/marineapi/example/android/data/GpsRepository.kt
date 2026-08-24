package io.github.solcott.marineapi.example.android.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.solcott.marineapi.example.android.di.IoDispatcher
import io.github.solcott.marineapi.nmea.io.PositionFix
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * What the phone's radio can do right now.
 *
 * Three states rather than a `Boolean`, because each has a different thing to do about it and only
 * the last is worth showing a device list for.
 */
enum class BluetoothAvailability {
  /** No Bluetooth hardware at all, or a system that has disabled it outright. */
  NO_RADIO,

  /** There is a radio and it is switched off. Nothing this app can do but say so. */
  DISABLED,

  /** Ready to be asked for paired devices, permission allowing. */
  READY,
}

/**
 * A paired receiver, reduced to the two things the screen and the repository need.
 *
 * A plain data class rather than a `BluetoothDevice` for the same reason [NmeaLogRepository] takes
 * a `String`: the framework type is a stub in a JVM unit test, and keeping it below this line is
 * what lets the ViewModel be tested without Robolectric. The address is the identity -- it is the
 * one thing about a paired device that cannot change, and two receivers of the same model share a
 * name.
 */
data class PairedDevice(val address: String, val label: String)

/** A paired Bluetooth GPS, as a flow of position fixes. */
interface GpsRepository {

  /** Cheap and permission-free; safe to call before asking for anything. */
  fun availability(): BluetoothAvailability

  /** Empty unless `BLUETOOTH_CONNECT` has been granted -- by definition, not by fact. */
  fun pairedDevices(): List<PairedDevice>

  /** Cold: the socket opens when collection starts and closes when it ends. */
  fun fixes(address: String): Flow<PositionFix>
}

/**
 * Reads a receiver that the system's own Bluetooth settings have already paired.
 *
 * **Bonded devices only -- this deliberately never scans.** Discovery would mean asking for
 * `BLUETOOTH_SCAN`, and on API 30 and below for location permission too, which is a lot of
 * permission for a demo to hold when the system's Bluetooth settings already pair devices perfectly
 * well. Pair the GPS there and it shows up here.
 *
 * The adapter is held rather than injected. Both `getSystemService` and `adapter` can return null
 * -- a device with no radio, and a radio the system has disabled -- and a nullable Dagger binding
 * to express that would push the same two nulls onto every consumer. [availability] answers the
 * question they were actually asking.
 */
internal class BluetoothGpsRepository
@Inject
constructor(
  @ApplicationContext context: Context,
  @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : GpsRepository {

  private val adapter: BluetoothAdapter? =
    context.getSystemService(BluetoothManager::class.java)?.adapter

  override fun availability(): BluetoothAvailability =
    when {
      adapter == null -> BluetoothAvailability.NO_RADIO
      !adapter.isEnabled -> BluetoothAvailability.DISABLED
      else -> BluetoothAvailability.READY
    }

  /**
   * Requires `BLUETOOTH_CONNECT` from API 31; the caller asks for it before getting this far, which
   * is why the lint check is suppressed here rather than repeated at every call.
   */
  @SuppressLint("MissingPermission")
  override fun pairedDevices(): List<PairedDevice> =
    adapter
      ?.bondedDevices
      .orEmpty()
      .map { PairedDevice(it.address, it.label()) }
      .sortedBy { it.label }

  override fun fixes(address: String): Flow<PositionFix> {
    // Not `adapter!!`: the radio can be switched off between listing the devices and connecting to
    // one, and a flow that fails with a message is better than a crash on a demo screen.
    val adapter = adapter ?: return flow { error("This device has no Bluetooth radio") }
    return adapter.getRemoteDevice(address).nmeaFixes(dispatcher)
  }
}

/** The device's name, falling back to its address for one that never reported a name. */
@SuppressLint("MissingPermission") private fun BluetoothDevice.label(): String = name ?: address
