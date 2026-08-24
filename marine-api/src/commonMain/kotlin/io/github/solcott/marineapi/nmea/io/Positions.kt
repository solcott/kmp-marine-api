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
import io.github.solcott.marineapi.nmea.sentence.Gst
import io.github.solcott.marineapi.nmea.sentence.Pgrme
import io.github.solcott.marineapi.nmea.sentence.Rmc
import io.github.solcott.marineapi.nmea.sentence.Vtg
import io.github.solcott.marineapi.nmea.sentence.Zda
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Knots to kilometres per hour. */
private const val KNOTS_TO_KMH = 1.852

/**
 * Which sentence an error estimate was read from.
 *
 * Worth carrying with the numbers, because the three are not the same measurement. [GST] and [GBS]
 * report standard deviations, so [FixAccuracy.horizontal] derived from them is a 1-sigma figure;
 * [GARMIN_EPE] is a vendor's own estimate at a confidence Garmin does not state. A caller drawing
 * an accuracy circle has to know which it is holding.
 */
public enum class AccuracySource {
  /** `GST`, pseudorange noise statistics. The only source here carrying an error ellipse. */
  GST,
  /**
   * `GBS`, RAIM fault detection. Reports its expected errors whether or not it blames a satellite.
   */
  GBS,
  /** `PGRME`, Garmin's proprietary position error estimate. */
  GARMIN_EPE,
}

/**
 * How wrong a receiver believes its own fix to be, in metres.
 *
 * **A claim, not a guarantee.** This is the receiver's model of its own error, and a receiver can
 * be confidently wrong: `ublox-lea-5q` in this project's conformance corpus reports latitude and
 * longitude errors of 304,885 metres. That capture reports no fix at all -- its `GGA` quality,
 * `RMC` status and `GLL` status all say the data is bad -- so nothing here is built from it, which
 * is why no threshold rejects implausible values. Suppressing a large error would be worse than
 * reporting it: a 300 metre sigma under a bridge is real, and the most useful thing that receiver
 * can say.
 *
 * Only 19 of the corpus's 103 receivers report any of this. See [PositionFix.horizontalDilution]
 * for what the other 84 offer instead.
 *
 * @property horizontal radial error, metres. From `GST` and `GBS` this is DRMS -- the root sum of
 *   the squared latitude and longitude deviations, which is roughly a 65% confidence radius rather
 *   than the 95% a reader may assume. From `PGRME` it is Garmin's own horizontal figure, used
 *   as-is. The components remain reachable so a caller wanting a different combination can compute
 *   one.
 * @property vertical standard deviation of the altitude error, metres
 * @property semiMajorError standard deviation of the error ellipse's semi-major axis, metres. `GST`
 *   only. Deliberately not used for [horizontal]: it describes one axis, so it overstates the
 *   radial error, and the two can flatly contradict each other -- `skytraq-dgps` reports a 22.2
 *   metre semi-major axis beside a 0.4 metre latitude deviation. Left raw so that stays visible.
 * @property semiMinorError standard deviation of the error ellipse's semi-minor axis, metres
 * @property errorEllipseOrientation orientation of the semi-major axis, degrees true
 * @property source which sentence this was read from
 */
public data class FixAccuracy(
  val horizontal: Double,
  val vertical: Double? = null,
  val semiMajorError: Double? = null,
  val semiMinorError: Double? = null,
  val errorEllipseOrientation: Double? = null,
  val source: AccuracySource,
)

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
 * @property accuracy how wrong the receiver believes this fix to be, in metres, from `GST`, `GBS`
 *   or `PGRME`. `null` on the great majority of receivers -- only 19 of the 103 in this project's
 *   conformance corpus report any of it.
 * @property horizontalDilution horizontal dilution of precision, from `GGA`. **Unitless**: it
 *   describes satellite geometry, not distance. It is here because 92 of the 103 corpus receivers
 *   report it where only 19 report [accuracy], so for most feeds it is the only thing resembling a
 *   quality figure.
 *
 *   To turn it into metres, multiply by a UERE -- an assumed error per unit of geometry. gpsd uses
 *   4.75 where the fix is differential and 19.0 where it is not (`P_UERE_WITH_DGPS` and
 *   `P_UERE_NO_DGPS` in `gpsd.h`, both at 95% confidence), which [fixQuality] and [faaMode] are
 *   enough to choose between. That multiplication is deliberately left to the caller: no field in
 *   the feed says 19, the constant varies with chipset, antenna and sky, and returning the product
 *   from here would put a fabricated distance in a parser's output -- the same mistake as the
 *   host-clock date described on [date]. It would also mean [accuracy] and this were both "metres"
 *   at different confidences.
 *
 *   Taken from `GGA` rather than `GSA` because `GGA` reports one combined value where a
 *   multi-constellation receiver sends a `GSA` per constellation, and because `GSA` would add no
 *   coverage: no corpus log carries `GSA` without `GGA`.
 */
