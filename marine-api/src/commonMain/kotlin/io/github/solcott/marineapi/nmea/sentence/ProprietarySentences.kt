package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Datum
import io.github.solcott.marineapi.nmea.GpsFixStatus
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import kotlinx.datetime.LocalTime

/**
 * u-blox proprietary sentence, the envelope for a u-blox message.
 *
 * Example:
 * `$PUBX,00,125926.00,4717.11337,N,00833.91163,E,111.500,GLL,20,15,0.007,0.00,,,,3.00,3.00,3.00,,,,,,,*7C`
 *
 * Proprietary sentences are `$P` followed by a manufacturer mnemonic instead of a talker and a type
 * code, so the whole tag here is `PUBX` and [talker] is [TalkerId.P]. What the fields mean depends
 * entirely on [messageId]: `00` is a position report, `03` a satellite status report, and u-blox
 * defines others this library does not decode. Decode the message with
 * [io.github.solcott.marineapi.ublox.UbloxRegistry].
 *
 * The fields are kept as raw text, which is the honest representation for a sentence whose layout
 * is not known until its first field has been read. That is also all the Java implementation
 * offered for it -- a bag of `getUBXFieldIntValue`-style accessors -- with the difference that
 * these are a value rather than a live view onto a mutable parser.
 *
 * @property messageId u-blox message type, e.g. 0 for a position report
 * @property fields every field of the sentence, [messageId] included at index 0, empty fields as
 *   `null`
 */
public data class Ubx(
  override val talker: TalkerId,
  val messageId: Int,
  val fields: List<String?> = emptyList(),
) : Sentence {

  override val id: String
    get() = ID

  /** Field at [index], or `null` if it is empty or past the end of the sentence. */
  public fun stringAt(index: Int): String? = fields.getOrNull(index)

  /** Field at [index] as an [Int], or `null` if it is empty, absent or not a number. */
  public fun intAt(index: Int): Int? = stringAt(index)?.toIntOrNull()

  /** Field at [index] as a [Double], or `null` if it is empty, absent or not a number. */
  public fun doubleAt(index: Int): Double? = stringAt(index)?.toDoubleOrNull()

  override fun toNmeaString(): String = buildNmea(talker, ID, fields)

  public companion object {
    /** Sentence type code. Proprietary, so it stands in for the whole `PUBX` tag. */
    public const val ID: String = "UBX"

    private const val MESSAGE_ID = 0

    /** Reads a UBX sentence from its fields. */
    public fun from(fields: SentenceFields): Ubx =
      Ubx(
        talker = fields.talker,
        messageId =
          fields.intAt(MESSAGE_ID)
            ?: throw NmeaFieldException(
              "UBX sentence has no message id, so its fields have no meaning"
            ),
        fields = List(fields.size) { fields.stringAt(it) },
      )
  }
}

/**
 * SeaTalk sentence, carrying a Raymarine SeaTalk datagram over NMEA.
 *
 * Example: `$STALK,52,A1,00,00*36`
 *
 * SeaTalk is Raymarine's own instrument bus. A SeaTalk-to-NMEA bridge wraps each datagram in this
 * sentence rather than translating it: [command] is the SeaTalk datagram type and [parameters] its
 * bytes, all as two-digit hex. Interpreting them needs a SeaTalk datagram reference, which this
 * library does not implement -- it delivers the datagram intact and stops there.
 *
 * The tag is `$STALK`: talker `ST`, type `ALK`. That is the only talker this sentence occurs with,
 * and a different one means the tag was misread.
 *
 * @property command SeaTalk datagram type, two hex digits
 * @property parameters the datagram's remaining bytes, each two hex digits
 */
