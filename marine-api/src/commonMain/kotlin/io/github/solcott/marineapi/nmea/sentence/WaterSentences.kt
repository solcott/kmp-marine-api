package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/**
 * Depth below the transducer, given in all three units.
 *
 * Example: `$SDDBT,7.8,f,2.4,M,1.3,F*0D`
 *
 * Real sensors often report only one of the three conversions, so each is independently nullable --
 * `$SDDBT,,f,22.5,M,,F` is a normal sentence. The `f`, `M` and `F` fields are fixed markers naming
 * the value before them and are written back as constants.
 *
 * The unit letters are case-sensitive: lower-case `f` is feet, upper-case `F` is fathoms.
 *
 * @property depthFeet depth below the transducer in feet
 * @property depthMeters depth below the transducer in metres
 * @property depthFathoms depth below the transducer in fathoms
 */
public data class Dbt(
  override val talker: TalkerId,
  val depthFeet: Double? = null,
  val depthMeters: Double? = null,
  val depthFathoms: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        depthFeet.field(),
        FEET_MARKER.toString(),
        depthMeters.field(),
        METERS_MARKER.toString(),
        depthFathoms.field(),
        FATHOMS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "DBT"

    private const val FEET_MARKER = 'f'
    private const val METERS_MARKER = 'M'
    private const val FATHOMS_MARKER = 'F'

    private const val DEPTH_FEET = 0
    private const val DEPTH_METERS = 2
    private const val DEPTH_FATHOMS = 4

    /** Reads a DBT sentence from its fields. */
    public fun from(fields: SentenceFields): Dbt =
      Dbt(
        talker = fields.talker,
        depthFeet = fields.doubleAt(DEPTH_FEET),
        depthMeters = fields.doubleAt(DEPTH_METERS),
        depthFathoms = fields.doubleAt(DEPTH_FATHOMS),
      )
  }
}

/**
 * Depth of water relative to the transducer, with the transducer's own offset.
 *
 * Example: `$INDPT,2.3,0.0*46`
 *
 * @property depth depth relative to the transducer, metres
 * @property offset distance from the transducer, metres. Positive measures up to the water line, so
 *   adding it gives depth below the surface; negative measures down to the keel, so adding it gives
 *   clearance under the keel.
 * @property maximumRange maximum range scale in use, added in NMEA 3.0
 */
public data class Dpt(
  override val talker: TalkerId,
  val depth: Double? = null,
  val offset: Double? = null,
  val maximumRange: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(depth.field(), offset.field(), maximumRange.field()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "DPT"

    private const val DEPTH = 0
    private const val OFFSET = 1
    private const val MAXIMUM_RANGE = 2

    /** Reads a DPT sentence from its fields. */
    public fun from(fields: SentenceFields): Dpt =
      Dpt(
        talker = fields.talker,
        depth = fields.doubleAt(DEPTH),
        offset = fields.doubleAt(OFFSET),
        maximumRange = fields.doubleAt(MAXIMUM_RANGE),
      )
  }
}

/**
 * Mean temperature of the water.
 *
 * Example: `$INMTW,17.9,C*1B`
 *
 * The `C` field is a fixed marker; the format defines no other unit for this sentence, so it is
 * written back as a constant.
 *
 * @property temperature degrees Celsius
 */
public data class Mtw(override val talker: TalkerId, val temperature: Double? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(temperature.field(), CELSIUS_MARKER.toString()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MTW"

    private const val CELSIUS_MARKER = 'C'
    private const val TEMPERATURE = 0

    /** Reads an MTW sentence from its fields. */
    public fun from(fields: SentenceFields): Mtw =
      Mtw(talker = fields.talker, temperature = fields.doubleAt(TEMPERATURE))
  }
}
