package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.AcquisitionType
import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.DisplayRotation
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.NmeaFieldException
import io.github.solcott.marineapi.nmea.NmeaFormat
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.ReferenceSystem
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.Side
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.TargetStatus
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.positionAt
import io.github.solcott.marineapi.nmea.positionFields
import kotlinx.datetime.LocalTime

/**
 * Rate of turn.
 *
 * Example: `$HEROT,0.0,A*2B`
 *
 * @property rateOfTurn degrees per minute; negative means the bow is turning to port
 * @property status whether the reading is valid
 */
public data class Rot(
  override val talker: TalkerId,
  val rateOfTurn: Double? = null,
  val status: DataStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(rateOfTurn.field(), status.field()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ROT"

    private const val RATE_OF_TURN = 0
    private const val STATUS = 1

    /** Reads a ROT sentence from its fields. */
    public fun from(fields: SentenceFields): Rot =
      Rot(
        talker = fields.talker,
        rateOfTurn = fields.doubleAt(RATE_OF_TURN),
        status = fields.codedAt(STATUS, DataStatus.entries),
      )
  }
}

/**
 * Rudder sensor angle, for one or two rudders.
 *
 * Example: `$IIRSA,1.2,A,2.3,V*4E`
 *
 * A single-rudder vessel reports only the starboard pair; the port pair is then empty. A negative
 * angle means the rudder is turning the vessel to port.
 *
 * @property starboardAngle starboard, or single, rudder angle in degrees
 * @property starboardStatus whether [starboardAngle] is valid
 * @property portAngle port rudder angle in degrees, empty on a single-rudder vessel
 * @property portStatus whether [portAngle] is valid
 */
public data class Rsa(
  override val talker: TalkerId,
  val starboardAngle: Double? = null,
  val starboardStatus: DataStatus? = null,
  val portAngle: Double? = null,
  val portStatus: DataStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** The angle of the rudder on [side], since a caller usually knows which one it wants. */
  public fun angleOf(side: Side): Double? =
    when (side) {
      Side.STARBOARD -> starboardAngle
      Side.PORT -> portAngle
    }

  /** Whether the sensor on [side] is reporting valid data. */
  public fun statusOf(side: Side): DataStatus? =
    when (side) {
      Side.STARBOARD -> starboardStatus
      Side.PORT -> portStatus
    }

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        starboardAngle.field(),
        starboardStatus.field(),
        portAngle.field(),
        portStatus.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RSA"

    private const val STARBOARD_ANGLE = 0
    private const val STARBOARD_STATUS = 1
    private const val PORT_ANGLE = 2
    private const val PORT_STATUS = 3

    /** Reads an RSA sentence from its fields. */
    public fun from(fields: SentenceFields): Rsa =
      Rsa(
        talker = fields.talker,
        starboardAngle = fields.doubleAt(STARBOARD_ANGLE),
        starboardStatus = fields.codedAt(STARBOARD_STATUS, DataStatus.entries),
        portAngle = fields.doubleAt(PORT_ANGLE),
        portStatus = fields.codedAt(PORT_STATUS, DataStatus.entries),
      )
  }
}

/**
 * Own ship data: what the radar believes about the vessel carrying it.
 *
 * Example: `$RAOSD,35.1,A,36.0,P,10.2,P,15.3,0.1,N*41`
 *
 * Course and speed each carry a [ReferenceSystem] saying where the radar got them from -- a
 * bottom-tracking log, the water, a positioning system, radar tracking of a fixed target, or a
 * human typing them in. A course from `MANUALLY_ENTERED` and one from
 * `POSITIONING_SYSTEM_GROUND_REFERENCE` are not equally trustworthy, which is why the field exists.
 *
 * @property heading heading in degrees true
 * @property headingStatus whether [heading] is valid
 * @property course course over ground in degrees true
 * @property courseReference where [course] came from
 * @property speed speed in [speedUnits]
 * @property speedReference where [speed] came from
 * @property vesselSet set of the current in degrees true -- the direction the water is flowing
 * @property vesselDrift drift of the current, its speed, in [speedUnits]
 * @property speedUnits `N` for knots, `K` for km/h, `S` for statute miles per hour
 */
