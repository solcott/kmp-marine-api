package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CharCoded
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import kotlinx.datetime.LocalTime

/** Whether a receiver chose a setting for itself or was told it. */
public enum class AutoManual(override val code: Char) : CharCoded {
  /** The receiver picked it. */
  AUTOMATIC('A'),
  /** An operator or a controlling sentence set it. */
  MANUAL('M'),
}

/**
 * Status of a differential beacon receiver: what it is hearing, and how well.
 *
 * Example: `$GPMSS,55,27,318.0,100,*hh`
 *
 * Differential GPS corrections used to arrive over medium-wave radio beacons rather than the
 * internet, and this is that receiver reporting on its reception. Still emitted by marine DGPS
 * gear. Its counterpart [Msk] is the sentence that tunes it.
 *
 * @property signalStrength dB relative to 1 microvolt
 * @property signalToNoise dB
 * @property beaconFrequency kHz, in the 283.5 to 325.0 marine radiobeacon band
 * @property beaconBitRate bits per second: 25, 50, 100 or 200
 * @property channelNumber which of the receiver's channels this describes
 */
public data class Mss(
  override val talker: TalkerId,
  val signalStrength: Double? = null,
  val signalToNoise: Double? = null,
  val beaconFrequency: Double? = null,
  val beaconBitRate: Int? = null,
  val channelNumber: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        signalStrength.field(),
        signalToNoise.field(),
        beaconFrequency.field(),
        beaconBitRate?.toString(),
        channelNumber?.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MSS"

    private const val SIGNAL_STRENGTH = 0
    private const val SIGNAL_TO_NOISE = 1
    private const val FREQUENCY = 2
    private const val BIT_RATE = 3
    private const val CHANNEL = 4

    /** Reads an MSS sentence from its fields. */
    public fun from(fields: SentenceFields): Mss =
      Mss(
        talker = fields.talker,
        signalStrength = fields.doubleAt(SIGNAL_STRENGTH),
        signalToNoise = fields.doubleAt(SIGNAL_TO_NOISE),
        beaconFrequency = fields.doubleAt(FREQUENCY),
        beaconBitRate = fields.intAt(BIT_RATE),
        channelNumber = fields.intAt(CHANNEL),
      )
  }
}

/**
 * Tunes a differential beacon receiver. The command [Mss] answers.
 *
 * Example: `$GPMSK,318.0,A,100,M,10*hh`
 *
 * @property beaconFrequency kHz to tune to, in the 283.5 to 325.0 band
 * @property frequencyMode whether the receiver may search for a frequency itself
 * @property beaconBitRate bits per second: 25, 50, 100 or 200
 * @property bitRateMode whether the receiver may choose the rate itself
 * @property statusInterval seconds between [Mss] reports, or `null` for none
 */
public data class Msk(
  override val talker: TalkerId,
  val beaconFrequency: Double? = null,
  val frequencyMode: AutoManual? = null,
  val beaconBitRate: Int? = null,
  val bitRateMode: AutoManual? = null,
  val statusInterval: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        beaconFrequency.field(),
        frequencyMode.field(),
        beaconBitRate?.toString(),
        bitRateMode.field(),
        statusInterval?.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MSK"

    private const val FREQUENCY = 0
    private const val FREQUENCY_MODE = 1
    private const val BIT_RATE = 2
    private const val BIT_RATE_MODE = 3
    private const val STATUS_INTERVAL = 4

    /** Reads an MSK sentence from its fields. */
    public fun from(fields: SentenceFields): Msk =
      Msk(
        talker = fields.talker,
        beaconFrequency = fields.doubleAt(FREQUENCY),
        // Load-bearing: whether the frequency beside it was chosen or commanded changes what that
        // number means, so an unreadable mode is not something to shrug at.
        frequencyMode = fields.codedAt(FREQUENCY_MODE, AutoManual.entries),
        beaconBitRate = fields.intAt(BIT_RATE),
        bitRateMode = fields.codedAt(BIT_RATE_MODE, AutoManual.entries),
        statusInterval = fields.intAt(STATUS_INTERVAL),
      )
  }
}

/**
 * One page of the GPS almanac: the orbit of a single satellite.
 *
 * Example: `$GPALM,32,1,01,1122,00,441d,4e,16be,fd5e,a10c9f,4a2da4,686e81,58cbe1,0a4,001*76`
 *
 * The almanac is the coarse orbital model every satellite broadcasts for the whole constellation,
 * and it is what lets a receiver know where to look after a cold start. A full set arrives as one
 * ALM per satellite, numbered by [sentenceIndex] out of [sentenceCount].
 *
 * **Fields 5 to 15 are raw hexadecimal** and are kept as text. gpsd says so explicitly, and turning
 * them into numbers here would be asserting a scaling and a sign convention that the sentence does
 * not carry -- they are packed bit fields from the satellite's own navigation message, and
 * interpreting them needs the ICD, not this library.
 *
 * @property sentenceCount how many ALM sentences make up this almanac
 * @property sentenceIndex which one this is, counting from 1
 * @property satelliteId PRN of the satellite this page describes, 01 to 32
 * @property gpsWeek the week the almanac was issued in
 */
