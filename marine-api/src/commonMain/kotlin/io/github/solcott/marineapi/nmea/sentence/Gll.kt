package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.positionAt
import io.github.solcott.marineapi.nmea.positionFields
import kotlinx.datetime.LocalTime

/**
 * Geographic position, latitude and longitude.
 *
 * Example: `$GPGLL,6011.552,N,02501.941,E,120045,A*26`
 *
 * @property position latitude and longitude; `null` when the receiver has no fix
 * @property time UTC of this position
 * @property status whether the data is valid
 * @property faaMode FAA mode indicator, added in NMEA 2.3; `null` from an older device
 */
public data class Gll(
  override val talker: TalkerId,
  val position: Position? = null,
  val time: LocalTime? = null,
  val status: DataStatus? = null,
  val faaMode: FaaMode? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      positionFields(position) +
        listOf(time?.let { NmeaDateTime.formatTime(it) }, status.field(), faaMode.field()),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GLL"

    private const val LATITUDE = 0
    private const val LATITUDE_HEMISPHERE = 1
    private const val LONGITUDE = 2
    private const val LONGITUDE_HEMISPHERE = 3
    private const val TIME = 4
    private const val STATUS = 5
    private const val FAA_MODE = 6

    /** Reads a GLL sentence from its fields. */
    public fun from(fields: SentenceFields): Gll =
      Gll(
        talker = fields.talker,
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        time = fields.timeAt(TIME),
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        faaMode = fields.advisoryCodedAt(FAA_MODE, FaaMode.entries),
      )
  }
}
