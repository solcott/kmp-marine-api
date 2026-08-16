package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.BearingReference
import io.github.solcott.marineapi.nmea.CompassPoint
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Units
import io.github.solcott.marineapi.nmea.Waypoint
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.positionAt
import io.github.solcott.marineapi.nmea.positionFields
import kotlin.time.Duration
import kotlinx.datetime.LocalTime

/**
 * Measured cross-track error: how far off the intended track the vessel is, and which way to steer
 * back.
 *
 * Example: `$IIXTE,A,A,5.36,R,N*67`
 *
 * @property status `V` for a Loran-C blink or SNR warning, or a general "no reliable fix" flag
 * @property cycleLockStatus `V` for a Loran-C cycle lock warning; `A` when OK or unused
 * @property magnitude cross-track distance in [units], always positive -- the side is [steerTo]
 * @property steerTo which way to steer to regain the track
 * @property units distance unit, `N` for nautical miles
 * @property faaMode FAA mode indicator, added in NMEA 2.3; `null` from an older device
 */
public data class Xte(
  override val talker: TalkerId,
  val status: DataStatus? = null,
  val cycleLockStatus: DataStatus? = null,
  val magnitude: Double? = null,
  val steerTo: Direction? = null,
  val units: Units? = null,
  val faaMode: FaaMode? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        status.field(),
        cycleLockStatus.field(),
        magnitude.field(),
        steerTo.field(),
        units.field(),
        faaMode.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "XTE"

    private const val STATUS = 0
    private const val CYCLE_LOCK_STATUS = 1
    private const val MAGNITUDE = 2
    private const val STEER_TO = 3
    private const val UNITS = 4
    private const val FAA_MODE = 5

    /** Reads an XTE sentence from its fields. */
    public fun from(fields: SentenceFields): Xte =
      Xte(
        talker = fields.talker,
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        cycleLockStatus = fields.advisoryCodedAt(CYCLE_LOCK_STATUS, DataStatus.entries),
        magnitude = fields.doubleAt(MAGNITUDE),
        steerTo = fields.codedAt(STEER_TO, Direction.entries),
        units = fields.codedAt(UNITS, Units.entries),
        faaMode = fields.advisoryCodedAt(FAA_MODE, FaaMode.entries),
      )
  }
}

/**
 * Autopilot sentence "B": everything an autopilot needs to steer the active leg.
 *
 * Example: `$GPAPB,A,A,0.10,R,N,V,V,011,M,DEST,011,M,011,M*82`
 *
 * The three bearings each carry their own `T`/`M` reference field, and they need not agree, so each
 * is read rather than assumed.
 *
 * Note that some autopilots -- Robertson's in particular -- read [bearingOriginToDestination] as
 * though it were [bearingPositionToDestination], which steers badly when the vessel is far enough
 * off track for the two to differ. That is a property of those devices, not of this sentence.
 *
 * @property status `V` for a Loran-C blink or SNR warning, or a general "no reliable fix" flag
 * @property cycleLockStatus `V` for a Loran-C cycle lock warning; `A` when OK or unused
 * @property crossTrackError distance off track in [crossTrackUnits]
 * @property steerTo which way to steer to regain the track
 * @property crossTrackUnits `N` for nautical miles or `K` for kilometres
 * @property arrivalCircleEntered `A` once the vessel is inside the destination's arrival circle
 * @property perpendicularPassed `A` once the vessel has crossed the line through the waypoint
 *   perpendicular to the leg
 * @property bearingOriginToDestination bearing along the leg, which does not change as the vessel
 *   moves
 * @property bearingOriginToDestinationReference which north [bearingOriginToDestination] uses
 * @property destinationWaypointId waypoint being steered to
 * @property bearingPositionToDestination bearing from where the vessel is now
 * @property bearingPositionToDestinationReference which north [bearingPositionToDestination] uses
 * @property headingToDestination heading to steer to reach the waypoint
 * @property headingToDestinationReference which north [headingToDestination] uses
 */
