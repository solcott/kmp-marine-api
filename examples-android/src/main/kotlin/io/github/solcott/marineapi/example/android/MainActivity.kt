package io.github.solcott.marineapi.example.android

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Reads a log the user picks and lists the fixes in it.
 *
 * The whole Android-specific part is one line:
 * ```
 * contentResolver.openInputStream(uri)!!.asSource().buffered()
 * ```
 *
 * That is the same `InputStream.asSource()` the JVM serial-port example uses, which is the point
 * worth taking away: Android is not a third IO story, it is the JVM one with a different way of
 * naming a file. Everything after that call is the common code every other platform runs.
 *
 * The Storage Access Framework is used rather than a directory scan because it hands back a
 * readable `Uri` with **no permission declared at all** — see the manifest, which asks for none.
 *
 * The UI is Compose. It is the only part of this example that is not about NMEA, so it is kept to
 * one screen with no navigation, no view model and no dependency injection: the reading is driven
 * by a [LaunchedEffect] keyed on the picked file, which is what makes the manual `CoroutineScope`
 * and `onDestroy` cancellation this class used to carry unnecessary.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // targetSdk 35 and up draws edge to edge whether or not this is called; calling it explicitly
    // keeps the behaviour the same on older releases. Scaffold is what then pads content away
    // from the system bars.
    enableEdgeToEdge()
    setContent { ExampleTheme { LogReaderScreen() } }
  }
}

/**
 * What the screen is showing.
 *
 * `total` is every fix in the file while `shown` stops at [MAX_FIXES], so a long log reports an
 * honest count without holding every formatted line.
 */
private sealed interface ReadState {
  data object Idle : ReadState

  data object Reading : ReadState

  data class Read(val shown: List<String>, val total: Int) : ReadState

  data class Failed(val message: String?) : ReadState
}

/**
 * A picked file, plus the [attempt] that picked it.
 *
 * The counter is not decoration: [LaunchedEffect] restarts only when its key changes, so without
 * something to tell one pick from the next, choosing the same file twice would do nothing.
 */
private data class Pick(val uri: Uri, val attempt: Int)

@Composable
private fun LogReaderScreen(modifier: Modifier = Modifier) {
  val contentResolver = LocalContext.current.contentResolver
  var pick by remember { mutableStateOf<Pick?>(null) }
  var state by remember { mutableStateOf<ReadState>(ReadState.Idle) }

  val openDocument =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) pick = Pick(uri, (pick?.attempt ?: 0) + 1)
    }

  LaunchedEffect(pick) {
    val current = pick ?: return@LaunchedEffect
    state = ReadState.Reading
    state = readFixes(contentResolver, current.uri)
  }

  LogReaderScreen(
    state = state,
    // "*/*" rather than "text/plain": a .log or .nmea file is often typed as
    // application/octet-stream, and a narrower filter hides the very files this reads.
    onPick = { openDocument.launch(arrayOf("*/*")) },
    modifier = modifier,
  )
}

/** The stateless half, so that [ReadState] can be rendered from a `@Preview` without any file. */
@Composable
private fun LogReaderScreen(
  state: ReadState,
  onPick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(modifier = modifier.fillMaxSize()) { insets ->
    Column(
      modifier = Modifier.fillMaxSize().padding(insets),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Button(
        onClick = onPick,
        enabled = state !is ReadState.Reading,
        modifier = Modifier.padding(16.dp),
      ) {
        Text(stringResource(R.string.pick_log))
      }

      when (state) {
        is ReadState.Idle -> Message(stringResource(R.string.instructions))
        is ReadState.Reading ->
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
          }
        is ReadState.Failed ->
          Message(
            stringResource(R.string.read_failed, state.message.orEmpty()),
            color = MaterialTheme.colorScheme.error,
          )
        is ReadState.Read ->
          if (state.total == 0) Message(stringResource(R.string.no_fixes)) else FixList(state)
      }
    }
  }
}

@Composable
private fun FixList(state: ReadState.Read) {
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
        text = fix,
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

@Composable
private fun Message(
  text: String,
  color: Color = MaterialTheme.colorScheme.onSurface,
) {
  Text(
    text = text,
    color = color,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
  )
}

@Composable
private fun ExampleTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    content = content,
  )
}

/**
 * Correlates the file into position fixes.
 *
 * `flowOn(Dispatchers.IO)` moves the reading off the main thread; the collector stays on it, which
 * is why the counting below needs no synchronisation. `Dispatchers.IO` is public API here, as on
 * the JVM — on Kotlin/Native it is `internal` and on JS it does not exist, which is why the library
 * never picks one for you.
 *
 * The result is returned in one piece rather than streamed into the UI: a long log would otherwise
 * recompose the list thousands of times to show rows nobody can read as they go past.
 */
private suspend fun readFixes(
  contentResolver: ContentResolver,
  uri: Uri,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
): ReadState {
  val shown = mutableListOf<String>()
  var total = 0
  return try {
    withContext(dispatcher) { contentResolver.openInputStream(uri) ?: error("could not open $uri") }
      .use { stream ->
        stream.asSource().buffered().nmeaSentences().positions().flowOn(Dispatchers.IO).collect {
          fix ->
          total++
          if (shown.size < MAX_FIXES) shown += "${fix.dateTime ?: fix.time}  ${fix.position}"
        }
      }
    ReadState.Read(shown, total)
  } catch (e: CancellationException) {
    // Cancellation is not a read failure. `catch (e: Exception)` swallows it, so leaving the
    // catch-all on its own would turn navigating away mid-read into a "Failed" screen and would
    // stop the enclosing LaunchedEffect from actually being cancellable.
    throw e
  } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    // A picked file may be anything at all -- a JPEG, a directory, a file whose permission was
    // revoked between picking and reading. Report it rather than crashing the demo.
    ReadState.Failed(e.message)
  }
}

/**
 * A picked file is arbitrary and may hold any number of fixes, so the list of formatted lines is
 * bounded. `LazyColumn` only composes what is on screen, so this caps memory rather than drawing.
 */
private const val MAX_FIXES = 2000

@Preview(showBackground = true)
@Composable
private fun LogReaderPreview() {
  ExampleTheme {
    LogReaderScreen(
      state =
        ReadState.Read(
          shown =
            listOf(
              "2010-06-15T10:39:00Z  60.0°N 25.0°E",
              "2010-06-15T10:39:01Z  60.0°N 25.0°E",
            ),
          total = 2,
        ),
      onPick = {},
    )
  }
}
