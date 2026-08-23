package io.github.solcott.marineapi.example.android.ui.gps

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.solcott.marineapi.example.android.data.BluetoothAvailability
import io.github.solcott.marineapi.example.android.data.GpsRepository
import io.github.solcott.marineapi.example.android.data.PairedDevice
import io.github.solcott.marineapi.example.android.data.TAG
import io.github.solcott.marineapi.example.android.ui.common.describe
import io.github.solcott.marineapi.nmea.io.PositionFix
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Connects to a paired receiver and keeps the fixes coming.
 *
 * The counterpart to [io.github.solcott.marineapi.example.android.ui.log.LogViewModel], and the
 * reason both are in one app: nothing below the first few lines of the two repositories knows which
 * one it is reading. A file and a Bluetooth socket are two ways to get an `InputStream`,
 * `asSource()` turns either into the `Source` the library reads, and the fixes come out the same
 * shape.
 *
 * Holding the connection here rather than in a `LaunchedEffect` is what makes a rotation
 * survivable: the socket stays open and the running total keeps running, where the screen this
 * replaced reconnected from scratch every time the phone turned.
 *
 * Switching tabs is still a disconnect, because the ViewModel is scoped to its navigation entry.
 * That is the same behaviour the tab chips had, and it is the honest default -- a receiver held
 * open by a screen nobody is looking at is a battery bill, not a feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GpsViewModel @Inject constructor(private val gps: GpsRepository) : ViewModel() {

  /**
   * The radio and the device list, refreshed only when something could have changed it.
   *
   * Kept as its own flow rather than read inside [combine], which runs on every fix -- ten times a
   * second -- and `bondedDevices` is a binder call.
   */
  private val picker = MutableStateFlow(Picker(gps.availability()))

  private val selected = MutableStateFlow<PairedDevice?>(null)

  /**
   * The device and its connection travel together rather than as two arms of [combine], so there is
   * never a frame showing a newly picked receiver beside the previous one's fixes.
   */
  private val connection: Flow<Pair<PairedDevice?, Connection?>> =
    selected.flatMapLatest { device ->
      feed(device).map { device to it }
    }

  val state: StateFlow<GpsUiState> =
    combine(picker, connection) { radio, (device, link) ->
        GpsUiState(
          availability = radio.availability,
          hasPermission = radio.hasPermission,
          devices = radio.devices,
          connectedTo = device,
          connection = link,
        )
      }
      // Eagerly, so a connection outlives the moment the screen stops collecting -- which is what a
      // rotation is.
      .stateIn(viewModelScope, SharingStarted.Eagerly, GpsUiState())

  /**
   * Re-reads the radio and the paired devices.
   *
   * Called once when the screen appears and again with the answer to the permission dialog. Before
   * the permission is granted `bondedDevices` is empty by definition rather than by fact, so there
   * is nothing worth asking for until then.
   */
  fun refresh(hasPermission: Boolean) {
    val availability = gps.availability()
    picker.value =
      Picker(
        availability = availability,
        hasPermission = hasPermission,
        devices =
          if (hasPermission && availability == BluetoothAvailability.READY) gps.pairedDevices()
          else emptyList(),
      )
  }

  /**
   * The one failure on this screen that leaves no other trace: a denial looks exactly like never
   * having asked, and a second denial is what silently turns the dialog into a no-op.
   */
  fun onPermissionResult(granted: Boolean) {
    if (!granted) Log.w(TAG, "BLUETOOTH_CONNECT denied; no paired device can be read")
    refresh(granted)
  }

  fun connect(device: PairedDevice) {
    selected.value = device
  }

  /** Cancels the collection, which is what closes the socket; see `nmeaFixes`. */
  fun disconnect() {
    selected.value = null
  }

  private fun feed(device: PairedDevice?): Flow<Connection?> {
    if (device == null) return flowOf(null)
    // Outside the builder so the failure path can report how far the user got. Safe because
    // flatMapLatest collects each flow exactly once and builds a new one per selection.
    var count = 0
    return flow {
      emit(Connection.Connecting)
      val recent = ArrayDeque<PositionFix>()
      gps.fixes(device.address).collect { fix ->
        count++
        recent.addFirst(fix)
        if (recent.size > MAX_LIVE_FIXES) recent.removeLast()
        emit(Connection.Streaming(fix, count, recent.toList()))
      }
      // The flow ends when the receiver stops sending -- switched off, or out of range.
      emit(Connection.Ended)
    }
      .catch { failure ->
        // A radio link fails in more ways than a file does: out of range, switched off mid-read,
        // paired but not offering SPP, or the permission revoked from Settings while connected.
        // `nmeaFixes` has already logged what it knows about the socket; this adds what only this
        // side knows, which is how far the user got before it broke.
        Log.e(TAG, "Feed from ${device.address} failed after $count fixes", failure)
        emit(Connection.Failed(failure.describe()))
      }
  }

  /** The picker's half of [GpsUiState], so that a fix arriving does not re-read the radio. */
  private data class Picker(
    val availability: BluetoothAvailability,
    val hasPermission: Boolean = false,
    val devices: List<PairedDevice> = emptyList(),
  )

  private companion object {
    /**
     * A live feed is unbounded, so the tail on screen is not.
     *
     * Lower than the log reader's cap because these arrive ten a second: anything past a screenful
     * is scrolled away before it can be read, and the running total above the list is what actually
     * says how many there have been.
     */
    const val MAX_LIVE_FIXES = 100
  }
}
