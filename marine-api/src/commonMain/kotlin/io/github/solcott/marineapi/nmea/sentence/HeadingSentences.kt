package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import kotlin.math.abs

/**
 * Magnetic sensor heading, with the deviation and variation needed to correct it.
 *
 * Example: `$HCHDG,123.4,1.2,E,4.8,W*4E`
 *
 * Deviation is the error the vessel's own magnetism induces in this compass; variation is the local
 * difference between magnetic and true north. Both are carried as a magnitude plus a separate
 * `E`/`W` field, and both are modelled that way here rather than as a signed number -- for the same
 * reason as [Rmc.magneticVariation], and because the Java implementation demonstrated the hazard
 * twice over: it folded the direction into the sign, returned `0` unsigned so the direction
 * vanished at zero, and used the opposite sign convention here from the one it used in RMC.
 *
 * @property heading magnetic sensor heading, degrees
 * @property deviation magnetic deviation as an unsigned magnitude, paired with [deviationDirection]
 * @property deviationDirection which way [deviation] points, [CompassPoint.EAST] or
 *   [CompassPoint.WEST]
 * @property variation magnetic variation as an unsigned magnitude, paired with [variationDirection]
 * @property variationDirection which way [variation] points
 */
public data class Hdg(
  override val talker: TalkerId,
  val heading: Double? = null,
  val deviation: Double? = null,
  val deviationDirection: CompassPoint? = null,
  val variation: Double? = null,
  val variationDirection: CompassPoint? = null,
) : Sentence {

  init {
    requireEastWest(deviation, deviationDirection, "deviation")
    requireEastWest(variation, variationDirection, "variation")
  }

  override val id: String
    get() = ID

  /** [deviation] signed east-positive, or `null` when the sentence reports none. */
  public val deviationEastPositive: Double?
    get() = deviation?.let { if (deviationDirection == CompassPoint.WEST) -it else it }

  /** [variation] signed east-positive, or `null` when the sentence reports none. */
  public val variationEastPositive: Double?
    get() = variation?.let { if (variationDirection == CompassPoint.WEST) -it else it }

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        heading.field(),
        deviation.field(),
        deviationDirection.field(),
        variation.field(),
        variationDirection.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HDG"

    private const val HEADING = 0
    private const val DEVIATION = 1
    private const val DEVIATION_DIRECTION = 2
    private const val VARIATION = 3
    private const val VARIATION_DIRECTION = 4

    /** Reads an HDG sentence from its fields. */
    public fun from(fields: SentenceFields): Hdg {
      val deviation = fields.magneticAngleAt(DEVIATION, DEVIATION_DIRECTION)
      val variation = fields.magneticAngleAt(VARIATION, VARIATION_DIRECTION)
      return Hdg(
        talker = fields.talker,
        heading = fields.doubleAt(HEADING),
        deviation = deviation.magnitude,
        deviationDirection = deviation.direction,
        variation = variation.magnitude,
        variationDirection = variation.direction,
      )
    }
  }
}

/**
 * Vessel heading with respect to magnetic north.
 *
 * Example: `$HCHDM,123.4,M*23`
 *
 * The `M` field is a fixed marker and is written back as a constant.
 *
 * @property heading degrees magnetic
 */
public data class Hdm(override val talker: TalkerId, val heading: Double? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(heading.field(), MAGNETIC_MARKER.toString()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HDM"

    private const val MAGNETIC_MARKER = 'M'
    private const val HEADING = 0

    /** Reads an HDM sentence from its fields. */
    public fun from(fields: SentenceFields): Hdm =
      Hdm(talker = fields.talker, heading = fields.doubleAt(HEADING))
  }
}

/**
 * Vessel heading with respect to true north.
 *
 * Example: `$GPHDT,274.07,T*03`
 *
 * The `T` field is a fixed marker and is written back as a constant.
 *
 * @property heading degrees true
 */
public data class Hdt(override val talker: TalkerId, val heading: Double? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(heading.field(), TRUE_MARKER.toString()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HDT"

    private const val TRUE_MARKER = 'T'
    private const val HEADING = 0

    /** Reads an HDT sentence from its fields. */
    public fun from(fields: SentenceFields): Hdt =
      Hdt(talker = fields.talker, heading = fields.doubleAt(HEADING))
  }
}

/** A magnetic magnitude together with the direction that gives it a sign. */
internal class MagneticAngle(val magnitude: Double?, val direction: CompassPoint?)

