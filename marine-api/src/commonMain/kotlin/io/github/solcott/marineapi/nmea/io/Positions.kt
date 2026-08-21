package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.sentence.Gbs
import io.github.solcott.marineapi.nmea.sentence.Gga
import io.github.solcott.marineapi.nmea.sentence.Gll
import io.github.solcott.marineapi.nmea.sentence.Gsa
import io.github.solcott.marineapi.nmea.sentence.Gst
import io.github.solcott.marineapi.nmea.sentence.Rmc
import io.github.solcott.marineapi.nmea.sentence.Vtg
import io.github.solcott.marineapi.nmea.sentence.Zda
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Knots to kilometres per hour. */
private const val KNOTS_TO_KMH = 1.852

/**
 * Where a vessel is, when it was there, and how fast it was going.
 *
 * One of these is assembled from a whole NMEA update cycle rather than from a single sentence,
 * because no single sentence carries all of it: GGA has the position and altitude but no date or
 * speed, RMC has the date and speed but no altitude, and VTG has only the velocity. See
 * [positions].
 *
 * @property position where the vessel is. Never `null` -- a fix with no position is not a fix, so
 *   [positions] declines to report one. [Position.altitude] is filled in only when the cycle
 *   carried a GGA, the sole sentence here that reports one, and only when GGA also supplied the
 *   position: the altitude belongs to the fix that produced it, and an altitude carried over from a
 *   different sentence's coordinates is not a point anyone measured.
 * @property time UTC time of the fix
 * @property date UTC date of the fix, from RMC or ZDA. `null` when the cycle carried neither: a
 *   receiver that reports no date has not told you what day it is, and the implementation this
 *   replaces substituted the host clock's date here, which is a guess dressed up as a measurement.
 * @property speedKnots speed over ground, from VTG in preference to RMC
 * @property courseTrue course over ground in degrees true. `null` when the receiver reports none,
 *   which is what a stationary vessel looks like -- course is undefined at rest.
 * @property faaMode how the fix was obtained, absent from receivers older than NMEA 2.3
 * @property fixQuality quality of the fix, reported by GGA alone
 * @property accuracy how well the receiver believes it knows this position, gathered from `GST`,
 *   `GBS`, `GSA` and `GGA`. Never `null`, but [FixAccuracy.isEmpty] when the cycle carried none of
 *   them -- which is the common case, since most consumer receivers send no `GST`.
 */
public data class PositionFix(
  val position: Position,
  val time: LocalTime? = null,
  val date: LocalDate? = null,
  val speedKnots: Double? = null,
  val courseTrue: Double? = null,
  val faaMode: FaaMode? = null,
  val fixQuality: GpsFixQuality? = null,
  val accuracy: FixAccuracy = FixAccuracy(),
) {

  /** [date] and [time] together, or `null` unless the cycle carried both. */
  public val dateTime: LocalDateTime?
    get() = if (date != null && time != null) LocalDateTime(date, time) else null

  /** [speedKnots] in kilometres per hour. */
  public val speedKmh: Double?
    get() = speedKnots?.let { it * KNOTS_TO_KMH }
}