public data class Alm(
  override val talker: TalkerId,
  val sentenceCount: Int? = null,
  val sentenceIndex: Int? = null,
  val satelliteId: String? = null,
  val gpsWeek: Int? = null,
  val satelliteHealth: String? = null,
  val eccentricity: String? = null,
  val almanacReferenceTime: String? = null,
  val inclinationAngle: String? = null,
  val rateOfRightAscension: String? = null,
  val rootOfSemiMajorAxis: String? = null,
  val argumentOfPerigee: String? = null,
  val longitudeOfAscensionNode: String? = null,
  val meanAnomaly: String? = null,
  val clockParameterF0: String? = null,
  val clockParameterF1: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** The eleven hexadecimal orbital fields, in sentence order. */
  public val orbitalFields: List<String?>
    get() =
      listOf(
        satelliteHealth,
        eccentricity,
        almanacReferenceTime,
        inclinationAngle,
        rateOfRightAscension,
        rootOfSemiMajorAxis,
        argumentOfPerigee,
        longitudeOfAscensionNode,
        meanAnomaly,
        clockParameterF0,
        clockParameterF1,
      )

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        sentenceCount?.toString(),
        sentenceIndex?.toString(),
        satelliteId,
        gpsWeek?.toString(),
      ) + orbitalFields,
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ALM"

    private const val SENTENCE_COUNT = 0
    private const val SENTENCE_INDEX = 1
    private const val SATELLITE_ID = 2
    private const val GPS_WEEK = 3
    private const val FIRST_ORBITAL_FIELD = 4

    /** Reads an ALM sentence from its fields. */
    public fun from(fields: SentenceFields): Alm {
      val orbital = List(ORBITAL_FIELDS) { fields.stringAt(FIRST_ORBITAL_FIELD + it) }
      return Alm(
        talker = fields.talker,
        sentenceCount = fields.intAt(SENTENCE_COUNT),
        sentenceIndex = fields.intAt(SENTENCE_INDEX),
        // Kept as text: the PRN is written with a leading zero and re-encoding it from an Int
        // would turn "01" into "1".
        satelliteId = fields.stringAt(SATELLITE_ID),
        gpsWeek = fields.intAt(GPS_WEEK),
        satelliteHealth = orbital[0],
        eccentricity = orbital[1],
        almanacReferenceTime = orbital[2],
        inclinationAngle = orbital[3],
        rateOfRightAscension = orbital[4],
        rootOfSemiMajorAxis = orbital[5],
        argumentOfPerigee = orbital[6],
        longitudeOfAscensionNode = orbital[7],
        meanAnomaly = orbital[8],
        clockParameterF0 = orbital[9],
        clockParameterF1 = orbital[10],
      )
    }

    private const val ORBITAL_FIELDS = 11
  }
}

/**
 * A Cospas-Sarsat return link message: confirmation that a distress beacon was heard.
 *
 * Example: `$GPRLM,12345678,123456.00,1,ABCDEF*hh`
 *
 * Sent to a vessel whose emergency beacon has been detected, so the crew know the alert got
 * through. One of the few NMEA sentences that carries good news.
 *
 * @property beaconId the beacon's 15-character hexadecimal identity
 * @property time UTC the return link message was received
 * @property messageCode which kind of acknowledgement this is
 * @property messageBody the message itself, hexadecimal
 */
public data class Rlm(
  override val talker: TalkerId,
  val beaconId: String? = null,
  val time: LocalTime? = null,
  val messageCode: String? = null,
  val messageBody: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(beaconId, time?.let { NmeaDateTime.formatTime(it) }, messageCode, messageBody),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RLM"

    private const val BEACON_ID = 0
    private const val TIME = 1
    private const val MESSAGE_CODE = 2
    private const val MESSAGE_BODY = 3

    /** Reads an RLM sentence from its fields. */
    public fun from(fields: SentenceFields): Rlm =
      Rlm(
        talker = fields.talker,
        beaconId = fields.stringAt(BEACON_ID),
        time = fields.timeAt(TIME),
        messageCode = fields.stringAt(MESSAGE_CODE),
        messageBody = fields.stringAt(MESSAGE_BODY),
      )
  }
}