public data class Apb(
  override val talker: TalkerId,
  val status: DataStatus? = null,
  val cycleLockStatus: DataStatus? = null,
  val crossTrackError: Double? = null,
  val steerTo: Direction? = null,
  val crossTrackUnits: Units? = null,
  val arrivalCircleEntered: DataStatus? = null,
  val perpendicularPassed: DataStatus? = null,
  val bearingOriginToDestination: Double? = null,
  val bearingOriginToDestinationReference: BearingReference? = null,
  val destinationWaypointId: String? = null,
  val bearingPositionToDestination: Double? = null,
  val bearingPositionToDestinationReference: BearingReference? = null,
  val headingToDestination: Double? = null,
  val headingToDestinationReference: BearingReference? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** True once the vessel is inside the destination waypoint's arrival circle. */
  public val hasEnteredArrivalCircle: Boolean
    get() = arrivalCircleEntered == DataStatus.ACTIVE

  /** True once the vessel has passed the perpendicular through the destination waypoint. */
  public val hasPassedPerpendicular: Boolean
    get() = perpendicularPassed == DataStatus.ACTIVE

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        status.field(),
        cycleLockStatus.field(),
        crossTrackError.field(),
        steerTo.field(),
        crossTrackUnits.field(),
        arrivalCircleEntered.field(),
        perpendicularPassed.field(),
        bearingOriginToDestination.field(),
        bearingOriginToDestinationReference.field(),
        destinationWaypointId,
        bearingPositionToDestination.field(),
        bearingPositionToDestinationReference.field(),
        headingToDestination.field(),
        headingToDestinationReference.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "APB"

    private const val STATUS = 0
    private const val CYCLE_LOCK_STATUS = 1
    private const val CROSS_TRACK_ERROR = 2
    private const val STEER_TO = 3
    private const val CROSS_TRACK_UNITS = 4
    private const val ARRIVAL_CIRCLE = 5
    private const val PERPENDICULAR = 6
    private const val BEARING_ORIGIN_TO_DESTINATION = 7
    private const val BEARING_ORIGIN_REFERENCE = 8
    private const val DESTINATION_WAYPOINT_ID = 9
    private const val BEARING_POSITION_TO_DESTINATION = 10
    private const val BEARING_POSITION_REFERENCE = 11
    private const val HEADING_TO_DESTINATION = 12
    private const val HEADING_REFERENCE = 13

    /** Reads an APB sentence from its fields. */
    public fun from(fields: SentenceFields): Apb =
      Apb(
        talker = fields.talker,
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        cycleLockStatus = fields.advisoryCodedAt(CYCLE_LOCK_STATUS, DataStatus.entries),
        crossTrackError = fields.doubleAt(CROSS_TRACK_ERROR),
        steerTo = fields.codedAt(STEER_TO, Direction.entries),
        crossTrackUnits = fields.codedAt(CROSS_TRACK_UNITS, CROSS_TRACK_UNIT_CODES),
        arrivalCircleEntered = fields.advisoryCodedAt(ARRIVAL_CIRCLE, DataStatus.entries),
        perpendicularPassed = fields.advisoryCodedAt(PERPENDICULAR, DataStatus.entries),
        bearingOriginToDestination = fields.doubleAt(BEARING_ORIGIN_TO_DESTINATION),
        bearingOriginToDestinationReference =
          fields.codedAt(BEARING_ORIGIN_REFERENCE, BearingReference.entries),
        destinationWaypointId = fields.stringAt(DESTINATION_WAYPOINT_ID),
        bearingPositionToDestination = fields.doubleAt(BEARING_POSITION_TO_DESTINATION),
        bearingPositionToDestinationReference =
          fields.codedAt(BEARING_POSITION_REFERENCE, BearingReference.entries),
        headingToDestination = fields.doubleAt(HEADING_TO_DESTINATION),
        headingToDestinationReference = fields.codedAt(HEADING_REFERENCE, BearingReference.entries),
      )

    // Units carries nine codes overall; only these two are distances an autopilot reports.
    private val CROSS_TRACK_UNIT_CODES = listOf(Units.NAUTICAL_MILES, Units.KILOMETERS)
  }
}

/**
 * Recommended minimum navigation information: where the active destination waypoint is and how to
 * get there.
 *
 * Example: `$GPRMB,A,0.00,R,,RUSKI,5536.200,N,01436.500,E,432.3,234.9,,V*58`
 *
 * Sent whenever a destination waypoint is active. Not to be confused with RMC, which reports the
 * vessel's own position and is emitted whether or not anything is being navigated to.
 *
 * @property status `A` when the data is valid
 * @property crossTrackError distance off track in nautical miles
 * @property steerTo which way to steer to regain the track
 * @property originWaypointId waypoint the leg starts from; empty in GOTO mode
 * @property destinationWaypointId waypoint being steered to
 * @property destinationPosition where the destination waypoint is
 * @property range distance to the destination in nautical miles
 * @property bearing bearing to the destination in degrees true
 * @property velocity closing velocity in knots, negative when opening
 * @property arrivalStatus `A` once the vessel is inside the arrival circle or past the
 *   perpendicular
 * @property faaMode FAA mode indicator, added in NMEA 2.3; `null` from an older device. The Java
 *   implementation did not read this field.
 */
