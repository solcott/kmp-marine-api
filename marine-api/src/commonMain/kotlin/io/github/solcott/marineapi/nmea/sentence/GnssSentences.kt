package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.NavStatus
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.fromCode
import io.github.solcott.marineapi.nmea.positionAt
import io.github.solcott.marineapi.nmea.positionFields
import kotlinx.datetime.LocalTime

/**
 * Fix data from more than one satellite constellation.
 *
 * Example: `$GNGNS,043539.00,4333.190206,N,00127.618380,E,AAAAN,14,1.3,250.8,50.0,,,V*62`
 *
 * The multi-constellation answer to GGA. Its distinguishing feature is [modeIndicator], which
 * carries one character per constellation rather than a single value: `AAAAN` is four systems
 * running autonomously and a fifth not contributing. The talker id says which systems are meant,
 * and the count varies by receiver -- the sample logs hold two, three, four and five character
 * indicators from different hardware.
 *
 * That string is kept raw, with [modes] decoding it. The format fixes neither its length nor which
 * position belongs to which constellation, so an indicator this library does not recognise still
 * survives a round trip instead of being flattened into a fixed set of properties.
 *
 * @property time UTC of this position
 * @property position latitude and longitude
 * @property modeIndicator one mode character per constellation, e.g. `AAAAN`
 * @property satelliteCount satellites used across all constellations, 00 to 99
 * @property horizontalDilution horizontal dilution of precision
 * @property altitude antenna altitude above mean sea level, metres
 * @property geoidalSeparation difference between the WGS-84 ellipsoid and mean sea level, metres
 * @property dgpsAge seconds since the last differential correction
 * @property dgpsStationId differential reference station
 * @property navStatus navigational status, added in NMEA 4.1. Read [NavStatus] before trusting it.
 *   The parser this replaces stopped at twelve fields and could not see this one.
 */
public data class Gns(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val position: Position? = null,
  val modeIndicator: String? = null,
  val satelliteCount: Int? = null,
  val horizontalDilution: Double? = null,
  val altitude: Double? = null,
  val geoidalSeparation: Double? = null,
  val dgpsAge: Double? = null,
  val dgpsStationId: String? = null,
  val navStatus: NavStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  /**
   * [modeIndicator] decoded, one entry per character, `null` where the code is not a known mode.
   *
   * Empty when the sentence carries no indicator. The order is the receiver's, not something this
   * library assigns meaning to.
   */
  public val modes: List<FaaMode?>
    get() = modeIndicator.orEmpty().map { FaaMode.entries.fromCode(it) }

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(time?.let { NmeaDateTime.formatTime(it) }) +
        positionFields(position) +
        listOf(
          modeIndicator,
          satelliteCount?.let { NmeaFormat.integer(it, 2) },
          horizontalDilution.field(),
          altitude.field(),
          geoidalSeparation.field(),
          dgpsAge.field(),
          dgpsStationId,
          navStatus.field(),
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GNS"

    private const val TIME = 0
    private const val LATITUDE = 1
    private const val LATITUDE_HEMISPHERE = 2
    private const val LONGITUDE = 3
    private const val LONGITUDE_HEMISPHERE = 4
    private const val MODE_INDICATOR = 5
    private const val SATELLITE_COUNT = 6
    private const val HORIZONTAL_DILUTION = 7
    private const val ALTITUDE = 8
    private const val GEOIDAL_SEPARATION = 9
    private const val DGPS_AGE = 10
    private const val DGPS_STATION_ID = 11
    private const val NAV_STATUS = 12

    /** Reads a GNS sentence from its fields. */
    public fun from(fields: SentenceFields): Gns =
      Gns(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        modeIndicator = fields.stringAt(MODE_INDICATOR),
        satelliteCount = fields.intAt(SATELLITE_COUNT),
        horizontalDilution = fields.doubleAt(HORIZONTAL_DILUTION),
        altitude = fields.doubleAt(ALTITUDE),
        geoidalSeparation = fields.doubleAt(GEOIDAL_SEPARATION),
        dgpsAge = fields.doubleAt(DGPS_AGE),
        dgpsStationId = fields.stringAt(DGPS_STATION_ID),
        navStatus = fields.advisoryCodedAt(NAV_STATUS, NavStatus.entries),
      )
  }
}

