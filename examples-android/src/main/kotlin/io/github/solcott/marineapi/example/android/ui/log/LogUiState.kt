package io.github.solcott.marineapi.example.android.ui.log

import io.github.solcott.marineapi.nmea.io.PositionFix

/**
 * What the log screen is showing.
 *
 * `total` is every fix in the file while `shown` stops at the ViewModel's cap, so a long log
 * reports an honest count without holding every fix it counted.
 */
sealed interface LogUiState {

  /** Nothing picked yet. */
  data object Idle : LogUiState

  data object Reading : LogUiState

  data class Read(val shown: List<PositionFix>, val total: Int) : LogUiState

  /** [message] is nullable because `IOException.getMessage()` is. */
  data class Failed(val message: String?) : LogUiState
}
