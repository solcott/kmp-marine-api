package io.github.solcott.marineapi.example.android.fake

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Puts a test dispatcher behind `viewModelScope`, which otherwise needs a real main looper.
 *
 * Unconfined rather than standard: both ViewModels start collecting eagerly, and unconfined runs
 * that collection where it is started rather than queuing it, so `state.value` has caught up by the
 * time the call that caused it returns. The alternative is an `advanceUntilIdle()` after every
 * line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) :
  TestWatcher() {

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