/**
 * Pseudorange noise statistics: how big the receiver thinks its own error is.
 *
 * Example: `$GPGST,182141.000,15.5,15.3,7.2,21.8,0.9,0.5,0.8*54`
 *
 * The error ellipse is the useful part -- a fix with a 15 metre semi-major axis is a different
 * thing from one with 0.5, and no other sentence says so.
 *
 * @property time UTC of the fix these statistics describe
 * @property rmsResidual total RMS standard deviation of the ranges going into the solution
 * @property semiMajorError standard deviation of the error ellipse's semi-major axis, metres
 * @property semiMinorError standard deviation of the error ellipse's semi-minor axis, metres
 * @property errorEllipseOrientation orientation of the semi-major axis, degrees true
 * @property latitudeError standard deviation of the latitude error, metres
 * @property longitudeError standard deviation of the longitude error, metres
 * @property altitudeError standard deviation of the altitude error, metres
 */
public data class Gst(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val rmsResidual: Double? = null,
  val semiMajorError: Double? = null,
  val semiMinorError: Double? = null,
  val errorEllipseOrientation: Double? = null,
  val latitudeError: Double? = null,
  val longitudeError: Double? = null,
  val altitudeError: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        time?.let { NmeaDateTime.formatTime(it) },
        rmsResidual.field(),
        semiMajorError.field(),
        semiMinorError.field(),
        errorEllipseOrientation.field(),
        latitudeError.field(),
        longitudeError.field(),
        altitudeError.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GST"

    private const val TIME = 0
    private const val RMS_RESIDUAL = 1
    private const val SEMI_MAJOR = 2
    private const val SEMI_MINOR = 3
    private const val ORIENTATION = 4
    private const val LATITUDE_ERROR = 5
    private const val LONGITUDE_ERROR = 6
    private const val ALTITUDE_ERROR = 7

    /** Reads a GST sentence from its fields. */
    public fun from(fields: SentenceFields): Gst =
      Gst(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        rmsResidual = fields.doubleAt(RMS_RESIDUAL),
        semiMajorError = fields.doubleAt(SEMI_MAJOR),
        semiMinorError = fields.doubleAt(SEMI_MINOR),
        errorEllipseOrientation = fields.doubleAt(ORIENTATION),
        latitudeError = fields.doubleAt(LATITUDE_ERROR),
        longitudeError = fields.doubleAt(LONGITUDE_ERROR),
        altitudeError = fields.doubleAt(ALTITUDE_ERROR),
      )
  }
}

/**
 * Satellite fault detection: which satellite the receiver believes is lying, if any.
 *
 * Example: `$GNGBS,003956.00,2.3,3.5,4.0,,,,,,*55`
 *
 * RAIM output. When the receiver has enough satellites to cross-check them it can single one out as
 * the likely bad measurement; the error estimates are reported whether or not it does, which is why
 * the example above fills the first four fields and leaves the rest empty.
 *
 * @property time UTC of the GGA or GNS fix this describes
 * @property latitudeError expected 1-sigma latitude error, metres
 * @property longitudeError expected 1-sigma longitude error, metres
 * @property altitudeError expected 1-sigma altitude error, metres
 * @property satelliteId the most likely failed satellite, 1 to 138
 * @property probability probability of missed detection for that satellite
 * @property bias estimate of the bias on that satellite, metres
 * @property biasStandardDeviation standard deviation of [bias]
 * @property systemId constellation this applies to, added in NMEA 4.10
 * @property signalId signal this applies to, added in NMEA 4.10. Neither this nor [systemId] could
 *   be read by the parser this replaces, which declared eight fields; u-blox receivers in the
 *   sample logs send ten.
 */
