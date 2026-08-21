package io.github.solcott.marineapi.nav.instrument

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.sentence.Hdt
import io.github.solcott.marineapi.nmea.sentence.Rmc
import io.github.solcott.marineapi.nmea.sentence.Vhw
import io.github.solcott.marineapi.nmea.sentence.Vtg
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * True wind over the water, computed from the apparent wind where the instruments do not report it.
 *
 * ```
 * source.nmeaSentences().trueWind().collect { tactician.update(it) }
 * ```
 *
 * A masthead unit measures the apparent wind, which is the true wind plus the wind the vessel makes
 * by moving. Taking the second out is a vector subtraction and needs the vessel's own speed: this
 * uses **speed through the water**, from `VHW`, because a boat sails in the water. [groundWind] is
 * the same subtraction against speed over the ground, and the two answers differ by the current.
 *
 * A reading that is already true over the water -- `MWV` marked `T`, or `VWT`, or `MWD` -- is
 * passed through rather than recomputed. The instrument that reported it has the better
 * information, and correcting an already-corrected wind would take the vessel's motion out twice.
 *
 * [Wind.directionTrue] is filled in whenever a true heading has been seen, from `HDT` or from
 * `VHW`'s own true heading field, and left `null` otherwise. Nothing emits until the feed has
 * carried a `VHW`, since without a boat speed there is no correction to make; a motor vessel with
 * no water-speed log gets nothing here, and should use [groundWind].
 *
 * Leeway is not modelled. The vessel's motion through the water is taken to be along her heading,
 * which is what every instrument on the market assumes and is wrong by a few degrees when a sailing
 * boat is hard on the wind.
 */
public fun Flow<Sentence>.trueWind(): Flow<Wind> = flow {
  val motion = VesselMotion()

  collect { sentence ->
    motion.update(sentence)

    val reading = sentence.windOrNull() ?: return@collect
    val corrected =
      when (reading.reference) {
        WindReference.TRUE_OVER_WATER -> reading
        WindReference.APPARENT -> {
          val boatSpeed = motion.throughWater ?: return@collect
          reading.corrected(0.0, boatSpeed, WindReference.TRUE_OVER_WATER) ?: return@collect
        }
        // Removing motion over the ground and then motion through the water would subtract the
        // vessel twice; going back the other way needs the current, which no sentence carries.
        WindReference.GROUND -> return@collect
      }
    emit(corrected.referencedTo(motion.headingTrue))
  }
}

/**
 * Ground wind: the apparent wind with the vessel's motion **over the ground** removed.
 *
 * ```
 * source.nmeaSentences().groundWind().collect { log.record(it.directionTrue, it.speedKnots) }
 * ```
 *
 * This is the wind a shore station or a weather buoy reports, and the one to compare a forecast
 * against. [trueWind] is the one to sail by. They differ by exactly the current, which in a tidal
 * gate is several knots -- conflating them is a common bug and the reason both exist here.
 *
 * **This needs a true heading as well as a course and speed over the ground**, and emits nothing
 * without all three. The apparent wind is measured relative to the *bow* while the vessel's motion
 * over the ground is along her *course*, and the angle between them is her leeway and set. Assuming
 * they are the same is exactly the shortcut that makes a ground wind wrong, so it is not taken
 * here. The one exception is a vessel stopped over the ground, where there is no course to report
 * and none is needed.
 *
 * Only apparent readings are used. A reading already corrected to true over the water cannot be
 * converted without knowing the current, which no NMEA sentence carries.
 */
public fun Flow<Sentence>.groundWind(): Flow<Wind> = flow {
  val motion = VesselMotion()

  collect { sentence ->
    motion.update(sentence)

    val reading = sentence.windOrNull() ?: return@collect
    if (reading.reference != WindReference.APPARENT) return@collect
    val heading = motion.headingTrue ?: return@collect
    val speed = motion.overGround ?: return@collect
    // A vessel stopped over the ground reports no course, and does not need one: her motion is
    // zero whichever way it would have pointed.
    val course = motion.courseOverGround ?: if (speed == 0.0) heading else return@collect

    val drift = (course - heading).toRadians()
    val ground =
      reading.corrected(speed * sin(drift), speed * cos(drift), WindReference.GROUND)
        ?: return@collect
    emit(ground.referencedTo(heading))
  }
}

/**
 * This reading with the vessel's velocity taken out of it, in the vessel's own frame.
 *
 * The apparent wind is the true wind plus the vessel's motion, so the true wind is the apparent
 * wind minus it: `T = A - (-V)`, done in components with the bow as the y axis and starboard as x.
 * The angles need care in both directions, because a wind angle names where the wind comes *from*
 * while a velocity names where a thing is *going*, so the wind vector points 180 degrees from the
 * angle that describes it.
 *
 * @param boatStarboard the vessel's velocity to starboard, knots
 * @param boatForward the vessel's velocity over the bow, knots
 */
private fun Wind.corrected(
  boatStarboard: Double,
  boatForward: Double,
  reference: WindReference,
): Wind? {
  val angle = angleFromBow ?: return null
  val starboard = speedKnots * sin(angle.toRadians()) - boatStarboard
  val forward = speedKnots * cos(angle.toRadians()) - boatForward
  val speed = hypot(starboard, forward)
  // A dead calm has a speed and no direction. Keeping the apparent angle would be a fabrication,
  // and 0 is at least visibly the absence of an answer rather than a plausible wrong one.
  val corrected = if (speed == 0.0) 0.0 else normalizeDegrees(atan2(starboard, forward).toDegrees())
  return Wind(speedKnots = speed, reference = reference, angleFromBow = corrected)
}

/** Fills in whichever of the bow angle and the compass direction the other one implies. */
private fun Wind.referencedTo(headingTrue: Double?): Wind {
  if (headingTrue == null) return this
  return copy(
    angleFromBow = angleFromBow ?: directionTrue?.let { normalizeDegrees(it - headingTrue) },
    directionTrue = directionTrue ?: angleFromBow?.let { normalizeDegrees(headingTrue + it) },
  )
}

/**
 * The vessel's own motion, as most recently reported.
 *
 * The corrections need the vessel's velocity at the moment a wind reading arrives, and the
 * sentences carrying it arrive separately from the ones carrying wind -- a masthead unit and a
 * speed log are different instruments on different schedules. So this holds the latest of each
 * rather than demanding they line up inside one update cycle: a wind reading is worth correcting
 * with a boat speed from a second ago, and waiting for a cycle that carried both would discard most
 * of them.
 */
private class VesselMotion {
  var headingTrue: Double? = null
  var throughWater: Double? = null
  var overGround: Double? = null
  var courseOverGround: Double? = null

  fun update(sentence: Sentence) {
    when (sentence) {
      is Hdt -> sentence.heading?.let { headingTrue = it }
      is Vhw -> {
        sentence.headingTrue?.let { headingTrue = it }
        sentence.speedInKnots()?.let { throughWater = it }
      }
      is Rmc -> {
        sentence.speedKnots?.let { overGround = it }
        sentence.courseTrue?.let { courseOverGround = it }
      }
      is Vtg -> {
        sentence.speedInKnots()?.let { overGround = it }
        sentence.courseTrue?.let { courseOverGround = it }
      }
      else -> Unit
    }
  }
}

private fun Vhw.speedInKnots(): Double? = speedKnots ?: speedKmh?.times(KNOTS_PER_KMH)

private fun Vtg.speedInKnots(): Double? = speedKnots ?: speedKmh?.times(KNOTS_PER_KMH)

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI
