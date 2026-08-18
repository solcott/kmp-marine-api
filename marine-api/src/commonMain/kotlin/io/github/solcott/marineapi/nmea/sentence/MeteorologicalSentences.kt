package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Measurement
import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/**
 * Air temperature.
 *
 * Example: `$IIMTA,21.5,C*1A`
 *
 * The air counterpart of [Mtw], which reports water temperature. The two are a letter apart and
 * report different things, so they are separate types.
 *
 * @property temperature air temperature in degrees Celsius
 */
public data class Mta(override val talker: TalkerId, val temperature: Double? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(temperature.field(), CELSIUS_MARKER.toString()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MTA"

    private const val CELSIUS_MARKER = 'C'
    private const val TEMPERATURE = 0

    /** Reads an MTA sentence from its fields. */
    public fun from(fields: SentenceFields): Mta =
      Mta(talker = fields.talker, temperature = fields.doubleAt(TEMPERATURE))
  }
}

/**
 * Barometric pressure, in inches of mercury and in bars.
 *
 * Example: `$IIMMB,29.9870,I,1.0154,B*75`
 *
 * Both values describe the same pressure; a device that reports one usually reports the other.
 *
 * @property inchesOfMercury pressure in inches of mercury
 * @property bars pressure in bars
 */
public data class Mmb(
  override val talker: TalkerId,
  val inchesOfMercury: Double? = null,
  val bars: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        inchesOfMercury.field(),
        INCHES_MARKER.toString(),
        bars.field(),
        BARS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MMB"

    private const val INCHES_MARKER = 'I'
    private const val BARS_MARKER = 'B'

    private const val INCHES_OF_MERCURY = 0
    private const val BARS = 2

    /** Reads an MMB sentence from its fields. */
    public fun from(fields: SentenceFields): Mmb =
      Mmb(
        talker = fields.talker,
        inchesOfMercury = fields.doubleAt(INCHES_OF_MERCURY),
        bars = fields.doubleAt(BARS),
      )
  }
}

/**
 * Humidity, absolute and relative, with the dew point.
 *
 * Example: `$IIMHU,66.0,5.0,3.0,C*3D`
 *
 * @property relativeHumidity relative humidity, percent
 * @property absoluteHumidity absolute humidity, grams per cubic metre
 * @property dewPoint dew point in degrees Celsius
 */
public data class Mhu(
  override val talker: TalkerId,
  val relativeHumidity: Double? = null,
  val absoluteHumidity: Double? = null,
  val dewPoint: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        relativeHumidity.field(),
        absoluteHumidity.field(),
        dewPoint.field(),
        CELSIUS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MHU"

    private const val CELSIUS_MARKER = 'C'

    private const val RELATIVE_HUMIDITY = 0
    private const val ABSOLUTE_HUMIDITY = 1
    private const val DEW_POINT = 2

    /** Reads an MHU sentence from its fields. */
    public fun from(fields: SentenceFields): Mhu =
      Mhu(
        talker = fields.talker,
        relativeHumidity = fields.doubleAt(RELATIVE_HUMIDITY),
        absoluteHumidity = fields.doubleAt(ABSOLUTE_HUMIDITY),
        dewPoint = fields.doubleAt(DEW_POINT),
      )
  }
}

/**
 * Meteorological composite: pressure, temperature, humidity and wind in one sentence.
 *
 * Example: `$IIMDA,99700.0,P,1.00,B,3.2,C,,C,,,,C,295.19,T,,M,5.70,N,2.93,M*08`
 *
 * Twenty fields, most of them a value followed by its unit. gpsd records this sentence as obsolete
 * as of 2009, and a weather station will usually emit MMB, MTA, MHU and MWD in its place -- but
 * plenty of installed hardware still sends it.
 *
 * Only [primaryPressureUnits] is read; the rest of the unit fields have one value each and are
 * written back as constants. The example above shows why that one is not: it reports pascals, where
 * the format's own table says inches of mercury.
 *
 * @property primaryPressure barometric pressure in [primaryPressureUnits]
 * @property primaryPressureUnits `I` for inches of mercury or `P` for pascals
 * @property secondaryPressureBars barometric pressure in bars
 * @property airTemperature air temperature in degrees Celsius
 * @property waterTemperature water temperature in degrees Celsius
 * @property relativeHumidity relative humidity, percent
 * @property absoluteHumidity absolute humidity, percent
 * @property dewPoint dew point in degrees Celsius
 * @property windDirectionTrue wind direction in degrees true
 * @property windDirectionMagnetic wind direction in degrees magnetic
 * @property windSpeedKnots wind speed in knots
 * @property windSpeedMetersPerSecond wind speed in metres per second
 */
public data class Mda(
  override val talker: TalkerId,
  val primaryPressure: Double? = null,
  val primaryPressureUnits: Units? = null,
  val secondaryPressureBars: Double? = null,
  val airTemperature: Double? = null,
  val waterTemperature: Double? = null,
  val relativeHumidity: Double? = null,
  val absoluteHumidity: Double? = null,
  val dewPoint: Double? = null,
  val windDirectionTrue: Double? = null,
  val windDirectionMagnetic: Double? = null,
  val windSpeedKnots: Double? = null,
  val windSpeedMetersPerSecond: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        primaryPressure.field(),
        primaryPressureUnits.field(),
        secondaryPressureBars.field(),
        BARS_MARKER.toString(),
        airTemperature.field(),
        CELSIUS_MARKER.toString(),
        waterTemperature.field(),
        CELSIUS_MARKER.toString(),
        relativeHumidity.field(),
        absoluteHumidity.field(),
        dewPoint.field(),
        CELSIUS_MARKER.toString(),
        windDirectionTrue.field(),
        TRUE_MARKER.toString(),
        windDirectionMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        windSpeedKnots.field(),
        KNOTS_MARKER.toString(),
        windSpeedMetersPerSecond.field(),
        METERS_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "MDA"

    private const val BARS_MARKER = 'B'
    private const val CELSIUS_MARKER = 'C'
    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'

    /**
     * Knots, per the format's own table.
     *
     * The implementation this replaces wrote `K` here, which is kilometres -- so a sentence it
     * built claimed the wrong unit for the wind speed beside it.
     */
    private const val KNOTS_MARKER = 'N'

    private const val METERS_MARKER = 'M'

    private const val PRIMARY_PRESSURE = 0
    private const val PRIMARY_PRESSURE_UNITS = 1
    private const val SECONDARY_PRESSURE = 2
    private const val AIR_TEMPERATURE = 4
    private const val WATER_TEMPERATURE = 6
    private const val RELATIVE_HUMIDITY = 8
    private const val ABSOLUTE_HUMIDITY = 9
    private const val DEW_POINT = 10
    private const val WIND_DIRECTION_TRUE = 12
    private const val WIND_DIRECTION_MAGNETIC = 14
    private const val WIND_SPEED_KNOTS = 16
    private const val WIND_SPEED_METERS = 18

    private val PRESSURE_UNITS = listOf(Units.INCHES, Units.PASCAL)

    /** Reads an MDA sentence from its fields. */
    public fun from(fields: SentenceFields): Mda =
      Mda(
        talker = fields.talker,
        primaryPressure = fields.doubleAt(PRIMARY_PRESSURE),
        primaryPressureUnits = fields.codedAt(PRIMARY_PRESSURE_UNITS, PRESSURE_UNITS),
        secondaryPressureBars = fields.doubleAt(SECONDARY_PRESSURE),
        airTemperature = fields.doubleAt(AIR_TEMPERATURE),
        waterTemperature = fields.doubleAt(WATER_TEMPERATURE),
        relativeHumidity = fields.doubleAt(RELATIVE_HUMIDITY),
        absoluteHumidity = fields.doubleAt(ABSOLUTE_HUMIDITY),
        dewPoint = fields.doubleAt(DEW_POINT),
        windDirectionTrue = fields.doubleAt(WIND_DIRECTION_TRUE),
        windDirectionMagnetic = fields.doubleAt(WIND_DIRECTION_MAGNETIC),
        windSpeedKnots = fields.doubleAt(WIND_SPEED_KNOTS),
        windSpeedMetersPerSecond = fields.doubleAt(WIND_SPEED_METERS),
      )
  }
}

/**
 * Transducer measurements: any number of sensors, each described by four fields.
 *
 * Example: `$HCXDR,A,171,D,PITCH,A,-37,D,ROLL,G,367,,MAGX,G,2420,,MAGY,G,-8984,,MAGZ*41`
 *
 * Each quadruple is a transducer type, a value, a unit and a name. This is the format's escape
 * hatch: a device with a sensor no sentence covers reports it here.
 *
 * [Measurement.units] stays raw text rather than becoming a [Units], because the letters are
 * genuinely ambiguous within this one sentence -- `B` is bars or binary, `K` is kelvin or kg/m3,
 * `M` is metres or cubic metres, `P` is pascals or percent of full range. Only the transducer type
 * beside it says which, and a wrong guess would be worse than no guess. The example above also
 * shows the unit left empty entirely, for the magnetometer readings.
 *
 * Every quadruple is kept, including one that is entirely empty, so the sentence re-encodes as it
 * arrived. Use [Measurement.isEmpty] to skip those.
 *
 * @property measurements one entry per quadruple, in the order they appear
 */
public data class Xdr(
  override val talker: TalkerId,
  val measurements: List<Measurement> = emptyList(),
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      measurements.flatMap { listOf(it.type, it.value.field(), it.units, it.name) },
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "XDR"

    private const val FIELDS_PER_MEASUREMENT = 4

    /**
     * Reads an XDR sentence from its fields.
     *
     * @throws NmeaFieldException if the field count is not a multiple of four, which means a
     *   quadruple was truncated.
     */
    public fun from(fields: SentenceFields): Xdr {
      val values = fields.stringsFrom(0)
      if (values.size % FIELDS_PER_MEASUREMENT != 0) {
        throw NmeaFieldException(
          "${fields.talker}$ID carries ${values.size} fields; each transducer takes " +
            "$FIELDS_PER_MEASUREMENT, so a count that is not a multiple of that is truncated"
        )
      }
      // Indices rather than chunking, so a value that is not a number is reported against the
      // field it came from -- the same way every other sentence reports one.
      return Xdr(
        talker = fields.talker,
        measurements =
          values.indices.step(FIELDS_PER_MEASUREMENT).map { start ->
            Measurement(
              type = fields.stringAt(start),
              value = fields.doubleAt(start + 1),
              units = fields.stringAt(start + 2),
              name = fields.stringAt(start + 3),
            )
          },
      )
    }
  }
}