/**
 * Correlates the position sentences of each update cycle into a single [PositionFix].
 *
 * A receiver reports one fix as a burst of sentences -- `GGA`, `GSA`, `GSV`, `RMC`, `VTG` is a
 * common cycle -- and the useful value is spread across them. This gathers `GGA`, `GLL`, `RMC`,
 * `VTG` and `ZDA`, and emits once per cycle that carries enough of them.
 *
 * ```
 * source.nmeaSentences()
 *   .positions()
 *   .collect { fix -> chart.plot(fix.position, fix.speedKnots) }
 * ```
 *
 * **A cycle is delimited by the data, not by a clock.** The predecessor to this operator grouped
 * sentences that arrived within 1000 ms of each other, which meant a fix depended on the host's
 * scheduling and on how the feed was buffered, and made replaying a log -- where every line arrives
 * at once -- behave unlike reading the same device live. Here a cycle ends where the receiver ends
 * it: when a sentence type it has already sent comes round again, or when the feed itself ends.
 * That is one update interval of latency, and it is the price of the altitude being GGA's and the
 * date being ZDA's -- neither can be attributed to this fix until the cycle carrying them is over.
 * It also bounds the state held during a warm-up, when a device emits `GGA` over and over with
 * nothing else: each repeat replaces the last.
 *
 * Waiting for the boundary rather than reporting the moment a fix looks complete is what makes the
 * result independent of the order a device sends its sentences in. Both orders occur: of the 91
 * receivers in this project's conformance corpus that send `RMC`, one sends it *before* its
 * position sentence, and of the 29 that send `ZDA`, all but a handful send it *last* -- so an
 * operator that stopped at the first complete-looking cycle would silently lose that device's
 * altitude and those devices' dates.
 *
 * **A cycle is reported when it holds a position** -- `GGA`, `RMC` or `GLL` -- and nothing else is
 * required. A velocity is not: a receiver that reports where it is without reporting how fast it is
 * going has still reported where it is, and [PositionFix.speedKnots] is nullable for exactly that
 * case. Two rules were tried before this one and both discarded real fixes: the predecessor
 * demanded a `GGA` or `GLL` in every cycle, losing everything from the four corpus receivers that
 * send `RMC` alone, and requiring a velocity lost another 136 positions from the two that send none
 * -- 122 `$ECGLL` sentences from a depth sounder and 14 `GGA` from an `mr-350p`. gpsd reports a fix
 * for every one of them, which is what settled it. Nothing is reported for a cycle whose sentences
 * say the data is bad -- a void `RMC` or `GLL` status, an `RMC` in [FaaMode.NONE], or a `GGA`
 * reporting [GpsFixQuality.INVALID] -- nor for one whose position fields are empty.
 *
 * Where two sentences carry the same value the more informative one wins: position from `GGA` (it
 * is the one with altitude) before `RMC` before `GLL`, velocity from `VTG` before `RMC`, and either
 * one's blank field falls through to the other.
 */
public fun Flow<Sentence>.positions(): Flow<PositionFix> = flow {
  val cycle = PositionCycle()
  collect { sentence -> cycle.add(sentence)?.let { emit(it) } }
  // The last cycle of a finite feed has no successor to close it, so the end of the feed does.
  // Without this, replaying a log would silently drop its final fix.
  cycle.close()?.let { emit(it) }
}

/**
 * The position sentences seen so far in the current update cycle.
 *
 * Not a general-purpose burst collector: each operator in this package keeps only the state its own
 * output needs, which is less code in total than the abstract base class they replace and leaves no
 * shared behaviour to reason about when one of them changes.
 */
private class PositionCycle {

  private var gga: Gga? = null
  private var gll: Gll? = null
  private var rmc: Rmc? = null
  private var vtg: Vtg? = null
  private var zda: Zda? = null

  /**
   * Accuracy accumulated as the cycle runs, rather than held sentence by sentence.
   *
   * GST, GBS and GSA are gathered but are deliberately NOT part of [alreadyHeld]: that set is what
   * ends a cycle, so adding to it would move every cycle boundary in the feed. They are carried
   * alongside, exactly as GSA is in SatelliteCycle. Merging on arrival also keeps this O(1) -- a
   * list would grow without bound through a warm-up where a device repeats one sentence forever,
   * which is the same reason the sentences above are held singly rather than accumulated.
   */
  private var accuracy = FixAccuracy.EMPTY

  /**
   * Adds a sentence, returning the fix from the cycle before it if this sentence ended that cycle.
   *
   * A sentence type the cycle already holds is the boundary: the receiver has come round again.
   */
  fun add(sentence: Sentence): PositionFix? {
    val ended = if (alreadyHeld(sentence)) close() else null
    keep(sentence)
    return ended
  }

  /** Ends the current cycle, returning its fix if it had one. */
  fun close(): PositionFix? {
    val fix = if (isComplete() && reportsGoodData()) build() else null
    reset()
    return fix
  }

