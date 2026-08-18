package io.github.solcott.marineapi.example.android

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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
 * Views are built in code and there is no Compose: this repository uses none, and pulling it in
 * would make the example about Compose rather than about reading NMEA. The one AndroidX dependency,
 * activity, is what [ComponentActivity] and [registerForActivityResult] come from.
 */
class MainActivity : ComponentActivity() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var output: TextView

  private val pickFile =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) read(uri)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    output = TextView(this).apply { setPadding(PADDING, PADDING, PADDING, PADDING) }
    val button =
      Button(this).apply {
        text = getString(R.string.pick_log)
        // "*/*" rather than "text/plain": a .log or .nmea file is often typed as
        // application/octet-stream, and a narrower filter hides the very files this reads.
        setOnClickListener { pickFile.launch(arrayOf("*/*")) }
      }

    setContentView(
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(button, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        addView(
          ScrollView(this@MainActivity).apply { addView(output) },
          LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
      }
    )

    output.text = getString(R.string.instructions)
  }

  /**
   * Correlates the file into position fixes.
   *
   * `flowOn(Dispatchers.IO)` moves the reading off the main thread; the collector stays on it, so
   * the text view is only ever touched from the UI thread. `Dispatchers.IO` is public API here, as
   * on the JVM — on Kotlin/Native it is `internal` and on JS it does not exist, which is why the
   * library never picks one for you.
   */
  private fun read(uri: Uri) {
    output.text = ""
    scope.launch {
      val fixes = StringBuilder()
      var count = 0
      try {
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri) ?: error("could not open $uri")
          }
          .use { stream ->
            stream
              .asSource()
              .buffered()
              .nmeaSentences()
              .positions()
              .flowOn(Dispatchers.IO)
              .collect { fix ->
                count++
                if (count <= MAX_LINES) {
                  fixes.append("${fix.dateTime ?: fix.time}  ${fix.position}\n")
                }
              }
          }
      } catch (e: Exception) {
        // A picked file may be anything at all. Report it rather than crashing the demo.
        fixes.append("could not read: ${e.message}\n")
      }
      if (count > MAX_LINES) fixes.append("... and ${count - MAX_LINES} more\n")
      output.text = if (count == 0) getString(R.string.no_fixes) else "$count fixes\n\n$fixes"
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private companion object {
    const val PADDING = 24

    /** A log can hold thousands of fixes; a TextView should not be asked to hold thousands. */
    const val MAX_LINES = 200
  }
}