public data class Rmb(
  override val talker: TalkerId,
  val status: DataStatus? = null,
  val crossTrackError: Double? = null,
  val steerTo: Direction? = null,
  val originWaypointId: String? = null,
  val destinationWaypointId: String? = null,
  val destinationPosition: Position? = null,
  val range: Double? = null,
  val bearing: Double? = null,
  val velocity: Double? = null,
  val arrivalStatus: DataStatus? = null,
  val faaMode: FaaMode? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** The destination as a named waypoint, or `null` when its name or position is missing. */
  public val destination: Waypoint?
    get() =
      if (destinationPosition != null && destinationWaypointId != null) {
        Waypoint(destinationWaypointId, destinationPosition)
      } else {
        null
      }

  /** True once the vessel has arrived at the destination waypoint. */
  public val hasArrived: Boolean
    get() = arrivalStatus == DataStatus.ACTIVE

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        status.field(),
        crossTrackError.field(),
        steerTo.field(),
        originWaypointId,
        destinationWaypointId,
      ) +
        positionFields(destinationPosition) +
        listOf(
          range.field(),
          bearing.field(),
          velocity.field(),
          arrivalStatus.field(),
          faaMode.field(),
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RMB"

    private const val STATUS = 0
    private const val CROSS_TRACK_ERROR = 1
    private const val STEER_TO = 2
    private const val ORIGIN_WAYPOINT_ID = 3
    private const val DESTINATION_WAYPOINT_ID = 4
    private const val DESTINATION_LATITUDE = 5
    private const val DESTINATION_LATITUDE_HEMISPHERE = 6
    private const val DESTINATION_LONGITUDE = 7
    private const val DESTINATION_LONGITUDE_HEMISPHERE = 8
    private const val RANGE = 9
    private const val BEARING = 10
    private const val VELOCITY = 11
    private const val ARRIVAL_STATUS = 12
    private const val FAA_MODE = 13

    /** Reads an RMB sentence from its fields. */
    public fun from(fields: SentenceFields): Rmb =
      Rmb(
        talker = fields.talker,
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        crossTrackError = fields.doubleAt(CROSS_TRACK_ERROR),
        steerTo = fields.codedAt(STEER_TO, Direction.entries),
        originWaypointId = fields.stringAt(ORIGIN_WAYPOINT_ID),
        destinationWaypointId = fields.stringAt(DESTINATION_WAYPOINT_ID),
        destinationPosition =
          fields.positionAt(
            DESTINATION_LATITUDE,
            DESTINATION_LATITUDE_HEMISPHERE,
            DESTINATION_LONGITUDE,
            DESTINATION_LONGITUDE_HEMISPHERE,
          ),
        range = fields.doubleAt(RANGE),
        bearing = fields.doubleAt(BEARING),
        velocity = fields.doubleAt(VELOCITY),
        arrivalStatus = fields.advisoryCodedAt(ARRIVAL_STATUS, DataStatus.entries),
        faaMode = fields.advisoryCodedAt(FAA_MODE, FaaMode.entries),
      )
  }
}

/**
 * Autopilot steering data, the older and looser form of [Apb].
 *
 * Example: `$GPAPA,A,A,0.10,R,N,V,V,011,M,DEST*hh`
 *
 * The first ten fields of [Apb] and nothing after them. What it loses is the bearing from the
 * present position to the destination and the heading to steer -- the two numbers an autopilot most
 * wants -- so a pilot fed APA has to work them out itself. gpsd describes APB as "a fixed form of
 * the APA sentence with some ambiguities removed", and marks APA obsolete as of 2009. Prefer [Apb];
 * this exists because equipment still sends it.
 *
 * @property status [DataStatus.VOID] on a Loran-C blink or signal-to-noise warning
 * @property cycleLock [DataStatus.VOID] on a Loran-C cycle lock warning
 * @property crossTrackError distance off the intended track
 * @property steerDirection which way to steer to get back on it
 * @property crossTrackUnits the unit [crossTrackError] is in
 * @property arrivalCircleEntered [DataStatus.ACTIVE] once inside the arrival circle
 * @property perpendicularPassed [DataStatus.ACTIVE] once past the perpendicular at the waypoint
 * @property bearingOriginToDestination the leg's bearing
 * @property bearingReference whether that bearing is true or magnetic
 */