public data class Osd(
  override val talker: TalkerId,
  val heading: Double? = null,
  val headingStatus: DataStatus? = null,
  val course: Double? = null,
  val courseReference: ReferenceSystem? = null,
  val speed: Double? = null,
  val speedReference: ReferenceSystem? = null,
  val vesselSet: Double? = null,
  val vesselDrift: Double? = null,
  val speedUnits: Units? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        heading.field(),
        headingStatus.field(),
        course.field(),
        courseReference.field(),
        speed.field(),
        speedReference.field(),
        vesselSet.field(),
        vesselDrift.field(),
        speedUnits.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "OSD"

    private const val HEADING = 0
    private const val HEADING_STATUS = 1
    private const val COURSE = 2
    private const val COURSE_REFERENCE = 3
    private const val SPEED = 4
    private const val SPEED_REFERENCE = 5
    private const val VESSEL_SET = 6
    private const val VESSEL_DRIFT = 7
    private const val SPEED_UNITS = 8

    /** Reads an OSD sentence from its fields. */
    public fun from(fields: SentenceFields): Osd =
      Osd(
        talker = fields.talker,
        heading = fields.doubleAt(HEADING),
        headingStatus = fields.codedAt(HEADING_STATUS, DataStatus.entries),
        course = fields.doubleAt(COURSE),
        courseReference = fields.codedAt(COURSE_REFERENCE, ReferenceSystem.entries),
        speed = fields.doubleAt(SPEED),
        speedReference = fields.codedAt(SPEED_REFERENCE, ReferenceSystem.entries),
        vesselSet = fields.doubleAt(VESSEL_SET),
        vesselDrift = fields.doubleAt(VESSEL_DRIFT),
        speedUnits = fields.codedAt(SPEED_UNITS, RADAR_DISTANCE_UNITS),
      )
  }
}

/**
 * Radar system data: where the operator has put the cursor, the range rings and the bearing lines.
 *
 * Example: `$RARSD,12,90,24,45,6,270,12,315,6.5,118,96,N,N*5A`
 *
 * This is display state rather than sensor data -- two origins, each with a variable range marker
 * and an electronic bearing line, plus the cursor and the range scale in use.
 *
 * gpsd's table for this sentence carries the note "some fields are missing from this description",
 * so treat the meanings below as the best available reading rather than a settled one.
 *
 * @property originOneRange range of the first origin
 * @property originOneBearing bearing of the first origin, degrees clockwise from zero
 * @property variableRangeMarkerOne first variable range marker
 * @property bearingLineOne first electronic bearing line
 * @property originTwoRange range of the second origin
 * @property originTwoBearing bearing of the second origin
 * @property variableRangeMarkerTwo second variable range marker
 * @property bearingLineTwo second electronic bearing line
 * @property cursorRange cursor range from own ship
 * @property cursorBearing cursor bearing, degrees clockwise from zero
 * @property rangeScale range scale currently displayed
 * @property rangeUnits unit of the ranges above
 * @property displayRotation how the display is oriented
 */
public data class Rsd(
  override val talker: TalkerId,
  val originOneRange: Double? = null,
  val originOneBearing: Double? = null,
  val variableRangeMarkerOne: Double? = null,
  val bearingLineOne: Double? = null,
  val originTwoRange: Double? = null,
  val originTwoBearing: Double? = null,
  val variableRangeMarkerTwo: Double? = null,
  val bearingLineTwo: Double? = null,
  val cursorRange: Double? = null,
  val cursorBearing: Double? = null,
  val rangeScale: Double? = null,
  val rangeUnits: Units? = null,
  val displayRotation: DisplayRotation? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        originOneRange.field(),
        originOneBearing.field(),
        variableRangeMarkerOne.field(),
        bearingLineOne.field(),
        originTwoRange.field(),
        originTwoBearing.field(),
        variableRangeMarkerTwo.field(),
        bearingLineTwo.field(),
        cursorRange.field(),
        cursorBearing.field(),
        rangeScale.field(),
        rangeUnits.field(),
        displayRotation.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RSD"

    private const val ORIGIN_ONE_RANGE = 0
    private const val ORIGIN_ONE_BEARING = 1
    private const val VRM_ONE = 2
    private const val EBL_ONE = 3
    private const val ORIGIN_TWO_RANGE = 4
    private const val ORIGIN_TWO_BEARING = 5
    private const val VRM_TWO = 6
    private const val EBL_TWO = 7
    private const val CURSOR_RANGE = 8
    private const val CURSOR_BEARING = 9
    private const val RANGE_SCALE = 10
    private const val RANGE_UNITS = 11
    private const val DISPLAY_ROTATION = 12

    /** Reads an RSD sentence from its fields. */
    public fun from(fields: SentenceFields): Rsd =
      Rsd(
        talker = fields.talker,
        originOneRange = fields.doubleAt(ORIGIN_ONE_RANGE),
        originOneBearing = fields.doubleAt(ORIGIN_ONE_BEARING),
        variableRangeMarkerOne = fields.doubleAt(VRM_ONE),
        bearingLineOne = fields.doubleAt(EBL_ONE),
        originTwoRange = fields.doubleAt(ORIGIN_TWO_RANGE),
        originTwoBearing = fields.doubleAt(ORIGIN_TWO_BEARING),
        variableRangeMarkerTwo = fields.doubleAt(VRM_TWO),
        bearingLineTwo = fields.doubleAt(EBL_TWO),
        cursorRange = fields.doubleAt(CURSOR_RANGE),
        cursorBearing = fields.doubleAt(CURSOR_BEARING),
        rangeScale = fields.doubleAt(RANGE_SCALE),
        rangeUnits = fields.codedAt(RANGE_UNITS, RADAR_DISTANCE_UNITS),
        displayRotation = fields.codedAt(DISPLAY_ROTATION, DisplayRotation.entries),
      )
  }
}