public data class PositionFix(
  val position: Position,
  val time: LocalTime? = null,
  val date: LocalDate? = null,
  val speedKnots: Double? = null,
  val courseTrue: Double? = null,
  val faaMode: FaaMode? = null,
  val fixQuality: GpsFixQuality? = null,
  val accuracy: FixAccuracy? = null,
  val horizontalDilution: Double? = null,
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
  // Accuracy sources. Recorded, but deliberately absent from alreadyHeld(): see keep().
  private var gst: Gst? = null
  private var gbs: Gbs? = null
  private var pgrme: Pgrme? = null

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
      // The accuracy sources are recorded but must never reach alreadyHeld(): they say how good the
      // fix is, not that a new one has begun, and making one a boundary would cut the cycle in half
      // between the position and the sentences that complete it. Every GST, GBS and PGRME in the
      // corpus arrives inside the cycle it describes, before the sentence that closes it.
      is Gst -> gst = sentence
      is Gbs -> gbs = sentence
      is Pgrme -> pgrme = sentence
      // Everything else in the cycle -- GSA, GSV, depth, wind -- says nothing about the fix, and
      // does not delimit one either: a receiver may send several GSVs per cycle by design. GSA does
      // carry DOP, but GGA carries the same figure as one combined value where a
      // multi-constellation
      // receiver sends a GSA per constellation, and no corpus log has GSA without GGA -- so reading
      // it here would add state and a choice between four sentences for no coverage at all.
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
      accuracy = accuracy(),
      horizontalDilution = gga?.horizontalDilution,
    )
  }

  /**
   * The cycle's error estimate, from the most informative source that reported one.
   *
   * GST before GBS because GST alone carries the error ellipse; where a receiver sends both -- four
   * do in the corpus -- they report the same figures to different precision, so the order costs
   * nothing. PGRME last because it is one vendor's own estimate at a confidence it does not state,
   * and standard before proprietary is the defensible default; no corpus receiver sends it
   * alongside either of the others, so nothing here decides that case in practice.
   *
   * A source that arrived but reported nothing falls through to the next, because the ranking is by
   * how much a sentence carries and not by how far it is trusted -- an empty GST is nothing to
   * prefer, not a refusal to be overridden. No corpus receiver sends an empty GST beside a
   * populated GBS, so nothing in the data settles this either way.
   */
  private fun accuracy(): FixAccuracy? =
    gst?.let { fromGst(it) } ?: gbs?.let { fromGbs(it) } ?: pgrme?.let { fromPgrme(it) }

  /**
   * GST's deviations and ellipse, without its RMS residual.
   *
   * `rmsResidual` is deliberately dropped: it is a residual on the ranges going into the solution,
   * not an error in the position, and it is where every absurd number in the corpus lives -- a
   * neo-m9n swings it from 228650 to 837 between consecutive sentences while its deviations sit at
   * 1.0/1.0/3.1. Leaving it off removes that whole class of nonsense without a threshold.
   */
  private fun fromGst(gst: Gst): FixAccuracy? =
    drms(gst.latitudeError, gst.longitudeError)?.let {
      FixAccuracy(
        horizontal = it,
        vertical = gst.altitudeError,
        semiMajorError = gst.semiMajorError,
        semiMinorError = gst.semiMinorError,
        errorEllipseOrientation = gst.errorEllipseOrientation,
        source = AccuracySource.GST,
      )
    }

  private fun fromGbs(gbs: Gbs): FixAccuracy? =
    drms(gbs.latitudeError, gbs.longitudeError)?.let {
      FixAccuracy(horizontal = it, vertical = gbs.altitudeError, source = AccuracySource.GBS)
    }

  private fun fromPgrme(pgrme: Pgrme): FixAccuracy? =
    pgrme.horizontalError?.let {
      FixAccuracy(
        horizontal = it,
        vertical = pgrme.verticalError,
        source = AccuracySource.GARMIN_EPE,
      )
    }

  /**
   * Latitude and longitude deviations combined into one radial figure.
   *
   * Distance root mean square, which is the standard way to collapse two orthogonal deviations into
   * a radius. Both are required: the root of one squared deviation is that deviation, not a radius,
   * and a receiver that reported only one axis has not said how far out it might be. No corpus
   * receiver reports one without the other.
   */
  private fun drms(latitudeError: Double?, longitudeError: Double?): Double? {
    if (latitudeError == null || longitudeError == null) return null
    return sqrt(latitudeError * latitudeError + longitudeError * longitudeError)
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
    gst = null
    gbs = null
    pgrme = null
  }
}
