package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.sentence.Gbs
import io.github.solcott.marineapi.nmea.sentence.Gga
import io.github.solcott.marineapi.nmea.sentence.Gsa
import io.github.solcott.marineapi.nmea.sentence.Gst
import kotlin.math.sqrt

/**
 * How well the receiver believes it knows where it is.
 *
 * Two different kinds of number live here and they are not interchangeable. The errors are in
 * **metres**, estimated by the receiver from its own residuals, and are the ones to threshold on.
 * The dilution-of-precision figures are **unitless** geometry: they say how favourably the
 * satellites are arranged, not how wrong the answer is, and the same HDOP means a very different
 * error on a survey receiver than on a phone. Prefer [horizontalError] and fall back to
 * [horizontalDop] only when the receiver reports no `GST`, which most consumer receivers do not.
 *
 * Assembled from a whole update cycle alongside [PositionFix], from whichever of `GST`, `GBS`,
 * `GSA` and `GGA` the receiver sent. Where two sentences report the same quantity the first one in
 * the cycle to carry a value wins; a multi-constellation receiver sends several `GSA` per cycle,
 * and in a combined solution they all report the DOP of that one solution.
 *
 * @property latitudeError one-sigma north-south error in metres, from `GST` or `GBS`
 * @property longitudeError one-sigma east-west error in metres, from `GST` or `GBS`
 * @property altitudeError one-sigma vertical error in metres, from `GST` or `GBS`
 * @property semiMajorError semi-major axis of the one-sigma error ellipse, metres. The ellipse is
 *   the honest shape of a horizontal error -- it is rarely a circle, because the satellite geometry
 *   is rarely symmetric.
 * @property semiMinorError semi-minor axis of that ellipse, metres
 * @property errorEllipseOrientation orientation of the ellipse's major axis, degrees from true
 *   north
 * @property rmsResidual root-mean-square of the range residuals, the receiver's overall measure of
 *   how well its own measurements agreed
 * @property positionDop position (3D) dilution of precision, from `GSA`
 * @property horizontalDop horizontal dilution of precision, from `GSA` or `GGA`
 * @property verticalDop vertical dilution of precision, from `GSA`
 * @property satelliteCount how many satellites the fix used, from `GGA`
 * @property dgpsAge seconds since the last differential correction, from `GGA`. A correction ages
 *   out: a `DGPS` fix quality with a `dgpsAge` of several minutes is a claim the receiver is no
 *   longer entitled to make.
 * @property suspectSatelliteId the satellite `GBS` singles out as the likely bad measurement, if
 *   its RAIM check found one
 */
public data class FixAccuracy(
  val latitudeError: Double? = null,
  val longitudeError: Double? = null,
  val altitudeError: Double? = null,
  val semiMajorError: Double? = null,
  val semiMinorError: Double? = null,
  val errorEllipseOrientation: Double? = null,
  val rmsResidual: Double? = null,
  val positionDop: Double? = null,
  val horizontalDop: Double? = null,
  val verticalDop: Double? = null,
  val satelliteCount: Int? = null,
  val dgpsAge: Double? = null,
  val suspectSatelliteId: String? = null,
) {

  /**
   * One-sigma horizontal error in metres, combining [latitudeError] and [longitudeError].
   *
   * This is DRMS -- the root of the summed variances -- which is the ~63% confidence radius, not
   * the 50% of CEP nor the 95% of 2DRMS. Doubling it gives 2DRMS, which is what a datasheet quoting
   * "2.5 m accuracy" usually means. `null` unless the receiver reported both components, because
   * half of a horizontal error is not a horizontal error.
   */
  public val horizontalError: Double?
    get() {
      val lat = latitudeError ?: return null
      val lon = longitudeError ?: return null
      return sqrt(lat * lat + lon * lon)
    }

  /** True when the cycle carried no accuracy information at all. */
  public val isEmpty: Boolean
    get() = this == EMPTY

  internal fun filledFrom(gst: Gst): FixAccuracy =
    copy(
      latitudeError = latitudeError ?: gst.latitudeError,
      longitudeError = longitudeError ?: gst.longitudeError,
      altitudeError = altitudeError ?: gst.altitudeError,
      semiMajorError = semiMajorError ?: gst.semiMajorError,
      semiMinorError = semiMinorError ?: gst.semiMinorError,
      errorEllipseOrientation = errorEllipseOrientation ?: gst.errorEllipseOrientation,
      rmsResidual = rmsResidual ?: gst.rmsResidual,
    )

  /**
   * `GBS` is RAIM output rather than a direct error estimate, so its numbers fall in behind `GST`'s
   * where a receiver sends both. It reports them whether or not it actually identified a faulty
   * satellite.
   */
  internal fun filledFrom(gbs: Gbs): FixAccuracy =
    copy(
      latitudeError = latitudeError ?: gbs.latitudeError,
      longitudeError = longitudeError ?: gbs.longitudeError,
      altitudeError = altitudeError ?: gbs.altitudeError,
      suspectSatelliteId = suspectSatelliteId ?: gbs.satelliteId,
    )

  internal fun filledFrom(gsa: Gsa): FixAccuracy =
    copy(
      positionDop = positionDop ?: gsa.positionDop,
      horizontalDop = horizontalDop ?: gsa.horizontalDop,
      verticalDop = verticalDop ?: gsa.verticalDop,
    )

  /**
   * `GGA`'s horizontal dilution fills in for a receiver that sends no `GSA`, which is the whole of
   * what it adds over one -- the satellite count and the correction age have no other source here.
   */
  internal fun filledFrom(gga: Gga): FixAccuracy =
    copy(
      horizontalDop = horizontalDop ?: gga.horizontalDilution,
      satelliteCount = satelliteCount ?: gga.satelliteCount,
      dgpsAge = dgpsAge ?: gga.dgpsAge,
    )

  internal companion object {
    val EMPTY: FixAccuracy = FixAccuracy()
  }
}