/**
 * One tracked radar target: where it is, where it is going, and how close it will come.
 *
 * Example: `$RATTM,11,25.3,13.7,T,7.0,20.0,T,10.1,20.2,N,NAME,Q,,175550.24,A*34`
 *
 * [closestPointOfApproachDistance] and [timeToClosestPointOfApproach] are the collision-avoidance
 * numbers a watchkeeper acts on. A negative time means the target is already opening, not closing.
 *
 * [bearing] and [course] each carry their own true-or-relative flag, and they need not agree.
 *
 * @property number target number, 0 to 99, which [Tlb] can attach a label to
 * @property distance distance to the target in [units]
 * @property bearing bearing to the target from own ship
 * @property bearingReference whether [bearing] is true or relative to own heading
 * @property speed target speed in [units] per hour
 * @property course target course
 * @property courseReference whether [course] is true or relative
 * @property closestPointOfApproachDistance distance at the closest point of approach, in [units]
 * @property timeToClosestPointOfApproach minutes until that point; negative means increasing
 * @property units unit for the distances and speed, `N`, `K` or `S`
 * @property name target name
 * @property status whether the target is being tracked, is still being acquired, or is lost
 * @property isReferenceTarget whether this target is the reference used to compute own speed
 * @property time UTC of this report, added in NMEA 3
 * @property acquisitionType how the target was acquired, added in NMEA 3
 */
public data class Ttm(
  override val talker: TalkerId,
  val number: Int? = null,
  val distance: Double? = null,
  val bearing: Double? = null,
  val bearingReference: AngleReference? = null,
  val speed: Double? = null,
  val course: Double? = null,
  val courseReference: AngleReference? = null,
  val closestPointOfApproachDistance: Double? = null,
  val timeToClosestPointOfApproach: Double? = null,
  val units: Units? = null,
  val name: String? = null,
  val status: TargetStatus? = null,
  val isReferenceTarget: Boolean = false,
  val time: LocalTime? = null,
  val acquisitionType: AcquisitionType? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        number?.let { NmeaFormat.integer(it, 2) },
        distance.field(),
        bearing.field(),
        bearingReference.field(),
        speed.field(),
        course.field(),
        courseReference.field(),
        closestPointOfApproachDistance.field(),
        timeToClosestPointOfApproach.field(),
        units.field(),
        name,
        status.field(),
        referenceTargetField(isReferenceTarget),
        time?.let { NmeaDateTime.formatTime(it) },
        acquisitionType.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TTM"

    private const val NUMBER = 0
    private const val DISTANCE = 1
    private const val BEARING = 2
    private const val BEARING_REFERENCE = 3
    private const val SPEED = 4
    private const val COURSE = 5
    private const val COURSE_REFERENCE = 6
    private const val CPA_DISTANCE = 7
    private const val CPA_TIME = 8
    private const val UNITS = 9
    private const val NAME = 10
    private const val STATUS = 11
    private const val REFERENCE_TARGET = 12
    private const val TIME = 13
    private const val ACQUISITION_TYPE = 14

    /** Reads a TTM sentence from its fields. */
    public fun from(fields: SentenceFields): Ttm =
      Ttm(
        talker = fields.talker,
        number = fields.intAt(NUMBER),
        distance = fields.doubleAt(DISTANCE),
        bearing = fields.doubleAt(BEARING),
        bearingReference = fields.codedAt(BEARING_REFERENCE, AngleReference.entries),
        speed = fields.doubleAt(SPEED),
        course = fields.doubleAt(COURSE),
        courseReference = fields.codedAt(COURSE_REFERENCE, AngleReference.entries),
        closestPointOfApproachDistance = fields.doubleAt(CPA_DISTANCE),
        timeToClosestPointOfApproach = fields.doubleAt(CPA_TIME),
        units = fields.codedAt(UNITS, RADAR_DISTANCE_UNITS),
        name = fields.stringAt(NAME),
        status = fields.codedAt(STATUS, TargetStatus.entries),
        isReferenceTarget = fields.readReferenceTarget(REFERENCE_TARGET),
        time = fields.timeAt(TIME),
        acquisitionType = fields.codedAt(ACQUISITION_TYPE, AcquisitionType.entries),
      )
  }
}

