package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.SatelliteInfo
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea

/**
 * Satellites in view, with their sky positions.
 *
 * Example: `$GPGSV,3,1,11,03,03,111,00,04,15,270,00,06,01,010,00,13,06,292,00*74`
 *
 * Receivers send these in groups: [sentenceCount] says how many make up the group and
 * [sentenceIndex] which one this is, each carrying up to four satellites. A receiver may report
 * more satellites than the format's twelve, and may report a satellite it is not tracking, in which
 * case that satellite's [SatelliteInfo.noise] is `null`.
 *
 * @property sentenceCount sentences in this group
 * @property sentenceIndex which sentence this is, counting from 1
 * @property satellitesInView total across the whole group, not just this sentence
 * @property satellites the satellites this sentence carries, at most four
 * @property signalId signal this sentence describes, added in NMEA 4.11 and absent from older
 *   devices. The Java implementation did not read this field.
 */
public data class Gsv(
  override val talker: TalkerId,
  val sentenceCount: Int? = null,
  val sentenceIndex: Int? = null,
  val satellitesInView: Int? = null,
  val satellites: List<SatelliteInfo> = emptyList(),
  val signalId: Int? = null,
) : Sentence {

  init {
    require(satellites.size <= SATELLITES_PER_SENTENCE) {
      "A GSV sentence carries at most $SATELLITES_PER_SENTENCE satellites, got ${satellites.size}"
    }
  }

  override val id: String
    get() = ID

  /** True if this is the first sentence of its group. */
  public val isFirst: Boolean
    get() = sentenceIndex == 1

  /** True if this is the last sentence of its group. */
  public val isLast: Boolean
    get() = sentenceIndex != null && sentenceIndex == sentenceCount

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      buildList {
        add(sentenceCount?.toString())
        add(sentenceIndex?.toString())
        add(satellitesInView?.let { NmeaFormat.integer(it, 2) })
        for (slot in 0 until SATELLITES_PER_SENTENCE) {
          val satellite = satellites.getOrNull(slot)
          add(satellite?.id)
          add(satellite?.elevation?.let { NmeaFormat.integer(it, 2) })
          add(satellite?.azimuth?.let { NmeaFormat.integer(it, 3) })
          add(satellite?.noise?.let { NmeaFormat.integer(it, 2) })
        }
        if (signalId != null) add(signalId.toString())
      },
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GSV"

    /** Satellites one GSV sentence can describe. */
    public const val SATELLITES_PER_SENTENCE: Int = 4

    private const val SENTENCE_COUNT = 0
    private const val SENTENCE_INDEX = 1
    private const val SATELLITES_IN_VIEW = 2
    private const val FIRST_SATELLITE = 3
    private const val FIELDS_PER_SATELLITE = 4
    private const val ELEVATION_OFFSET = 1
    private const val AZIMUTH_OFFSET = 2
    private const val NOISE_OFFSET = 3

    /** Reads a GSV sentence from its fields. */
    public fun from(fields: SentenceFields): Gsv {
      // A satellite counts as reported once it has an id. Its sky position may be missing --
      // "02,,,26" is a satellite with a signal but no fix on where it is -- and requiring
      // elevation and azimuth here silently dropped those.
      val satellites =
        (0 until SATELLITES_PER_SENTENCE).mapNotNull { slot ->
          val base = FIRST_SATELLITE + slot * FIELDS_PER_SATELLITE
          val id = fields.stringAt(base) ?: return@mapNotNull null
          SatelliteInfo(
            id = id,
            elevation = fields.intAt(base + ELEVATION_OFFSET),
            azimuth = fields.intAt(base + AZIMUTH_OFFSET),
            noise = fields.intAt(base + NOISE_OFFSET),
          )
        }

      val signalIdIndex = FIRST_SATELLITE + SATELLITES_PER_SENTENCE * FIELDS_PER_SATELLITE
      return Gsv(
        talker = fields.talker,
        sentenceCount = fields.intAt(SENTENCE_COUNT),
        sentenceIndex = fields.intAt(SENTENCE_INDEX),
        satellitesInView = fields.intAt(SATELLITES_IN_VIEW),
        satellites = satellites,
        signalId = fields.intAt(signalIdIndex),
      )
    }
  }
}
