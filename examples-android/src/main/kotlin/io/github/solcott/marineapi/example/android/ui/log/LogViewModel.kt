package io.github.solcott.marineapi.example.android.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.solcott.marineapi.example.android.data.NmeaLogRepository
import io.github.solcott.marineapi.nmea.io.PositionFix
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Reads whichever log was picked last.
 *
 * The read runs in [viewModelScope], and that is the whole reason this class exists: the screen it
 * replaced drove each read from a `LaunchedEffect`, so **a rotation threw the file away** and
 * started again from an empty screen. Here the ViewModel outlives the activity and the read simply
 * carries on underneath it.
 *
 * A [Channel] carries the picks rather than a `StateFlow`, because a `StateFlow` conflates equal
 * values -- picking the same file twice would set the same `Uri` and nothing would happen. The
 * screen this replaced worked around exactly that with a `Pick(uri, attempt)` counter whose only
 * job was to make two identical picks distinguishable; a channel makes each pick an event, and the
 * counter is gone.
 *
 * [flatMapLatest] is the other half: picking a second file cancels the first read before the new
 * one emits, so there is never a moment where two reads are racing to set the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(private val logs: NmeaLogRepository) : ViewModel() {

  private val picks = Channel<String>(Channel.CONFLATED)

  val state: StateFlow<LogUiState> =
    picks
      .receiveAsFlow()
      .flatMapLatest(::reading)
      // Eagerly, not WhileSubscribed: the point of moving this off the screen is that a read keeps
      // going while nothing is collecting it, which is what a rotation looks like from here.
      .stateIn(viewModelScope, SharingStarted.Eagerly, LogUiState.Idle)

  /** [uri] is the stringified `Uri` the picker returned; see [NmeaLogRepository] for why. */
  fun read(uri: String) {
    picks.trySend(uri)
  }

  /**
   * The result is emitted in one piece rather than streamed: a long log would otherwise recompose
   * the list thousands of times to show rows nobody can read as they go past.
   *
   * `catch` rather than a `try`/`catch` around the collection, because it declines to catch the
   * collector's own cancellation -- so a superseded read stays cancellable and does not report
   * itself as a failure on the way out.
   */
  private fun reading(uri: String): Flow<LogUiState> = flow {
    emit(LogUiState.Reading)
    val shown = mutableListOf<PositionFix>()
    var total = 0
    logs.fixes(uri).collect { fix ->
      total++
      if (shown.size < MAX_FIXES) shown += fix
    }
    emit(LogUiState.Read(shown, total))
  }
    .catch { failure -> emit(LogUiState.Failed(failure.message)) }

  private companion object {
    /**
     * A picked file is arbitrary and may hold any number of fixes, so the list of formatted lines
     * is bounded. `LazyColumn` only composes what is on screen, so this caps memory rather than
     * drawing.
     */
    const val MAX_FIXES = 2000
  }
}
