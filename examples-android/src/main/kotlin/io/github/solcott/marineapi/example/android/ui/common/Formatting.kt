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
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.FixAccuracy
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

/**
 * Metres of position error per unit of HDOP, for a receiver that reports no error of its own.
 *
 * These are gpsd's `P_UERE_WITH_DGPS` and `P_UERE_NO_DGPS` (`gpsd.h`), both at 95% confidence. The
 * library deliberately does not do this multiplication -- nothing in an NMEA feed says 19, and the
 * true figure varies with chipset, antenna and sky -- so the policy lives here, in an app that is
 * never published, rather than in the parser's output where every consumer would inherit it.
 *
 * A differential fix is the accurate case: the correction removes most of the ionospheric and
 * ephemeris error the larger constant is there to cover.
 */
private const val UERE_DGPS = 4.75

private const val UERE_AUTONOMOUS = 19.0

/**
 * How good this fix is, in metres, or `null` if the receiver has given no basis for saying.
 *
 * Two quite different numbers come out of here, which is why [AccuracyEstimate] carries the
 * distinction rather than returning a bare `Double`. [FixAccuracy] is the receiver's own
 * measurement -- only about one receiver in five reports one at all. Everything else falls back to
 * dilution of precision times an assumed error, which describes the satellite geometry and an
 * assumption about everything else; it cannot see multipath, a bad antenna or a bad day in the
 * ionosphere. Presenting the two as the same number would be the misleading part, so the UI labels
 * them apart.
 */
data class AccuracyEstimate(val metres: Double, val measured: Boolean)

fun PositionFix.accuracyEstimate(): AccuracyEstimate? {
  accuracy?.let {
    return AccuracyEstimate(it.horizontal, measured = true)
  }
  val hdop = horizontalDilution ?: return null
  return AccuracyEstimate(
    hdop * if (isCorrected()) UERE_DGPS else UERE_AUTONOMOUS,
    measured = false,
  )
}

/**
 * Whether this fix was corrected against a reference station.
 *
 * gpsd's model only distinguishes differential from autonomous, but RTK is differential too and
 * more accurate still, so it belongs on this side: the worst that does is overstate an RTK
 * receiver's error, which is the safe direction to be wrong in. Either field may answer, since a
 * cycle can carry a GGA without an RMC or the reverse.
 */
private fun PositionFix.isCorrected(): Boolean =
  fixQuality in setOf(GpsFixQuality.DGPS, GpsFixQuality.RTK, GpsFixQuality.FRTK) ||
    faaMode in setOf(FaaMode.DGPS, FaaMode.RTK_FIXED, FaaMode.RTK_FLOAT)

/** Metres to one decimal, rounded by hand for the same locale reason as the speed in [describe]. */
fun AccuracyEstimate.metresToOneDecimal(): String = "${(metres * 10).roundToInt() / 10.0}"

/** An exception as one line, falling back to the type when the message is null or blank. */
fun Throwable.describe(): String =
  message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