public data class Stalk(
  override val talker: TalkerId = TalkerId.ST,
  val command: String,
  val parameters: List<String> = emptyList(),
) : Sentence {

  init {
    require(talker == TalkerId.ST) { "A SeaTalk sentence is always \$STALK, got \$$talker$ID" }
    require(command.isNotEmpty()) { "A SeaTalk sentence carries a command" }
  }

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, listOf(command) + parameters)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ALK"

    private const val COMMAND = 0

    /** Reads a SeaTalk sentence from its fields. */
    public fun from(fields: SentenceFields): Stalk =
      Stalk(
        talker = fields.talker,
        command =
          fields.stringAt(COMMAND) ?: throw NmeaFieldException("SeaTalk sentence has no command"),
        parameters = List(maxOf(fields.size - 1, 0)) { fields.stringAt(it + 1).orEmpty() },
      )
  }
}

/**
 * Garmin's estimate of how wrong its own position is, in metres.
 *
 * Example: `$PGRME,15.0,M,45.0,M,25.0,M*22`
 *
 * The number a chart plotter draws its accuracy circle from, and the reason to read a proprietary
 * sentence at all: the standard set reports dilution of precision, which is a geometry factor
 * describing how the satellites are arranged, not a distance. This is a distance.
 *
 * @property horizontalError estimated horizontal position error, metres
 * @property verticalError estimated vertical position error, metres
 * @property sphericalError overall spherical equivalent position error, metres
 */
public data class Pgrme(
  override val talker: TalkerId,
  val horizontalError: Double? = null,
  val verticalError: Double? = null,
  val sphericalError: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        horizontalError.field(),
        METRES_MARKER.toString(),
        verticalError.field(),
        METRES_MARKER.toString(),
        sphericalError.field(),
        METRES_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code, the `GRME` of `$PGRME`. */
    public const val ID: String = "GRME"

    private const val METRES_MARKER = 'M'
    private const val HORIZONTAL = 0
    private const val VERTICAL = 2
    private const val SPHERICAL = 4

    /** Reads a `$PGRME` sentence from its fields. */
    public fun from(fields: SentenceFields): Pgrme =
      Pgrme(
        talker = fields.talker,
        horizontalError = fields.doubleAt(HORIZONTAL),
        verticalError = fields.doubleAt(VERTICAL),
        sphericalError = fields.doubleAt(SPHERICAL),
      )
  }
}

/**
 * Garmin's altitude, in feet, with the kind of fix it came from.
 *
 * Example: `$PGRMZ,246,f,3*1B`
 *
 * **Feet, not metres** -- the one unit surprise in this group, and the reason [altitudeFeet] is
 * named as it is rather than left to be assumed. [Gga] reports altitude in metres beside a unit
 * marker; this has its own marker but only ever `f`.
 *
 * @property altitudeFeet altitude in feet
 * @property fixStatus whether the altitude came from a 2D fix, a 3D fix, or no fix at all. The
 *   distinction matters here more than most: a 2D fix has no vertical solution, so its altitude is
 *   an assumption rather than a measurement.
 */
public data class Pgrmz(
  override val talker: TalkerId,
  val altitudeFeet: Double? = null,
  val fixStatus: GpsFixStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(altitudeFeet.field(), FEET_MARKER.toString(), fixStatus.field()))

  public companion object {
    /** Sentence type code, the `GRMZ` of `$PGRMZ`. */
    public const val ID: String = "GRMZ"

    private const val FEET_MARKER = 'f'
    private const val ALTITUDE = 0
    private const val FIX_STATUS = 2

    /** Reads a `$PGRMZ` sentence from its fields. */
    public fun from(fields: SentenceFields): Pgrmz =
      Pgrmz(
        talker = fields.talker,
        altitudeFeet = fields.doubleAt(ALTITUDE),
        // 1, 2 and 3 for no fix, 2D and 3D -- exactly GpsFixStatus's existing codes, which is why
        // there is no Garmin-specific twin of that enum.
        fixStatus = fields.advisoryIntCodedAt(FIX_STATUS, GpsFixStatus.entries),
      )
  }
}

