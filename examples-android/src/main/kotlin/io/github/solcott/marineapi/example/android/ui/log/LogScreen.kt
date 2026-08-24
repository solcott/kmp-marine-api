package io.github.solcott.marineapi.example.android.ui.log

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.solcott.marineapi.example.android.R
import io.github.solcott.marineapi.example.android.ui.common.Message
import io.github.solcott.marineapi.example.android.ui.common.describe
import io.github.solcott.marineapi.example.android.ui.theme.ExampleTheme
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlinx.datetime.LocalTime

/**
 * Picks a log and shows the fixes in it.
 *
 * All this half does is turn an Activity Result into a call on [LogViewModel]. The `Uri` is
 * stringified on the way in and never travels further: the read grant behind it belongs to this
 * task, so it outlives a rotation exactly as the ViewModel does, and both end together when the
 * process does.
 */
@Composable
fun LogScreen(modifier: Modifier = Modifier, viewModel: LogViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  val openDocument =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) viewModel.read(uri.toString())
    }

  LogScreen(
    state = state,
    // "*/*" rather than "text/plain": a .log or .nmea file is often typed as
    // application/octet-stream, and a narrower filter hides the very files this reads.
    onPick = { openDocument.launch(arrayOf("*/*")) },
    modifier = modifier,
  )
}

/** The stateless half, so that [LogUiState] can be rendered from a `@Preview` without any file. */
@Composable
private fun LogScreen(state: LogUiState, onPick: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Button(
      onClick = onPick,
      enabled = state !is LogUiState.Reading,
      modifier = Modifier.padding(16.dp),
    ) {
      Text(stringResource(R.string.pick_log))
    }

    when (state) {
      is LogUiState.Idle -> Message(stringResource(R.string.instructions))
      is LogUiState.Reading ->
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          CircularProgressIndicator()
        }
      is LogUiState.Failed ->
        Message(
          stringResource(R.string.read_failed, state.message.orEmpty()),
          color = MaterialTheme.colorScheme.error,
        )
      is LogUiState.Read ->
        if (state.total == 0) Message(stringResource(R.string.no_fixes)) else FixList(state)
    }
  }
}

@Composable
private fun FixList(state: LogUiState.Read) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    item {
      Text(
        text = pluralStringResource(R.plurals.fix_count, state.total, state.total),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }
    // The fixes are distinct lines of text with no identity of their own, so the index is the only
    // honest key; two identical readings a second apart are genuinely two separate list entries.
    items(state.shown) { fix ->
      Text(
        text = fix.describe(),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
      )
    }
    if (state.total > state.shown.size) {
      item {
        Text(
          text = stringResource(R.string.more_fixes, state.total - state.shown.size),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun LogReaderPreview() {
  ExampleTheme {
    LogScreen(
      state =
        LogUiState.Read(
          shown =
            listOf(
              PositionFix(Position(60.0, 25.0, 12.5), LocalTime(10, 39, 0), speedKnots = 4.2),
              PositionFix(Position(60.0001, 25.0, 12.4), LocalTime(10, 39, 1), speedKnots = 4.1),
            ),
          total = 2,
        ),
      onPick = {},
    )
  }
}