  private fun alreadyHeld(sentence: Sentence): Boolean =
    when (sentence) {
      is Gga -> gga != null
      is Gll -> gll != null
      is Rmc -> rmc != null
      is Vtg -> vtg != null
      is Zda -> zda != null
      else -> false
    }

  private fun keep(sentence: Sentence) {
    when (sentence) {
      is Gga -> gga = sentence
      is Gll -> gll = sentence
      is Rmc -> rmc = sentence
      is Vtg -> vtg = sentence
      is Zda -> zda = sentence
      // Accuracy only: these say how good the fix is, never where or when it is, so they add to
      // the accumulator and never to the held set above.
      is Gst -> accuracy = accuracy.filledFrom(sentence)
      is Gbs -> accuracy = accuracy.filledFrom(sentence)
      is Gsa -> accuracy = accuracy.filledFrom(sentence)
      // Everything else in the cycle -- GSV, depth, wind -- says nothing about the fix, and does
      // not delimit one either: a receiver may send several GSVs per cycle by design.
      else -> Unit
    }
  }

  /** Whether the cycle carries a position. That is the whole of what a fix requires. */
  private fun isComplete(): Boolean = gga != null || rmc != null || gll != null

  /**
   * Whether the cycle's own status fields say the data is worth reporting.
   *
   * The predecessor checked the FAA mode only when the sentence had more than eleven fields,
   * because its accessor threw for a field that was not there. A mode that was never sent reads as
   * `null` here, which is not [FaaMode.NONE], so the field count no longer comes into it.
   */
  private fun reportsGoodData(): Boolean {
    rmc?.let { if (it.status == DataStatus.VOID || it.faaMode == FaaMode.NONE) return false }
    gga?.let { if (it.fixQuality == GpsFixQuality.INVALID) return false }
    // ACTIVE specifically, not "anything but VOID". A status this library could not read is not
    // the same as a station saying nothing: an eXplorist 110 in the corpus sends `N` there, and
    // reading that as permission would report 11 fixes gpsd reports as no-fix. Of the 1,025 GLL
    // sentences in the corpus not one omits the field, so demanding it costs nothing real.
    gll?.let { if (it.status != DataStatus.ACTIVE) return false }
    return true
  }

  private fun build(): PositionFix? {
    val position =
      gga?.position?.withAltitudeOf(gga) ?: rmc?.position ?: gll?.position ?: return null
    return PositionFix(
      position = position,
      time = rmc?.time ?: gga?.time ?: gll?.time ?: zda?.time,
      date = rmc?.date ?: zda?.date,
      speedKnots = vtg?.speedKnots ?: rmc?.speedKnots,
      courseTrue = vtg?.courseTrue ?: rmc?.courseTrue,
      // The predecessor read this from RMC alone, and then only when RMC had also supplied the
      // position, so the mode vanished from every cycle that included a GGA.
      faaMode = rmc?.faaMode ?: vtg?.faaMode ?: gll?.faaMode,
      fixQuality = gga?.fixQuality,
      // GGA comes last so GSA's dilution wins over its own, which is the same value when both are
      // sent and the better-specified one when they disagree.
      accuracy = gga?.let { accuracy.filledFrom(it) } ?: accuracy,
    )
  }

  /**
   * Moves GGA's altitude into the position it goes with.
   *
   * GGA keeps the two apart because the sentence does: altitude has its own field and its own units
   * field beside it. A fix is where they belong together, so long as the units are the metres
   * [Position.altitude] is measured in -- a receiver reporting feet gets no altitude here rather
   * than one that is wrong by a factor of three.
   */
  private fun Position.withAltitudeOf(gga: Gga?): Position =
    if (gga?.altitude == null || (gga.altitudeUnits != null && gga.altitudeUnits != Units.METER)) {
      this
    } else {
      copy(altitude = gga.altitude)
    }

  private fun reset() {
    gga = null
    gll = null
    rmc = null
    vtg = null
    zda = null
    accuracy = FixAccuracy.EMPTY
  }
}
