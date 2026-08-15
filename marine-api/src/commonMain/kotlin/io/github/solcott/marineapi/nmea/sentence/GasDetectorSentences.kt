package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import kotlinx.datetime.LocalDateTime

/**
 * A gas concentration reading from a Boreal Laser open-path detector.
 *
 * Example: `$GFDTA,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3C`
 *
 * Manufacturer output rather than a standard sentence, and it shows: the timestamp is `yyyy/MM/dd
 * HH:mm:ss`, complete with a space, where NMEA would use `hhmmss` and a separate `ddmmyy`. [Dtb] is
 * the same layout on a second channel.
 *
 * Some units omit the leading channel number, shortening the sentence to seven fields; the reader
 * detects that from the field count, as the implementation this replaces did.
 *
 * @property channelNumber measurement channel, absent on units that do not report one
 * @property gasConcentration concentration in ppm-metres
 * @property confidenceFactor R-squared confidence in the fit, percent
 * @property distance path length to the reflector, metres
 * @property lightLevel returned light level
 * @property dateTime instrument timestamp; local to the instrument, with no zone information
 * @property serialNumber instrument serial number
 * @property statusCode instrument status
 */
public data class Dta(
  override val talker: TalkerId,
  val channelNumber: Int? = null,
  val gasConcentration: Double? = null,
  val confidenceFactor: Double? = null,
  val distance: Double? = null,
  val lightLevel: Double? = null,
  val dateTime: LocalDateTime? = null,
  val serialNumber: String? = null,
  val statusCode: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, gasReadingFields(this))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "DTA"

    /** Reads a DTA sentence from its fields. */
    public fun from(fields: SentenceFields): Dta = fields.readGasReading(fields.talker)
  }
}

/**
 * A gas concentration reading on a second channel, otherwise identical to [Dta].
 *
 * Example: `$GFDTB,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3F`
 *
 * [channelNumber] is read from the sentence like any other field. The implementation this replaces
 * returned a hardcoded `2` here regardless of what the sentence said, which meant a DTB naming any
 * other channel was reported as channel 2.
 */
public data class Dtb(
  override val talker: TalkerId,
  val channelNumber: Int? = null,
  val gasConcentration: Double? = null,
  val confidenceFactor: Double? = null,
  val distance: Double? = null,
  val lightLevel: Double? = null,
  val dateTime: LocalDateTime? = null,
  val serialNumber: String? = null,
  val statusCode: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      gasReadingFields(
        Dta(
          talker,
          channelNumber,
          gasConcentration,
          confidenceFactor,
          distance,
          lightLevel,
          dateTime,
          serialNumber,
          statusCode,
        )
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "DTB"

    /** Reads a DTB sentence from its fields. */
    public fun from(fields: SentenceFields): Dtb {
      val reading = fields.readGasReading(fields.talker)
      return Dtb(
        talker = reading.talker,
        channelNumber = reading.channelNumber,
        gasConcentration = reading.gasConcentration,
        confidenceFactor = reading.confidenceFactor,
        distance = reading.distance,
        lightLevel = reading.lightLevel,
        dateTime = reading.dateTime,
        serialNumber = reading.serialNumber,
        statusCode = reading.statusCode,
      )
    }
  }
}

/** Fields the eight-field form carries; the seven-field form drops the first. */
private const val FULL_FIELD_COUNT = 8

private fun SentenceFields.readGasReading(talker: TalkerId): Dta {
  // Units that report no channel send seven fields, shifting everything left by one.
  val hasChannel = size >= FULL_FIELD_COUNT
  val shift = if (hasChannel) 0 else 1
  return Dta(
    talker = talker,
    channelNumber = if (hasChannel) intAt(0) else null,
    gasConcentration = doubleAt(1 - shift),
    confidenceFactor = doubleAt(2 - shift),
    distance = doubleAt(3 - shift),
    lightLevel = doubleAt(4 - shift),
    dateTime = instrumentDateTimeAt(5 - shift),
    serialNumber = stringAt(6 - shift),
    statusCode = intAt(7 - shift),
  )
}

private fun gasReadingFields(reading: Dta): List<String?> = buildList {
  if (reading.channelNumber != null) add(reading.channelNumber.toString())
  add(reading.gasConcentration.field())
  add(reading.confidenceFactor.field())
  add(reading.distance.field())
  add(reading.lightLevel.field())
  add(reading.dateTime?.let { formatInstrumentDateTime(it) })
  add(reading.serialNumber)
  add(reading.statusCode?.toString())
}

/**
 * Reads a `yyyy/MM/dd HH:mm:ss` timestamp.
 *
 * Not [SentenceFields.dateAt] or [SentenceFields.timeAt]: this is the instrument's own format, not
 * NMEA's, and it packs a whole date and time into one field.
 */
private fun SentenceFields.instrumentDateTimeAt(index: Int): LocalDateTime? {
  val raw = stringAt(index) ?: return null
  val parts = raw.split(' ')
  val date = parts.getOrNull(0)?.split('/')
  val time = parts.getOrNull(1)?.split(':')
  val numbers =
    if (parts.size == 2 && date?.size == 3 && time?.size == 3) {
      (date + time).map { it.toIntOrNull() }
    } else {
      null
    }
  if (numbers == null || numbers.any { it == null }) {
    throw NmeaFieldException(
      "$talker$id field $index is not a yyyy/MM/dd HH:mm:ss timestamp: \"$raw\""
    )
  }
  val parsed = numbers.map { it!! }
  return runCatching {
    LocalDateTime(parsed[0], parsed[1], parsed[2], parsed[3], parsed[4], parsed[5])
  }
    .getOrElse {
      throw NmeaFieldException("$talker$id field $index is not a valid date and time: \"$raw\"")
    }
}

private fun formatInstrumentDateTime(value: LocalDateTime): String =
  NmeaFormat.integer(value.year, 4) +
    "/" +
    NmeaFormat.integer(value.monthNumber, 2) +
    "/" +
    NmeaFormat.integer(value.dayOfMonth, 2) +
    " " +
    NmeaFormat.integer(value.hour, 2) +
    ":" +
    NmeaFormat.integer(value.minute, 2) +
    ":" +
    NmeaFormat.integer(value.second, 2)