/**
 * A tracked target's position, reported as latitude and longitude rather than range and bearing.
 *
 * Example: `$RATLL,01,3731.51205,N,02436.00000,E,ANDROS,163700.86,T,*25`
 *
 * [number] matches the target number in the [Ttm] describing the same target, and the label in a
 * [Tlb].
 *
 * @property number target number, 0 to 99
 * @property position where the target is
 * @property name target name
 * @property time UTC of this report
 * @property status whether the target is being tracked, is still being acquired, or is lost
 * @property isReferenceTarget whether this target is the reference used to compute own speed
 */
public data class Tll(
  override val talker: TalkerId,
  val number: Int? = null,
  val position: Position? = null,
  val name: String? = null,
  val time: LocalTime? = null,
  val status: TargetStatus? = null,
  val isReferenceTarget: Boolean = false,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(number?.let { NmeaFormat.integer(it, 2) }) +
        positionFields(position) +
        listOf(
          name,
          time?.let { NmeaDateTime.formatTime(it) },
          status.field(),
          referenceTargetField(isReferenceTarget),
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TLL"

    private const val NUMBER = 0
    private const val LATITUDE = 1
    private const val LATITUDE_HEMISPHERE = 2
    private const val LONGITUDE = 3
    private const val LONGITUDE_HEMISPHERE = 4
    private const val NAME = 5
    private const val TIME = 6
    private const val STATUS = 7
    private const val REFERENCE_TARGET = 8

    /** Reads a TLL sentence from its fields. */
    public fun from(fields: SentenceFields): Tll =
      Tll(
        talker = fields.talker,
        number = fields.intAt(NUMBER),
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        name = fields.stringAt(NAME),
        time = fields.timeAt(TIME),
        status = fields.codedAt(STATUS, TargetStatus.entries),
        isReferenceTarget = fields.readReferenceTarget(REFERENCE_TARGET),
      )
  }
}

/** One target number and the label an operator has given it, as carried by [Tlb]. */
public data class TargetLabel(
  /** Target number, matching the one in a [Ttm] or [Tll]. */
  val number: Int?,
  /** The label, or `null` when the sentence names the target but gives it no label. */
  val label: String?,
)

/**
 * Labels for tracked targets.
 *
 * Example: `$RATLB,1,SHIPONE,2,SHIPTWO,3,SHIPTHREE*3D`
 *
 * The sentence is a run of target-number and label pairs, as many as fit in a sentence, tying
 * operator-assigned names to the numbers used by [Ttm] and [Tll].
 *
 * @property labels the pairs, in the order they appear
 */
public data class Tlb(override val talker: TalkerId, val labels: List<TargetLabel> = emptyList()) :
  Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, labels.flatMap { listOf(it.number?.toString(), it.label) })

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TLB"

    /**
     * Reads a TLB sentence from its fields.
     *
     * @throws NmeaFieldException if the fields do not divide into number-and-label pairs, which
     *   means the sentence was truncated or a field was lost.
     */
    public fun from(fields: SentenceFields): Tlb {
      val values = fields.stringsFrom(0)
      if (values.size % 2 != 0) {
        throw NmeaFieldException(
          "${fields.talker}$ID carries ${values.size} fields; target numbers and labels come in " +
            "pairs, so an odd count means one is missing"
        )
      }
      return Tlb(
        talker = fields.talker,
        labels =
          values.chunked(2).map { (number, label) ->
            TargetLabel(number = number?.toIntOrNull(), label = label)
          },
      )
    }
  }
}

/**
 * Units a radar reports distances and speeds in.
 *
 * The format documents `K` and `N` for these fields; `S` is accepted too because the implementation
 * this replaces accepted it, and rejecting a whole sentence over a unit it names correctly would
 * lose the target data with it.
 */
private val RADAR_DISTANCE_UNITS =
  listOf(Units.KILOMETERS, Units.NAUTICAL_MILES, Units.STATUTE_MILES)

private const val REFERENCE_TARGET_MARKER = 'R'

/**
 * Reads the reference-target flag, which is `R` or nothing at all.
 *
 * Unlike the `A`/`V` status fields this has only one meaningful value, so it becomes a boolean
 * rather than a nullable enum: an empty field means "not the reference target", not "unknown".
 */
private fun SentenceFields.readReferenceTarget(index: Int): Boolean {
  val value = stringAt(index) ?: return false
  if (value != REFERENCE_TARGET_MARKER.toString()) {
    throw NmeaFieldException(
      "$talker$id field $index is not '$REFERENCE_TARGET_MARKER' or empty: \"$value\""
    )
  }
  return true
}

private fun referenceTargetField(isReferenceTarget: Boolean): String? =
  if (isReferenceTarget) REFERENCE_TARGET_MARKER.toString() else null
