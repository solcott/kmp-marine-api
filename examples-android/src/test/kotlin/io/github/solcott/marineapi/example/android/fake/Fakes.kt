package io.github.solcott.marineapi.example.android.fake

import io.github.solcott.marineapi.example.android.data.BluetoothAvailability
import io.github.solcott.marineapi.example.android.data.GpsRepository
import io.github.solcott.marineapi.example.android.data.NmeaLogRepository
import io.github.solcott.marineapi.example.android.data.PairedDevice
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalTime

/*
 * The repositories' whole reason for being interfaces. Both ViewModels can be driven from here with
 * no ContentResolver, no Bluetooth radio and no Robolectric -- which is only possible because the
 * repository interfaces speak in Strings rather than in Uri and BluetoothDevice.
 */

class FakeNmeaLogRepository : NmeaLogRepository {

  /** What [fixes] returns, keyed by the uri it was asked for. */
  var contents: (String) -> Flow<PositionFix> = { emptyFlow() }

  /** Every uri asked for, in order, so a test can prove a second pick was actually acted on. */
  val requested: MutableList<String> = mutableListOf()

  override fun fixes(uri: String): Flow<PositionFix> {
    requested += uri
    return contents(uri)
  }
}

class FakeGpsRepository : GpsRepository {

  var radio: BluetoothAvailability = BluetoothAvailability.READY
  var paired: List<PairedDevice> = emptyList()
  var feed: (String) -> Flow<PositionFix> = { emptyFlow() }

  override fun availability(): BluetoothAvailability = radio

  override fun pairedDevices(): List<PairedDevice> = paired

  override fun fixes(address: String): Flow<PositionFix> = feed(address)
}

/**
 * A fix that differs from its neighbours only in the second, which is enough to tell them apart.
 */
fun fixAt(second: Int): PositionFix =
  PositionFix(
    position = Position(60.0 + second / 10_000.0, 25.0),
    time = LocalTime(10, 39, second % 60),
    speedKnots = 4.2,
  )