public data class Gbs(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val latitudeError: Double? = null,
  val longitudeError: Double? = null,
  val altitudeError: Double? = null,
  val satelliteId: String? = null,
  val probability: Double? = null,
  val bias: Double? = null,
  val biasStandardDeviation: Double? = null,
  val systemId: Int? = null,
  val signalId: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      buildList {
        add(time?.let { NmeaDateTime.formatTime(it) })
        add(latitudeError.field())
        add(longitudeError.field())
        add(altitudeError.field())
        add(satelliteId)
        add(probability.field())
        add(bias.field())
        add(biasStandardDeviation.field())
        // NMEA 4.10 additions; a device from before it sends eight fields and should get eight
        // back rather than two empties claiming it reported a constellation.
        if (hasSystemFields) {
          add(systemId?.toString())
          add(signalId?.toString())
        }
      },
    )

  private val hasSystemFields: Boolean
    get() = systemId != null || signalId != null

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GBS"

    private const val TIME = 0
    private const val LATITUDE_ERROR = 1
    private const val LONGITUDE_ERROR = 2
    private const val ALTITUDE_ERROR = 3
    private const val SATELLITE_ID = 4
    private const val PROBABILITY = 5
    private const val BIAS = 6
    private const val BIAS_DEVIATION = 7
    private const val SYSTEM_ID = 8
    private const val SIGNAL_ID = 9

    /** Reads a GBS sentence from its fields. */
    public fun from(fields: SentenceFields): Gbs =
      Gbs(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        latitudeError = fields.doubleAt(LATITUDE_ERROR),
        longitudeError = fields.doubleAt(LONGITUDE_ERROR),
        altitudeError = fields.doubleAt(ALTITUDE_ERROR),
        satelliteId = fields.stringAt(SATELLITE_ID),
        probability = fields.doubleAt(PROBABILITY),
        bias = fields.doubleAt(BIAS),
        biasStandardDeviation = fields.doubleAt(BIAS_DEVIATION),
        systemId = fields.advisoryIntAt(SYSTEM_ID),
        signalId = fields.advisoryIntAt(SIGNAL_ID),
      )
  }
}

/**
 * Range residuals: how far each satellite's measurement sits from the fix that was computed.
 *
 * Example: `$GPGRS,150119.000,1,-0.33,-2.59,3.03,-0.09,-2.98,7.12,-15.6,17.0,,,,*5A`
 *
 * The companion to [Gst] and [Gbs]. Where GST reports the shape of the error ellipse and GBS names
 * the satellite most likely to be at fault, this gives the raw per-satellite disagreement that both
 * are derived from. A residual far larger than its neighbours is a satellite worth distrusting.
 *
 * The residuals are positional: the nth here belongs to the nth satellite in the [Gsa] of the same
 * cycle, which is the only place their identities appear. Nothing links the two sentences but their
 * order in the burst.
 *
 * @property time UTC of the GGA or GNS fix these residuals belong to
 * @property computedAfterFix `false` when the residuals were used in computing the fix, `true` when
 *   they were recomputed from it afterwards. The two are not interchangeable -- residuals that went
 *   into a solution are smaller than the ones measured against it.
 * @property residuals metres, up to twelve, ordered to match the GSA of the same cycle. A blank
 *   slot reads as `null` rather than zero: a satellite that contributed nothing has no residual,
 *   and zero would mean a perfect one.
 * @property systemId which constellation, added in NMEA 4.11
 * @property signalId which signal, added in NMEA 4.11
 */
public data class Grs(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val computedAfterFix: Boolean? = null,
  val residuals: List<Double?> = emptyList(),
  val systemId: Int? = null,
  val signalId: Int? = null,
) : Sentence {

  init {
    require(residuals.size <= RESIDUAL_SLOTS) {
      "GRS carries at most $RESIDUAL_SLOTS residuals, got ${residuals.size}"
    }
  }

  override val id: String
    get() = ID

  /** The residuals the receiver actually reported, with the empty slots left out. */
  public val reportedResiduals: List<Double>
    get() = residuals.filterNotNull()

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      buildList {
        add(time?.let { NmeaDateTime.formatTime(it) })
        add(computedAfterFix?.let { if (it) "1" else "0" })
        for (slot in 0 until RESIDUAL_SLOTS) add(residuals.getOrNull(slot).field())
        if (systemId != null || signalId != null) {
          add(systemId?.toString())
          add(signalId?.toString())
        }
      },
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GRS"

    /** Residuals one GRS sentence can carry, matching GSA's twelve satellite slots. */
    public const val RESIDUAL_SLOTS: Int = 12

    private const val TIME = 0
    private const val MODE = 1
    private const val FIRST_RESIDUAL = 2

    /** Reads a GRS sentence from its fields. */
    public fun from(fields: SentenceFields): Grs {
      val systemIdIndex = FIRST_RESIDUAL + RESIDUAL_SLOTS
      return Grs(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        // 0 and 1 are the whole of the defined range, so an unreadable value is not a third
        // meaning to be preserved -- it is a field that said nothing.
        computedAfterFix = fields.advisoryIntAt(MODE)?.let { if (it in 0..1) it == 1 else null },
        residuals = List(RESIDUAL_SLOTS) { fields.doubleAt(FIRST_RESIDUAL + it) },
        systemId = fields.advisoryIntAt(systemIdIndex),
        signalId = fields.advisoryIntAt(systemIdIndex + 1),
      )
    }
  }
}
