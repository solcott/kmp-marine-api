package io.github.solcott.marineapi.nav.instrument

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentence.Mwd
import io.github.solcott.marineapi.nmea.sentence.Mwv
import io.github.solcott.marineapi.nmea.sentence.Vwr
import io.github.solcott.marineapi.nmea.sentence.Vwt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/** Shared with TrueWind.kt, which converts the vessel's own speed the same way. */
internal const val KNOTS_PER_KMH = 1.0 / 1.852

private const val KNOTS_PER_METRE_PER_SECOND = 3600.0 / 1852.0

private const val FULL_CIRCLE = 360.0

/**
 * Which motion has been taken out of a wind reading.
 *
 * The three are different winds, not three ways of saying one. Handing a sailor an apparent wind
 * where a true one was wanted puts them on the wrong tack; handing them a ground wind instead of a
 * true one does the same thing more subtly, and only when there is a current.
 */
public enum class WindReference {
  /**
   * What the masthead actually feels: the true wind plus the wind the vessel makes by moving.
   *
   * Always further forward and, on any point of sail but a run, stronger than the true wind. It is
   * what sets the sails and it is not what tells you where the wind is.
   */
  APPARENT,
  /**
   * Apparent wind with the vessel's motion **through the water** removed.
   *
   * The sailor's true wind, and what a masthead instrument means when it says "true". It is the
   * wind that decides which tack is favoured, because a boat sails in the water rather than over
   * the ground.
   */
  TRUE_OVER_WATER,
  /**
   * Apparent wind with the vessel's motion **over the ground** removed.
   *
   * The meteorologist's wind, and what a shore station or a moored buoy reports. It differs from
   * [TRUE_OVER_WATER] by exactly the current: a two-knot tide makes two knots of difference to the
   * answer, which in light airs is most of the wind.
   */
  GROUND,
}

/**
 * A wind reading, and what it is a reading of.
 *
 * Either [angleFromBow] or [directionTrue] is always present and often both. Which ones depends on
 * what the sentence carried and on what else the feed had said: `MWD` reports a compass direction
 * and no bow angle, `MWV` reports a bow angle and no compass direction, and either can be turned
 * into the other only once the vessel's true heading is known.
 *
 * @property speedKnots wind speed, normalised to knots whatever unit the sentence used
 * @property reference which motion has been taken out; see [WindReference]
 * @property angleFromBow degrees the wind blows **from**, clockwise from the bow: 0 is on the nose,
 *   90 is on the starboard beam, 270 on the port beam. Wind is always named for where it comes
 *   from, which is why 0 does not mean a following wind.
 * @property directionTrue degrees true the wind blows from, or `null` when the vessel's heading was
 *   not known. `null` on a vessel with only a magnetic compass, since turning that into a true
 *   heading needs the local variation and this does not guess at it.
 */
public data class Wind(
  val speedKnots: Double,
  val reference: WindReference,
  val angleFromBow: Double? = null,
  val directionTrue: Double? = null,
)

/**
 * The wind this sentence reports, as it reports it, or `null` if it reports none.
 *
 * Nothing is converted between references here -- an `MWV` marked relative comes back
 * [WindReference.APPARENT] and one marked true comes back [WindReference.TRUE_OVER_WATER], which is
 * what the instrument said. See [trueWind] for the operator that does the arithmetic.
 *
 * `MWD` is reported as [WindReference.TRUE_OVER_WATER], with the caveat that the sentence does not
 * actually say: it carries a true wind direction without recording whether the instrument removed
 * the vessel's motion through the water or over the ground. Over water is what a masthead unit
 * computes, so that is the reading taken.
 *
 * A `VWR` or `VWT` with no side field gives `null`. Those two report an unsigned angle off the bow
 * with a separate `L` or `R`, so without it a reading of 30 degrees could be either side of the
 * vessel, and the two are 60 degrees apart.
 */
public fun Sentence.windOrNull(): Wind? =
  when (this) {
    is Mwv -> mwvWind()
    is Vwr ->
      relativeWind(
        windAngle,
        side,
        speedKnots,
        speedMetersPerSecond,
        speedKmh,
        WindReference.APPARENT,
      )
    is Vwt ->
      relativeWind(
        windAngle,
        side,
        speedKnots,
        speedMetersPerSecond,
        speedKmh,
        WindReference.TRUE_OVER_WATER,
      )
    is Mwd ->
      (speedKnots ?: speedMetersPerSecond?.times(KNOTS_PER_METRE_PER_SECOND))?.let { speed ->
        directionTrue?.let {
          Wind(speed, WindReference.TRUE_OVER_WATER, directionTrue = normalizeDegrees(it))
        }
      }
    else -> null
  }

/**
 * Every wind reading in this flow, as the instruments report them.
 *
 * ```
 * source.nmeaSentences().winds().collect { display.show(it.speedKnots, it.reference) }
 * ```
 *
 * The counterpart to `headings`: one sentence, one reading, no correlation, and no arithmetic. A
 * vessel whose masthead unit sends both `MWV,R` and `MWV,T` produces two readings per cycle,
 * distinguished by their [Wind.reference], which is the same shape as a vessel carrying both a gyro
 * and a magnetic compass.
 */
public fun Flow<Sentence>.winds(): Flow<Wind> = mapNotNull { it.windOrNull() }

private fun Mwv.mwvWind(): Wind? {
  // An MWV that says its own reading is void is not a reading. The field is advisory, so a
  // sentence that omits it entirely is taken at face value.
  if (status == DataStatus.VOID) return null
  val angle = windAngle ?: return null
  val speed = windSpeed ?: return null
  val knots =
    when (speedUnits) {
      Units.NAUTICAL_MILES -> speed
      Units.KILOMETERS -> speed * KNOTS_PER_KMH
      Units.METER -> speed * KNOTS_PER_METRE_PER_SECOND
      // Without a unit the number could be any of three that differ by a factor of two.
      else -> return null
    }
  val reference =
    when (this.reference) {
      AngleReference.RELATIVE -> WindReference.APPARENT
      AngleReference.TRUE -> WindReference.TRUE_OVER_WATER
      null -> return null
    }
  return Wind(knots, reference, angleFromBow = normalizeDegrees(angle))
}

/** The shared shape of `VWR` and `VWT`: an unsigned angle off the bow with the side beside it. */
private fun relativeWind(
  windAngle: Double?,
  side: Direction?,
  speedKnots: Double?,
  speedMetersPerSecond: Double?,
  speedKmh: Double?,
  reference: WindReference,
): Wind? {
  val angle = windAngle ?: return null
  val knots =
    speedKnots
      ?: speedMetersPerSecond?.times(KNOTS_PER_METRE_PER_SECOND)
      ?: speedKmh?.times(KNOTS_PER_KMH)
      ?: return null
  val fromBow =
    when (side) {
      Direction.RIGHT -> angle
      Direction.LEFT -> -angle
      null -> return null
    }
  return Wind(knots, reference, angleFromBow = normalizeDegrees(fromBow))
}

/**
 * Wraps an angle into `0.0..<360.0`.
 *
 * An angle already in range is returned unchanged rather than recomputed, because the arithmetic is
 * not exact: `((125.1 % 360.0) + 360.0) % 360.0` is 125.10000000000002, the intermediate 485.1
 * having no exact representation. A masthead unit reporting 125.1 must read back as 125.1, not as
 * something that merely prints the same. Zero is left to the arithmetic so that a negative zero
 * comes back positive.
 */
internal fun normalizeDegrees(degrees: Double): Double =
  if (degrees > 0.0 && degrees < FULL_CIRCLE) degrees
  else ((degrees % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE
