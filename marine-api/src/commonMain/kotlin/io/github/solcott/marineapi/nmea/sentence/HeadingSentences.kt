package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CompassPoint
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
