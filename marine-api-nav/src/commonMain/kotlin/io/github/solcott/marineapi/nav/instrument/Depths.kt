package io.github.solcott.marineapi.nav.instrument

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.sentence.Dbk
import io.github.solcott.marineapi.nmea.sentence.Dbs
import io.github.solcott.marineapi.nmea.sentence.Dbt
import io.github.solcott.marineapi.nmea.sentence.Dpt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/** A foot, for converting a sounder that reports only feet. */
private const val METRES_PER_FOOT = 0.3048

/** A fathom: six feet, and still how a paper chart of anywhere deep is marked. */
private const val METRES_PER_FATHOM = 1.8288

/** How much deeper than the alarm depth the water must get before the alarm clears. */
private const val DEFAULT_CLEARANCE = 1.2

/**
 * Where a depth is measured from.
 *
 * The difference between these is the difference between "there is 2 metres of water" and "you are
 * about to hit the bottom", and no sounder tells you which one it means without being asked. NMEA
 * has four depth sentences and three of them are named for the datum they use.
 */
public enum class DepthReference {
  /**
   * Below the transducer, which is bolted to the hull somewhere between the waterline and the keel.
   *
   * What `DBT` reports, and what almost every sounder actually measures. Always shallower than
   * [SURFACE] and deeper than [KEEL], by amounts that depend on the boat and are not in the
   * sentence.
   */
  TRANSDUCER,
  /**
   * Below the waterline: the whole depth of water, which is what a chart is surveyed to.
   *
   * What `DBS` reports. Compare it against charted soundings, not against your draught.
   */
  SURFACE,
  /**
   * Below the keel: how much water is left under you.
   *
   * What `DBK` reports, and the only one of the three that answers the question a helmsman is
   * actually asking.
   */
  KEEL,
}

/**
 * A depth of water, and what it was measured from.
 *
 * [reference] is not decoration. A reading of 2.0 m below the transducer on a boat drawing 2 m is
 * aground, and the same 2.0 m below the keel is 2 metres of water to spare.
 *
 * @property metres depth in metres, the unit everything here normalises to
 * @property reference where the measurement starts
 */
public data class Depth(val metres: Double, val reference: DepthReference) {

  /** [metres] in feet. */
  public val feet: Double
    get() = metres / METRES_PER_FOOT

  /** [metres] in fathoms. */
  public val fathoms: Double
    get() = metres / METRES_PER_FATHOM
}

/**
 * The depth this sentence reports measured from [reference], or `null` if it cannot answer.
 *
 * `DBT`, `DBS` and `DBK` each know one datum and nothing about the others: a `DBT` asked for the
 * depth below the keel gives `null`, because how far the transducer sits above the keel is a
 * property of the boat that the sentence does not carry. **This never guesses.** A sounder is one
 * of the few instruments on a boat whose output is load-bearing for not sinking, and a plausible
 * number derived from an assumption is worse than no number.
 *
 * `DPT` is the one that can convert, because it carries the transducer's offset -- but only in one
 * direction at a time. A positive offset measures *up* to the waterline, so it yields
 * [DepthReference.SURFACE]; a negative one measures *down* to the keel, so it yields
 * [DepthReference.KEEL]. It is never both, and an offset of exactly zero is read as unconfigured
 * rather than as a transducer mounted at the waterline, which no boat has.
 *
 * Where a sounder reports the same depth in three units, metres are preferred and feet or fathoms
 * are converted when the metres field is empty -- `$SDDBT,7.8,f,,M,,F` is a sentence real devices
 * send. That is a change of unit, not of datum, and is always safe.
 */
public fun Sentence.depthOrNull(reference: DepthReference): Depth? =
  when (this) {
    is Dbt -> depthFrom(reference, DepthReference.TRANSDUCER, depthMeters, depthFeet, depthFathoms)
    is Dbs -> depthFrom(reference, DepthReference.SURFACE, depthMeters, depthFeet, depthFathoms)
    is Dbk -> depthFrom(reference, DepthReference.KEEL, depthMeters, depthFeet, depthFathoms)
    is Dpt -> depthFromOffset(reference)
    else -> null
  }