/**
 * Names which of several instruments on one bus sent the sentences around it.
 *
 * Example: `$IISTN,02*hh`
 *
 * A prefix rather than a report: it carries no data of its own, only an identifier for the
 * sentences that follow. Instruments sharing a talker id -- two depth sounders both talking as `SD`
 * -- use it to be told apart.
 *
 * @property talkerIdNumber which source the following sentences came from
 */
public data class Stn(override val talker: TalkerId, val talkerIdNumber: String? = null) :
  Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, listOf(talkerIdNumber))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "STN"

    private const val TALKER_ID_NUMBER = 0

    /** Reads an STN sentence from its fields. */
    public fun from(fields: SentenceFields): Stn =
      // Text, not a number: the field is written with a leading zero and an Int would lose it.
      Stn(talker = fields.talker, talkerIdNumber = fields.stringAt(TALKER_ID_NUMBER))
  }
}

/**
 * The frequency pair and mode a radio is set to.
 *
 * Example: `$CTFSI,020230,020230,m,5*hh`
 *
 * For remote control of a marine radio: what it transmits on, what it listens on, and how hard it
 * is driving the antenna.
 *
 * @property transmitFrequency the transmitting frequency, as the six-digit code the format uses
 * @property receiveFrequency the receiving frequency, likewise
 * @property communicationsMode the mode character, from NMEA's own table
 * @property powerLevel 0 for standby, then 1 (lowest) to 9 (highest)
 */
public data class Fsi(
  override val talker: TalkerId,
  val transmitFrequency: String? = null,
  val receiveFrequency: String? = null,
  val communicationsMode: Char? = null,
  val powerLevel: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        transmitFrequency,
        receiveFrequency,
        communicationsMode?.toString(),
        powerLevel?.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "FSI"

    private const val TRANSMIT = 0
    private const val RECEIVE = 1
    private const val MODE = 2
    private const val POWER = 3

    /** Reads an FSI sentence from its fields. */
    public fun from(fields: SentenceFields): Fsi =
      Fsi(
        talker = fields.talker,
        // Text: these are fixed-width frequency codes, and leading zeros carry meaning.
        transmitFrequency = fields.stringAt(TRANSMIT),
        receiveFrequency = fields.stringAt(RECEIVE),
        communicationsMode = fields.charAt(MODE),
        powerLevel = fields.intAt(POWER),
      )
  }
}

/**
 * The frequencies a radio is scanning, as a numbered series of sentences.
 *
 * Example: `$CTSFI,2,1,020230,m,021500,m*hh`
 *
 * A scan list too long for one sentence arrives as [sentenceCount] of them. Each carries a variable
 * number of frequency-and-mode pairs, the way [Xdr] carries transducer quadruples.
 *
 * @property sentenceCount how many sentences the list is spread over
 * @property sentenceIndex which one this is, counting from 1
 * @property frequencies the pairs this sentence carries
 */
public data class Sfi(
  override val talker: TalkerId,
  val sentenceCount: Int? = null,
  val sentenceIndex: Int? = null,
  val frequencies: List<ScannedFrequency> = emptyList(),
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      buildList {
        add(sentenceCount?.toString())
        add(sentenceIndex?.toString())
        for (entry in frequencies) {
          add(entry.frequency)
          add(entry.mode?.toString())
        }
      },
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "SFI"

    private const val SENTENCE_COUNT = 0
    private const val SENTENCE_INDEX = 1
    private const val FIRST_PAIR = 2
    private const val FIELDS_PER_PAIR = 2

    /** Reads an SFI sentence from its fields. */
    public fun from(fields: SentenceFields): Sfi {
      val pairs = ((fields.size - FIRST_PAIR).coerceAtLeast(0)) / FIELDS_PER_PAIR
      return Sfi(
        talker = fields.talker,
        sentenceCount = fields.intAt(SENTENCE_COUNT),
        sentenceIndex = fields.intAt(SENTENCE_INDEX),
        frequencies =
          (0 until pairs).mapNotNull { pair ->
            val base = FIRST_PAIR + pair * FIELDS_PER_PAIR
            val frequency = fields.stringAt(base)
            val mode = fields.charAt(base + 1)
            if (frequency == null && mode == null) null else ScannedFrequency(frequency, mode)
          },
      )
    }
  }
}

/**
 * One frequency a radio is scanning, and the mode it listens in.
 *
 * @property frequency the six-digit frequency code, kept as text so its leading zeros survive
 * @property mode the mode character, from NMEA's own table
 */
public data class ScannedFrequency(val frequency: String?, val mode: Char?)
