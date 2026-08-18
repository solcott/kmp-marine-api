package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import kotlin.math.absoluteValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.number

/**
 * UTC time and date, with the local zone offset.
 *
 * Example: `$GPZDA,032915,07,08,2004,00,00*4D`
 *
 * The only sentence that carries a zone offset, and the only one whose year is four digits, so no
 * pivot-year guess is needed. The offset describes the operator's local time; the time and date
 * fields are UTC regardless.
 *
 * @property time UTC time of day
 * @property date UTC date
 * @property localZoneOffset offset of local time from UTC, `null` when the device reports none
 */
public data class Zda(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val date: LocalDate? = null,
  val localZoneOffset: UtcOffset? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** [date] and [time] together, or `null` unless the sentence carried both. */
  public val dateTime: LocalDateTime?
    get() = if (date != null && time != null) LocalDateTime(date, time) else null

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        time?.let { NmeaDateTime.formatTime(it) },
        date?.let { NmeaFormat.integer(it.day, 2) },
        date?.let { NmeaFormat.integer(it.month.number, 2) },
        date?.let { NmeaFormat.integer(it.year, 4) },
        localZoneOffset?.let { NmeaFormat.integer(it.totalSeconds / 3600, 2) },
        localZoneOffset?.let { NmeaFormat.integer((it.totalSeconds / 60 % 60).absoluteValue, 2) },
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ZDA"

    private const val TIME = 0
    private const val DAY = 1
    private const val MONTH = 2
    private const val YEAR = 3
    private const val ZONE_HOURS = 4
    private const val ZONE_MINUTES = 5

    /** Reads a ZDA sentence from its fields. */
    public fun from(fields: SentenceFields): Zda {
      val day = fields.intAt(DAY)
      val month = fields.intAt(MONTH)
      val year = fields.intAt(YEAR)
      return Zda(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        date =
          if (day != null && month != null && year != null) LocalDate(year, month, day) else null,
        // The minutes field carries no sign of its own; the format says to apply the hours'.
        localZoneOffset =
          NmeaDateTime.utcOffset(fields.intAt(ZONE_HOURS), fields.intAt(ZONE_MINUTES)),
      )
    }
  }
}
