package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.Nmea
import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/**
 * Datum reference: which geodetic datum the position sentences around this one are expressed in.
 *
 * Example: `$GPDTM,W84,C*52`
 *
 * Almost always `W84`, WGS-84, which is what GPS computes in. A receiver configured for a local
 * datum says so here, and the offsets give the shift from that datum back to WGS-84 -- so a
 * consumer that ignores this sentence and assumes WGS-84 can be wrong by hundreds of metres.
 *
 * Most receivers send only the first field or two, as the example does.
 *
 * @property datumCode local datum code, e.g. `W84`
 * @property datumSubCode local datum subcode
 * @property latitudeOffset latitude offset in minutes, paired with [latitudeOffsetDirection]
 * @property latitudeOffsetDirection `N` or `S`
 * @property longitudeOffset longitude offset in minutes, paired with [longitudeOffsetDirection]
 * @property longitudeOffsetDirection `E` or `W`
 * @property altitudeOffset altitude offset in metres
 * @property datumName datum name, e.g. `W84`
 */
public data class Dtm(
  override val talker: TalkerId,
  val datumCode: String? = null,
  val datumSubCode: String? = null,
  val latitudeOffset: Double? = null,
  val latitudeOffsetDirection: CompassPoint? = null,
  val longitudeOffset: Double? = null,
  val longitudeOffsetDirection: CompassPoint? = null,
  val altitudeOffset: Double? = null,
  val datumName: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        datumCode,
        datumSubCode,
        latitudeOffset.field(),
        latitudeOffsetDirection.field(),
        longitudeOffset.field(),
        longitudeOffsetDirection.field(),
        altitudeOffset.field(),
        datumName,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "DTM"

    private const val DATUM_CODE = 0
    private const val DATUM_SUBCODE = 1
    private const val LATITUDE_OFFSET = 2
    private const val LATITUDE_DIRECTION = 3
    private const val LONGITUDE_OFFSET = 4
    private const val LONGITUDE_DIRECTION = 5
    private const val ALTITUDE_OFFSET = 6
    private const val DATUM_NAME = 7

    private val NORTH_SOUTH = listOf(CompassPoint.NORTH, CompassPoint.SOUTH)
    private val EAST_WEST = listOf(CompassPoint.EAST, CompassPoint.WEST)

    /** Reads a DTM sentence from its fields. */
    public fun from(fields: SentenceFields): Dtm =
      Dtm(
        talker = fields.talker,
        datumCode = fields.stringAt(DATUM_CODE),
        datumSubCode = fields.stringAt(DATUM_SUBCODE),
        latitudeOffset = fields.doubleAt(LATITUDE_OFFSET),
        latitudeOffsetDirection = fields.codedAt(LATITUDE_DIRECTION, NORTH_SOUTH),
        longitudeOffset = fields.doubleAt(LONGITUDE_OFFSET),
        longitudeOffsetDirection = fields.codedAt(LONGITUDE_DIRECTION, EAST_WEST),
        altitudeOffset = fields.doubleAt(ALTITUDE_OFFSET),
        datumName = fields.stringAt(DATUM_NAME),
      )
  }
}

/**
 * A text message from the receiver: firmware banners, warnings, diagnostics.
 *
 * Example: `$GNTXT,01,01,00,PDTINFO*1F`
 *
 * A long message is split across several sentences, numbered by [messageIndex] out of
 * [messageCount].
 *
 * [message] runs to the end of the sentence rather than stopping at the next comma. The field is
 * nominally the last one, but real receivers put commas inside it -- a u-blox in the sample logs
 * sends `$GNTXT,01,01,01,0,000222,0000,08A0,08A0,-37.124,0`, where everything from `0` onwards is
 * one diagnostic string. Reading only as far as the next delimiter would truncate that to a single
 * character.
 *
 * @property messageCount sentences this message is split across
 * @property messageIndex which sentence this is, counting from 1
 * @property identifier message type. u-blox uses a two-digit severity -- `00` error, `01` warning,
 *   `02` notice, `07` user -- while other makers put a name here, so it stays text.
 * @property message the text
 */
public data class Txt(
  override val talker: TalkerId,
  val messageCount: Int? = null,
  val messageIndex: Int? = null,
  val identifier: String? = null,
  val message: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        messageCount?.let { NmeaFormat.integer(it, 2) },
        messageIndex?.let { NmeaFormat.integer(it, 2) },
        identifier,
        message,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TXT"

    private const val MESSAGE_COUNT = 0
    private const val MESSAGE_INDEX = 1
    private const val IDENTIFIER = 2
    private const val MESSAGE = 3

    /** Reads a TXT sentence from its fields. */
    public fun from(fields: SentenceFields): Txt =
      Txt(
        talker = fields.talker,
        messageCount = fields.advisoryIntAt(MESSAGE_COUNT),
        messageIndex = fields.advisoryIntAt(MESSAGE_INDEX),
        identifier = fields.stringAt(IDENTIFIER),
        // Everything from here on, commas included: the message is free text, not a field list.
        message =
          fields
            .stringsFrom(MESSAGE)
            .joinToString(Nmea.FIELD_DELIMITER.toString()) { it.orEmpty() }
            .takeIf { it.isNotEmpty() },
      )
  }
}
