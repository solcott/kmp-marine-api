package io.github.solcott.marineapi.example.android.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlin.math.roundToInt

/** A centred paragraph, which is what both screens use to say why there is nothing to show. */
@Composable
fun Message(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
  Text(
    text = text,
    color = color,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
  )
}

/**
 * One fix as one line: when it was, where, and how fast.
 *
 * `PositionFix` is a data class, so its own `toString` names every field including the nulls, which
 * is right for a failure message and far too wide for a phone. [Position] does have a `toString`
 * worth using, so this only has to decide what goes around it. Time rather than date and time,
 * because a receiver reports no date at all unless the cycle carried an RMC or a ZDA.
 *
 * The speed is rounded by hand rather than with `String.format`, which would read the default
 * locale and print "4,2" on half the phones this could run on.
 */
fun PositionFix.describe(): String {
  val speed = speedKnots?.let { " ${(it * 10).roundToInt() / 10.0} kn" }.orEmpty()
  return "${dateTime ?: time ?: ""}  $position$speed".trim()
}

/** An exception as one line, falling back to the type when the message is null or blank. */
fun Throwable.describe(): String =
  message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
