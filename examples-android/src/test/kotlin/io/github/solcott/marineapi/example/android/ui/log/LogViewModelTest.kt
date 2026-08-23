package io.github.solcott.marineapi.example.android.ui.log

import io.github.solcott.marineapi.example.android.fake.FakeNmeaLogRepository
import io.github.solcott.marineapi.example.android.fake.MainDispatcherRule
import io.github.solcott.marineapi.example.android.fake.fixAt
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class LogViewModelTest {

  @get:Rule val mainDispatcher = MainDispatcherRule()

  private val logs = FakeNmeaLogRepository()
  /**
   * Constructed lazily, which matters: a JUnit rule runs after the test instance is built, so a
   * ViewModel created in a field initializer would start collecting before there is a main
   * dispatcher to collect on, and its state would never move off the initial value.
   */
  private val viewModel by lazy { LogViewModel(logs) }

  @Test
  fun startsWithNothingPicked() {
    assertEquals(LogUiState.Idle, viewModel.state.value)
  }

  @Test
  fun readsAFileIntoFixes() = runTest {
    logs.contents = { (1..3).map(::fixAt).asFlow() }

    viewModel.read("content://logs/one")

    val state = assertIs<LogUiState.Read>(viewModel.state.value)
    assertEquals(3, state.total)
    assertEquals(listOf(fixAt(1), fixAt(2), fixAt(3)), state.shown)
  }

  @Test
  fun countsEveryFixButHoldsOnlyTheCappedList() = runTest {
    logs.contents = { (1..2500).map(::fixAt).asFlow() }

    viewModel.read("content://logs/long")

    val state = assertIs<LogUiState.Read>(viewModel.state.value)
    // The count is honest about the whole file; the list stops at MAX_FIXES.
    assertEquals(2500, state.total)
    assertEquals(2000, state.shown.size)
  }

  @Test
  fun reportsAFileItCannotReadRatherThanThrowing() = runTest {
    logs.contents = { flow { throw IOException("Could not open it") } }

    viewModel.read("content://logs/jpeg")

    assertEquals(LogUiState.Failed("Could not open it"), viewModel.state.value)
  }

  @Test
  fun picksTheSameFileTwice() = runTest {
    logs.contents = { (1..2).map(::fixAt).asFlow() }

    viewModel.read("content://logs/one")
    viewModel.read("content://logs/one")

    // The state a StateFlow of picks would conflate away, which is what the old Pick(attempt)
    // counter existed to defeat: two identical picks are two reads.
    assertEquals(listOf("content://logs/one", "content://logs/one"), logs.requested)
  }

  @Test
  fun aSecondPickSupersedesAReadStillRunning() = runTest {
    logs.contents = { uri ->
      if (uri.endsWith("slow"))
        flow {
          emit(fixAt(1))
          awaitCancellation()
        }
      else (1..2).map(::fixAt).asFlow()
    }

    viewModel.read("content://logs/slow")
    assertEquals(LogUiState.Reading, viewModel.state.value)

    viewModel.read("content://logs/quick")

    val state = assertIs<LogUiState.Read>(viewModel.state.value)
    assertEquals(2, state.total)
  }
}
