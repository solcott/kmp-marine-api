package io.github.solcott.marineapi.example.android.ui.gps

import io.github.solcott.marineapi.example.android.data.BluetoothAvailability
import io.github.solcott.marineapi.example.android.data.PairedDevice
import io.github.solcott.marineapi.nmea.io.PositionFix

/**
 * Everything the Bluetooth screen draws, in one value.
 *
 * [connection] is null while there is nothing connected, which is what puts the device picker on
 * screen. Picking is this screen's own state rather than the connection's, so the two are separate
 * fields instead of one sealed hierarchy that would have to repeat the device list in every case.
 */
data class GpsUiState(
  val availability: BluetoothAvailability = BluetoothAvailability.NO_RADIO,
  val hasPermission: Boolean = false,
  val devices: List<PairedDevice> = emptyList(),
  val connectedTo: PairedDevice? = null,
  val connection: Connection? = null,
)

/** What the connection is doing. */
sealed interface Connection {

  data object Connecting : Connection

  /**
   * [count] is every fix since connecting, where [recent] holds only the last few, newest first.
   *
   * A log is finite and is read to the end before anything is shown; a receiver runs until you walk
   * away from it, so this keeps the latest and a bounded tail behind it.
   */
  data class Streaming(val latest: PositionFix, val count: Int, val recent: List<PositionFix>) :
    Connection

  /** The receiver stopped sending without failing: switched off, or simply done. */
  data object Ended : Connection

  /**
   * [message] is never null, unlike the exception it came from.
   *
   * `IOException.getMessage()` is null often enough on this path to matter -- a socket closed under
   * a blocked read raises one with nothing in it -- and a failure that renders as an empty string
   * is indistinguishable on screen from the feed ending normally. `Throwable.describe()` is what
   * guarantees there is something to show; logcat has the stack trace that goes with it.
   */
  data class Failed(val message: String) : Connection
}