/**
 * Every depth in this flow, measured from [reference].
 *
 * ```
 * source.nmeaSentences()
 *   .depths(DepthReference.KEEL)
 *   .collect { display.show(it.metres) }
 * ```
 *
 * Unlike `positions` there is no cycle to correlate -- each depth sentence carries a complete
 * reading -- so this is a filter and a conversion rather than a join. A sounder sending both `DBT`
 * and `DPT` yields two readings per cycle when both can answer, which is what it reported.
 *
 * **[reference] has no default on purpose.** Every caller has to say which datum it wants, because
 * the one thing that must not happen here is a number arriving with an assumed meaning. A feed
 * whose sounder cannot answer the question asked yields nothing at all, which is the honest result
 * and is worth checking for.
 */
public fun Flow<Sentence>.depths(reference: DepthReference): Flow<Depth> = mapNotNull {
  it.depthOrNull(reference)
}

/**
 * Whether the water is shallower than an alarm threshold, reported only when the answer changes.
 *
 * @property isShallow `true` when the alarm is on
 * @property depth the reading that turned it on or off
 */
public data class DepthAlarm(val isShallow: Boolean, val depth: Depth)

/**
 * A shallow-water alarm with hysteresis, emitting one value per change rather than per reading.
 *
 * ```
 * source.nmeaSentences()
 *   .depths(DepthReference.KEEL)
 *   .shallowerThan(metres = 2.0)
 *   .collect { if (it.isShallow) alarm.sound() else alarm.silence() }
 * ```
 *
 * **[clearAt] is why this is an operator rather than a `filter`.** An echo sounder over an uneven
 * bottom, or in a chop that lifts the transducer, reads a foot either side of the truth from one
 * ping to the next -- so a bare threshold at the depth you care about chatters, and an alarm that
 * chatters gets switched off. The water has to get [clearAt] deep before the alarm resets, which by
 * default is 20% deeper than it was when it sounded.
 *
 * The first reading always produces a value, since going from knowing nothing to either state is a
 * change. Nothing here checks that the readings share a [DepthReference] with each other or with
 * the threshold; [depths] produces one datum per flow, so keep them separate.
 *
 * @param metres alarm when the water is shallower than this
 * @param clearAt clear the alarm once it is deeper than this; must not be shallower than [metres]
 */
public fun Flow<Depth>.shallowerThan(
  metres: Double,
  clearAt: Double = metres * DEFAULT_CLEARANCE,
): Flow<DepthAlarm> {
  require(metres > 0.0) { "The alarm depth must be positive, was $metres" }
  require(clearAt >= metres) {
    "The alarm would never clear: clearAt $clearAt is shallower than the alarm depth $metres"
  }
  return flow {
    var isShallow: Boolean? = null
    collect { depth ->
      val next = if (isShallow == true) depth.metres < clearAt else depth.metres < metres
      if (next != isShallow) {
        isShallow = next
        emit(DepthAlarm(next, depth))
      }
    }
  }
}

/** One of the three fixed-datum sentences, if it is the datum being asked for. */
private fun depthFrom(
  wanted: DepthReference,
  reported: DepthReference,
  metres: Double?,
  feet: Double?,
  fathoms: Double?,
): Depth? {
  if (wanted != reported) return null
  val value = metres ?: feet?.times(METRES_PER_FOOT) ?: fathoms?.times(METRES_PER_FATHOM)
  return value?.let { Depth(it, reported) }
}

/** `DPT`, which carries the offset that lets one datum become another. */
private fun Dpt.depthFromOffset(wanted: DepthReference): Depth? {
  val belowTransducer = depth ?: return null
  return when (wanted) {
    DepthReference.TRANSDUCER -> Depth(belowTransducer, wanted)
    // A positive offset is the distance up to the water line, so adding it gives the full depth.
    DepthReference.SURFACE ->
      offset?.takeIf { it > 0.0 }?.let { Depth(belowTransducer + it, wanted) }
    // A negative offset is the distance down to the keel, so adding it takes that much away.
    DepthReference.KEEL -> offset?.takeIf { it < 0.0 }?.let { Depth(belowTransducer + it, wanted) }
  }
}