public data class Apa(
  override val talker: TalkerId,
  val status: DataStatus? = null,
  val cycleLock: DataStatus? = null,
  val crossTrackError: Double? = null,
  val steerDirection: Direction? = null,
  val crossTrackUnits: Units? = null,
  val arrivalCircleEntered: DataStatus? = null,
  val perpendicularPassed: DataStatus? = null,
  val bearingOriginToDestination: Double? = null,
  val bearingReference: BearingReference? = null,
  val destinationWaypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        status.field(),
        cycleLock.field(),
        crossTrackError.field(),
        steerDirection.field(),
        crossTrackUnits.field(),
        arrivalCircleEntered.field(),
        perpendicularPassed.field(),
        bearingOriginToDestination.field(),
        bearingReference.field(),
        destinationWaypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "APA"

    private const val STATUS = 0
    private const val CYCLE_LOCK = 1
    private const val CROSS_TRACK_ERROR = 2
    private const val STEER_DIRECTION = 3
    private const val CROSS_TRACK_UNITS = 4
    private const val ARRIVAL_CIRCLE = 5
    private const val PERPENDICULAR = 6
    private const val BEARING = 7
    private const val BEARING_REFERENCE = 8
    private const val DESTINATION = 9

    /** Reads an APA sentence from its fields. */
    public fun from(fields: SentenceFields): Apa =
      Apa(
        talker = fields.talker,
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        cycleLock = fields.advisoryCodedAt(CYCLE_LOCK, DataStatus.entries),
        crossTrackError = fields.doubleAt(CROSS_TRACK_ERROR),
        // Load-bearing, both of them: the direction is the sign of the error and the unit is its
        // scale. Misreading either puts the vessel the wrong distance the wrong way.
        steerDirection = fields.codedAt(STEER_DIRECTION, LEFT_RIGHT),
        crossTrackUnits = fields.codedAt(CROSS_TRACK_UNITS, CROSS_TRACK_UNIT_CODES),
        arrivalCircleEntered = fields.advisoryCodedAt(ARRIVAL_CIRCLE, DataStatus.entries),
        perpendicularPassed = fields.advisoryCodedAt(PERPENDICULAR, DataStatus.entries),
        bearingOriginToDestination = fields.doubleAt(BEARING),
        bearingReference = fields.codedAt(BEARING_REFERENCE, BearingReference.entries),
        destinationWaypointId = fields.stringAt(DESTINATION),
      )

    private val LEFT_RIGHT = listOf(Direction.LEFT, Direction.RIGHT)
    private val CROSS_TRACK_UNIT_CODES = listOf(Units.NAUTICAL_MILES, Units.KILOMETERS)
  }
}

/**
 * Cross-track error worked out by dead reckoning rather than measured.
 *
 * Example: `$GPXTR,0.10,R,N*hh`
 *
 * [Xte]'s three middle fields with the two status flags removed, and the removal is the point: this
 * is what a receiver reports when it has no fix and is estimating its position from the last one
 * plus heading and speed. There is no signal to report the quality of.
 *
 * @property crossTrackError distance off the intended track
 * @property steerDirection which way to steer to get back on it
 * @property units the unit [crossTrackError] is in
 */
public data class Xtr(
  override val talker: TalkerId,
  val crossTrackError: Double? = null,
  val steerDirection: Direction? = null,
  val units: Units? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(crossTrackError.field(), steerDirection.field(), units.field()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "XTR"

    private const val CROSS_TRACK_ERROR = 0
    private const val STEER_DIRECTION = 1
    private const val UNITS = 2

    /** Reads an XTR sentence from its fields. */
    public fun from(fields: SentenceFields): Xtr =
      Xtr(
        talker = fields.talker,
        crossTrackError = fields.doubleAt(CROSS_TRACK_ERROR),
        steerDirection = fields.codedAt(STEER_DIRECTION, LEFT_RIGHT),
        units = fields.codedAt(UNITS, listOf(Units.NAUTICAL_MILES, Units.KILOMETERS)),
      )

    private val LEFT_RIGHT = listOf(Direction.LEFT, Direction.RIGHT)
  }
}