/**
 * The map datum a Garmin receiver is reporting positions in.
 *
 * Example: `$PGRMM,WGS 84*06`
 *
 * Worth having because a position means nothing without it: the same coordinates on NAD83 and on a
 * local datum can be hundreds of metres apart. [Dtm] is the standard sentence for this; a Garmin
 * sends both.
 *
 * **gpsd does not document this sentence.** Its single field is nonetheless unambiguous in the
 * conformance corpus, which carries only `$PGRMM,NAD83` and `$PGRMM,WGS 84` -- a datum name and
 * nothing else. It is kept as the text the receiver sent rather than matched against [Datum], whose
 * entries are a fixed set this does not necessarily fall inside.
 *
 * @property datumName the datum, as the receiver spells it
 */
public data class Pgrmm(override val talker: TalkerId, val datumName: String? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, listOf(datumName))

  public companion object {
    /** Sentence type code, the `GRMM` of `$PGRMM`. */
    public const val ID: String = "GRMM"

    private const val DATUM_NAME = 0

    /** Reads a `$PGRMM` sentence from its fields. */
    public fun from(fields: SentenceFields): Pgrmm =
      Pgrmm(talker = fields.talker, datumName = fields.stringAt(DATUM_NAME))
  }
}

/**
 * Attitude from an inertial sensor: roll and pitch as well as heading.
 *
 * Example: `$PASHR,123816.80,312.95,T,-0.83,-0.42,-0.01,0.234,0.224,0.298,1,0*09`
 *
 * The only sentence here that reports **roll and pitch**. No standard NMEA sentence does -- [Hdt]
 * and [Ths] give heading alone -- so an application needing a vessel's attitude has this or
 * nothing. Emitted by Ashtech and RT300 inertial units, and by others that have adopted it.
 *
 * @property time UTC of the measurement
 * @property heading degrees true
 * @property roll degrees, positive to starboard
 * @property pitch degrees, positive bow up
 * @property heave metres, the vertical displacement of the vessel in the swell
 * @property rollAccuracy standard deviation of [roll], degrees
 * @property pitchAccuracy standard deviation of [pitch], degrees
 * @property headingAccuracy standard deviation of [heading], degrees
 * @property aidingStatus whether the inertial solution is being corrected by GNSS
 * @property imuStatus the inertial unit's own health
 */
public data class Pashr(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val heading: Double? = null,
  val roll: Double? = null,
  val pitch: Double? = null,
  val heave: Double? = null,
  val rollAccuracy: Double? = null,
  val pitchAccuracy: Double? = null,
  val headingAccuracy: Double? = null,
  val aidingStatus: Int? = null,
  val imuStatus: Int? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        time?.let { NmeaDateTime.formatTime(it) },
        heading.field(),
        TRUE_MARKER.toString(),
        roll.field(),
        pitch.field(),
        heave.field(),
        rollAccuracy.field(),
        pitchAccuracy.field(),
        headingAccuracy.field(),
        aidingStatus?.toString(),
        imuStatus?.toString(),
      ),
    )

  public companion object {
    /** Sentence type code, the `ASHR` of `$PASHR`. */
    public const val ID: String = "ASHR"

    private const val TRUE_MARKER = 'T'
    private const val TIME = 0
    private const val HEADING = 1
    private const val ROLL = 3
    private const val PITCH = 4
    private const val HEAVE = 5
    private const val ROLL_ACCURACY = 6
    private const val PITCH_ACCURACY = 7
    private const val HEADING_ACCURACY = 8
    private const val AIDING_STATUS = 9
    private const val IMU_STATUS = 10

    /** Reads a `$PASHR` sentence from its fields. */
    public fun from(fields: SentenceFields): Pashr =
      Pashr(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        heading = fields.doubleAt(HEADING),
        roll = fields.doubleAt(ROLL),
        pitch = fields.doubleAt(PITCH),
        heave = fields.doubleAt(HEAVE),
        rollAccuracy = fields.doubleAt(ROLL_ACCURACY),
        pitchAccuracy = fields.doubleAt(PITCH_ACCURACY),
        headingAccuracy = fields.doubleAt(HEADING_ACCURACY),
        aidingStatus = fields.advisoryIntAt(AIDING_STATUS),
        imuStatus = fields.advisoryIntAt(IMU_STATUS),
      )
  }
}