/**
 * Reads a magnetic magnitude and its `E`/`W` direction as one value, or nothing.
 *
 * The two fields only mean something together: a magnitude with no direction cannot be signed, and
 * a direction with no magnitude says nothing. So either both are read or neither is, and a
 * direction field holding something other than `E` or `W` discards the pair rather than the whole
 * sentence.
 *
 * That last part is the tolerant half of the rule [SentenceFields.advisoryCodedAt] describes,
 * applied to a pair instead of a field. The direction is load-bearing *for the magnitude* -- it is
 * the sign, so misreading it inverts a compass correction -- but the pair as a whole is auxiliary
 * to a sentence whose job is to report position, time, speed and course. Two receivers in this
 * project's sample logs, a Saab R4 and a Motorola T805, put their FAA mode in this field; failing
 * those lines would throw away a valid fix over a correction the device never sent.
 */
internal fun SentenceFields.magneticAngleAt(
  magnitudeIndex: Int,
  directionIndex: Int,
): MagneticAngle {
  val magnitude = doubleAt(magnitudeIndex)
  val direction = advisoryCodedAt(directionIndex, EAST_WEST)
  return if (magnitude == null || direction == null) {
    MagneticAngle(null, null)
  } else {
    MagneticAngle(abs(magnitude), direction)
  }
}

private val EAST_WEST = listOf(CompassPoint.EAST, CompassPoint.WEST)

/** Rejects a magnitude that is negative, unpaired, or paired with a north/south indicator. */
internal fun requireEastWest(magnitude: Double?, direction: CompassPoint?, name: String) {
  require(magnitude == null || magnitude >= 0.0) {
    "Magnetic $name is an unsigned magnitude; the direction goes in its own property: $magnitude"
  }
  require(direction == null || direction == CompassPoint.EAST || direction == CompassPoint.WEST) {
    "Magnetic $name direction must be EAST or WEST: $direction"
  }
  require(magnitude == null || direction != null) {
    "A magnetic $name of $magnitude needs a direction to mean anything"
  }
}

/**
 * True heading with a validity flag: the modern replacement for [Hdt].
 *
 * Example: `$GNTHS,244.28,A*11`
 *
 * Added in NMEA 4.1 to give HDT something it never had -- a way of saying the heading is not to be
 * trusted. A gyro that has lost its reference still outputs a number, and an [Hdt] carrying that
 * number is indistinguishable from a good one.
 *
 * **gpsd does not document this sentence**, so unlike every other type here its layout does not
 * rest on that reference. It rests on the corpus instead, which is unusually direct evidence: a
 * Skytraq PX1172RH sends `$GNTHS,244.28,A` immediately followed by `$GNHDT,244.28,T`, so field 1 is
 * the same true heading HDT carries. Only `A` occurs there in the nine sentences available, so
 * [DataStatus.VOID] is read on the strength of the convention rather than of data.
 *
 * @property heading degrees true
 * @property status whether the heading is usable
 */
public data class Ths(
  override val talker: TalkerId,
  val heading: Double? = null,
  val status: DataStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(heading.field(), status.field()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "THS"

    private const val HEADING = 0
    private const val STATUS = 1

    /** Reads a THS sentence from its fields. */
    public fun from(fields: SentenceFields): Ths =
      Ths(
        talker = fields.talker,
        heading = fields.doubleAt(HEADING),
        // Advisory: NMEA 4.1 allows E, M and S here as well, none of which this models, and a
        // heading is still a heading when the mode letter is one we do not know.
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
      )
  }
}

/**
 * Heading to steer, as an autopilot or steering gear was commanded.
 *
 * Example: `$IIHSC,241.0,T,238.7,M*hh`
 *
 * A *command*, not a measurement: what the vessel has been told to steer, against [Hdt] and [Hdm]
 * which report where the bow is actually pointing. The difference between the two is the error the
 * steering gear is working to close.
 *
 * @property headingTrue degrees true
 * @property headingMagnetic degrees magnetic
 */
public data class Hsc(
  override val talker: TalkerId,
  val headingTrue: Double? = null,
  val headingMagnetic: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        headingTrue.field(),
        TRUE_MARKER.toString(),
        headingMagnetic.field(),
        MAGNETIC_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HSC"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val HEADING_TRUE = 0
    private const val HEADING_MAGNETIC = 2

    /** Reads an HSC sentence from its fields. */
    public fun from(fields: SentenceFields): Hsc =
      Hsc(
        talker = fields.talker,
        headingTrue = fields.doubleAt(HEADING_TRUE),
        headingMagnetic = fields.doubleAt(HEADING_MAGNETIC),
      )
  }
}
