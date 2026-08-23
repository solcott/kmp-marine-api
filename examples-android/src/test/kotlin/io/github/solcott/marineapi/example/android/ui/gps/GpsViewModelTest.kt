package io.github.solcott.marineapi.example.android.ui.gps

import io.github.solcott.marineapi.example.android.data.BluetoothAvailability
import io.github.solcott.marineapi.example.android.data.PairedDevice
import io.github.solcott.marineapi.example.android.fake.FakeGpsRepository
import io.github.solcott.marineapi.example.android.fake.MainDispatcherRule
import io.github.solcott.marineapi.example.android.fake.fixAt
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class GpsViewModelTest {

  @get:Rule val mainDispatcher = MainDispatcherRule()

  private val gps = FakeGpsRepository()
  /**
   * Constructed lazily, which matters: a JUnit rule runs after the test instance is built, so a
   * ViewModel created in a field initializer would start collecting before there is a main
   * dispatcher to collect on, and its state would never move off the initial value.
   */
  private val viewModel by lazy { GpsViewModel(gps) }

  private val glo = PairedDevice("00:11:22:33:44:55", "Garmin GLO")

  @Test
  fun offersThePairedDevicesOnceThePermissionIsGranted() = runTest {
    gps.paired = listOf(glo)

    viewModel.refresh(hasPermission = true)

    val state = viewModel.state.value
    assertTrue(state.hasPermission)
    assertEquals(listOf(glo), state.devices)
  }

  @Test
  fun offersNothingWithoutThePermission() = runTest {
    gps.paired = listOf(glo)

    viewModel.onPermissionResult(granted = false)

    // Empty by definition rather than by fact: bondedDevices is unreadable until the grant, so
    // there is nothing worth asking the radio for.
    assertEquals(emptyList(), viewModel.state.value.devices)
  }

  @Test
  fun offersNothingWhileTheRadioIsOff() = runTest {
    gps.radio = BluetoothAvailability.DISABLED
    gps.paired = listOf(glo)

    viewModel.refresh(hasPermission = true)

    assertEquals(BluetoothAvailability.DISABLED, viewModel.state.value.availability)
    assertEquals(emptyList(), viewModel.state.value.devices)
  }

  @Test
  fun keepsTheLatestFixAndABoundedTailOfTheOnesBefore() = runTest {
    // Emits and then stays open, which is what a receiver does; a flow that simply ended would
    // leave the state at Ended and say nothing about what was on screen while it was running.
    gps.feed = {
      flow {
        (1..150).forEach { emit(fixAt(it)) }
        awaitCancellation()
      }
    }

    viewModel.connect(glo)

    val connection = assertIs<Connection.Streaming>(viewModel.state.value.connection)
    assertEquals(150, connection.count)
    assertEquals(fixAt(150), connection.latest)
    assertEquals(100, connection.recent.size)
    // Newest first, so the list reads as a feed rather than as a backlog.
    assertEquals(fixAt(150), connection.recent.first())
    assertEquals(fixAt(51), connection.recent.last())
  }

  @Test
  fun endsWhenTheReceiverStopsSending() = runTest {
    gps.feed = { (1..2).map(::fixAt).asFlow() }

    viewModel.connect(glo)

    assertEquals(Connection.Ended, viewModel.state.value.connection)
  }

  @Test
  fun reportsAFailedLinkWithSomethingToShow() = runTest {
    gps.feed = { flow { throw IOException("read failed, socket might closed") } }

    viewModel.connect(glo)

    val connection = assertIs<Connection.Failed>(viewModel.state.value.connection)
    assertEquals("read failed, socket might closed", connection.message)
  }

  @Test
  fun namesTheExceptionWhenItCarriesNoMessage() = runTest {
    gps.feed = { flow { throw IOException() } }

    viewModel.connect(glo)

    // A blank message renders identically to the feed simply ending, which is the one thing this
    // screen must not confuse.
    assertEquals(Connection.Failed("IOException"), viewModel.state.value.connection)
  }

  @Test
  fun disconnectingGoesBackToThePicker() = runTest {
    gps.paired = listOf(glo)
    gps.feed = {
      flow {
        emit(fixAt(1))
        awaitCancellation()
      }
    }
    viewModel.refresh(hasPermission = true)
    viewModel.connect(glo)
    assertEquals(glo, viewModel.state.value.connectedTo)

    viewModel.disconnect()

    // Cancelling the collection is what closes the socket; the screen falls back to the device
    // list.
    assertNull(viewModel.state.value.connectedTo)
    assertNull(viewModel.state.value.connection)
    assertEquals(listOf(glo), viewModel.state.value.devices)
  }
}