/**
 * Recommended minimum navigation information, the Loran-C form.
 *
 * Example: `$GPRMA,A,4916.45,N,12311.12,W,,,000.5,054.7,020.3,E*hh`
 *
 * The third of the RMx family and the one that shows its age. [Rmc] is the GPS form and carries a
 * date; [Rmb] describes the leg being steered; this carries a position, a velocity and two Loran-C
 * time differences, and no date at all.
 *
 * @property status [DataStatus.ACTIVE] when the position is usable
 * @property position where the vessel is
 * @property timeDifferenceA the first Loran-C time difference, microseconds
 * @property timeDifferenceB the second
 * @property speedOverGround knots
 * @property trackMadeGood degrees true
 * @property magneticVariation unsigned magnitude, paired with [variationDirection]
 * @property variationDirection which way [magneticVariation] points
 */
public data class Rma(
  override val talker: TalkerId,
  val status: DataStatus? = null,
  val position: Position? = null,
  val timeDifferenceA: Double? = null,
  val timeDifferenceB: Double? = null,
  val speedOverGround: Double? = null,
  val trackMadeGood: Double? = null,
  val magneticVariation: Double? = null,
  val variationDirection: CompassPoint? = null,
) : Sentence {

  init {
    requireEastWest(magneticVariation, variationDirection, "variation")
  }

  override val id: String
    get() = ID

  /** [magneticVariation] signed east-positive, or `null` when the sentence reports none. */
  public val variationEastPositive: Double?
    get() = magneticVariation?.let { if (variationDirection == CompassPoint.WEST) -it else it }

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(status.field()) +
        positionFields(position) +
        listOf(
          timeDifferenceA.field(),
          timeDifferenceB.field(),
          speedOverGround.field(),
          trackMadeGood.field(),
          magneticVariation.field(),
          variationDirection.field(),
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RMA"

    private const val STATUS = 0
    private const val LATITUDE = 1
    private const val LATITUDE_HEMISPHERE = 2
    private const val LONGITUDE = 3
    private const val LONGITUDE_HEMISPHERE = 4
    private const val TIME_DIFFERENCE_A = 5
    private const val TIME_DIFFERENCE_B = 6
    private const val SPEED = 7
    private const val TRACK = 8
    private const val VARIATION = 9
    private const val VARIATION_DIRECTION = 10

    /** Reads an RMA sentence from its fields. */
    public fun from(fields: SentenceFields): Rma {
      val variation = fields.magneticAngleAt(VARIATION, VARIATION_DIRECTION)
      return Rma(
        talker = fields.talker,
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        timeDifferenceA = fields.doubleAt(TIME_DIFFERENCE_A),
        timeDifferenceB = fields.doubleAt(TIME_DIFFERENCE_B),
        speedOverGround = fields.doubleAt(SPEED),
        trackMadeGood = fields.doubleAt(TRACK),
        magneticVariation = variation.magnitude,
        variationDirection = variation.direction,
      )
    }
  }
}

/**
 * Time still to run to the destination waypoint.
 *
 * Example: `$GPZTG,092204,010000,DEST*hh`
 *
 * @property time UTC the estimate was made at
 * @property timeRemaining how long the leg still has to run
 * @property destinationWaypointId the waypoint it runs to
 */
public data class Ztg(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val timeRemaining: Duration? = null,
  val destinationWaypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        time?.let { NmeaDateTime.formatTime(it) },
        timeRemaining?.let { NmeaDateTime.formatElapsed(it) },
        destinationWaypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ZTG"

    private const val TIME = 0
    private const val TIME_REMAINING = 1
    private const val DESTINATION = 2

    /** Reads a ZTG sentence from its fields. */
    public fun from(fields: SentenceFields): Ztg =
      Ztg(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        timeRemaining = fields.elapsedAt(TIME_REMAINING),
        destinationWaypointId = fields.stringAt(DESTINATION),
      )
  }
}

/**
 * Time elapsed since leaving the origin waypoint. [Ztg] counting the other way.
 *
 * Example: `$GPZFO,092204,003500,ORIGIN*hh`
 *
 * @property time UTC of the observation
 * @property elapsedTime how long since the origin was left
 * @property originWaypointId the waypoint it was left from
 */
public data class Zfo(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val elapsedTime: Duration? = null,
  val originWaypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        time?.let { NmeaDateTime.formatTime(it) },
        elapsedTime?.let { NmeaDateTime.formatElapsed(it) },
        originWaypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ZFO"

    private const val TIME = 0
    private const val ELAPSED = 1
    private const val ORIGIN = 2

    /** Reads a ZFO sentence from its fields. */
    public fun from(fields: SentenceFields): Zfo =
      Zfo(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        elapsedTime = fields.elapsedAt(ELAPSED),
        originWaypointId = fields.stringAt(ORIGIN),
      )
  }
}
