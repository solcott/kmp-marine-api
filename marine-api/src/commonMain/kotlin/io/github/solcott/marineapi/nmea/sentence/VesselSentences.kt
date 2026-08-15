package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.BearingReference
import io.github.solcott.marineapi.nmea.CharCoded
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.ReferenceSystem
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.SteeringMode
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.TurnMode
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/** What a revolution count is measured on. */
public enum class RevolutionSource(override val code: Char) : CharCoded {
  /** A propeller shaft. */
  SHAFT('S'),
  /** An engine. */
  ENGINE('E'),
}

/**
 * Engine or shaft revolutions.
 *
 * Example: `$IIRPM,E,1,2418.2,10.5,A*XX`
 *
 * @property source whether [revolutions] is an engine or a shaft speed
 * @property sourceNumber which engine or shaft, numbered from the centreline outwards
 * @property revolutions revolutions per minute
 * @property propellerPitch percent of maximum; negative means astern
 * @property status whether the reading is valid
 */
public data class Rpm(
  override val talker: TalkerId,
  val source: RevolutionSource? = null,
  val sourceNumber: Int? = null,
  val revolutions: Double? = null,
  val propellerPitch: Double? = null,
  val status: DataStatus? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        source.field(),
        sourceNumber?.toString(),
        revolutions.field(),
        propellerPitch.field(),
        status.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RPM"

    private const val SOURCE = 0
    private const val SOURCE_NUMBER = 1
    private const val REVOLUTIONS = 2
    private const val PROPELLER_PITCH = 3
    private const val STATUS = 4

    /** Reads an RPM sentence from its fields. */
    public fun from(fields: SentenceFields): Rpm =
      Rpm(
        talker = fields.talker,
        source = fields.advisoryCodedAt(SOURCE, RevolutionSource.entries),
        sourceNumber = fields.intAt(SOURCE_NUMBER),
        revolutions = fields.doubleAt(REVOLUTIONS),
        propellerPitch = fields.doubleAt(PROPELLER_PITCH),
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
      )
  }
}

/**
 * Water current at one depth layer, as a current profiler reports it.
 *
 * Example: `$INCUR,A,1,0,0.0,0.0,T,1.5,0.0,90.0,T,B*XX`
 *
 * A profiler measures the current at several depths and sends one of these per layer, so
 * [dataSetNumber] and [layerNumber] are what tie a set of them together.
 *
 * @property status whether the reading is valid
 * @property dataSetNumber which set of measurements this belongs to
 * @property layerNumber which depth layer within that set
 * @property currentDepth depth of this layer, metres
 * @property currentDirection direction the current flows towards, degrees
 * @property directionReference whether [currentDirection] is true or relative to the vessel
 * @property currentSpeed current speed, knots
 * @property referenceLayerDepth depth of the layer speed is referenced against, metres
 * @property currentHeading vessel heading used for the measurement
 * @property headingReference whether [currentHeading] is true or magnetic
 * @property speedReference what the speed is measured against -- the bottom, the water, or a
 *   positioning system
 */
public data class Cur(
  override val talker: TalkerId,
  val status: DataStatus? = null,
  val dataSetNumber: Int? = null,
  val layerNumber: Int? = null,
  val currentDepth: Double? = null,
  val currentDirection: Double? = null,
  val directionReference: AngleReference? = null,
  val currentSpeed: Double? = null,
  val referenceLayerDepth: Double? = null,
  val currentHeading: Double? = null,
  val headingReference: BearingReference? = null,
  val speedReference: ReferenceSystem? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        status.field(),
        dataSetNumber?.toString(),
        layerNumber?.toString(),
        currentDepth.field(),
        currentDirection.field(),
        directionReference.field(),
        currentSpeed.field(),
        referenceLayerDepth.field(),
        currentHeading.field(),
        headingReference.field(),
        speedReference.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "CUR"

    private const val STATUS = 0
    private const val DATA_SET = 1
    private const val LAYER = 2
    private const val CURRENT_DEPTH = 3
    private const val CURRENT_DIRECTION = 4
    private const val DIRECTION_REFERENCE = 5
    private const val CURRENT_SPEED = 6
    private const val REFERENCE_LAYER_DEPTH = 7
    private const val CURRENT_HEADING = 8
    private const val HEADING_REFERENCE = 9
    private const val SPEED_REFERENCE = 10

    /** Reads a CUR sentence from its fields. */
    public fun from(fields: SentenceFields): Cur =
      Cur(
        talker = fields.talker,
        status = fields.advisoryCodedAt(STATUS, DataStatus.entries),
        dataSetNumber = fields.intAt(DATA_SET),
        layerNumber = fields.intAt(LAYER),
        currentDepth = fields.doubleAt(CURRENT_DEPTH),
        currentDirection = fields.doubleAt(CURRENT_DIRECTION),
        directionReference = fields.codedAt(DIRECTION_REFERENCE, AngleReference.entries),
        currentSpeed = fields.doubleAt(CURRENT_SPEED),
        referenceLayerDepth = fields.doubleAt(REFERENCE_LAYER_DEPTH),
        currentHeading = fields.doubleAt(CURRENT_HEADING),
        headingReference = fields.codedAt(HEADING_REFERENCE, BearingReference.entries),
        speedReference = fields.advisoryCodedAt(SPEED_REFERENCE, ReferenceSystem.entries),
      )
  }
}

/**
 * Heading and track control command: what an autopilot is being told to do.
 *
 * Example: `$AGHTC,V,0.1,R,M,,15.0,15.0,,,,,,T*XX`
 *
 * The command half of a pair. [Htd] reports back what the autopilot is actually doing, with the
 * same thirteen fields followed by four more.
 *
 * @property override whether manual override is active
 * @property commandedRudderAngle rudder angle being commanded, degrees
 * @property commandedRudderDirection which way that angle turns the vessel
 * @property selectedSteeringMode steering mode in use
 * @property turnMode how a turn is being controlled -- by radius, by rate, or not at all
 * @property commandedRudderLimit maximum rudder angle allowed, degrees
 * @property commandedOffHeadingLimit maximum heading error allowed, degrees
 * @property commandedRadiusOfTurn radius commanded for heading changes
 * @property commandedRateOfTurn rate commanded for heading changes, degrees per minute
 * @property commandedHeadingToSteer heading to steer, degrees
 * @property commandedOffTrackLimit maximum cross-track error allowed
 * @property commandedTrack track to make good, degrees
 * @property headingReference whether the headings above are true or magnetic
 */
public data class Htc(
  override val talker: TalkerId,
  val override: DataStatus? = null,
  val commandedRudderAngle: Double? = null,
  val commandedRudderDirection: Direction? = null,
  val selectedSteeringMode: SteeringMode? = null,
  val turnMode: TurnMode? = null,
  val commandedRudderLimit: Double? = null,
  val commandedOffHeadingLimit: Double? = null,
  val commandedRadiusOfTurn: Double? = null,
  val commandedRateOfTurn: Double? = null,
  val commandedHeadingToSteer: Double? = null,
  val commandedOffTrackLimit: Double? = null,
  val commandedTrack: Double? = null,
  val headingReference: BearingReference? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, controlFields(this))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HTC"

    /** Reads an HTC sentence from its fields. */
    public fun from(fields: SentenceFields): Htc = fields.readControlFields(fields.talker)
  }
}

/**
 * Heading and track control data: what an autopilot is actually doing.
 *
 * Example: `$AGHTD,V,0.1,R,M,,15.0,15.0,,,,,,T,A,A,A,90.3,*39`
 *
 * The report half of the pair [Htc] commands. Its first thirteen fields are HTC's, so [command]
 * exposes them as one, and the four after them say whether the autopilot is managing to hold what
 * it was told to.
 *
 * @property command the thirteen command fields this sentence echoes
 * @property rudderStatus whether the rudder is within its commanded limit
 * @property offHeadingStatus whether the vessel is within its commanded heading limit
 * @property offTrackStatus whether the vessel is within its commanded track limit
 * @property heading vessel heading, degrees
 */
public data class Htd(
  override val talker: TalkerId,
  val command: Htc,
  val rudderStatus: DataStatus? = null,
  val offHeadingStatus: DataStatus? = null,
  val offTrackStatus: DataStatus? = null,
  val heading: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      controlFields(command) +
        listOf(
          rudderStatus.field(),
          offHeadingStatus.field(),
          offTrackStatus.field(),
          heading.field(),
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HTD"

    private const val RUDDER_STATUS = 13
    private const val OFF_HEADING_STATUS = 14
    private const val OFF_TRACK_STATUS = 15
    private const val HEADING = 16

    /** Reads an HTD sentence from its fields. */
    public fun from(fields: SentenceFields): Htd =
      Htd(
        talker = fields.talker,
        command = fields.readControlFields(fields.talker),
        rudderStatus = fields.advisoryCodedAt(RUDDER_STATUS, DataStatus.entries),
        offHeadingStatus = fields.advisoryCodedAt(OFF_HEADING_STATUS, DataStatus.entries),
        offTrackStatus = fields.advisoryCodedAt(OFF_TRACK_STATUS, DataStatus.entries),
        heading = fields.doubleAt(HEADING),
      )
  }
}

// HTC's thirteen fields are also HTD's first thirteen, so both the reader and the writer are
// shared rather than transcribed twice.
private const val OVERRIDE = 0
private const val RUDDER_ANGLE = 1
private const val RUDDER_DIRECTION = 2
private const val STEERING_MODE = 3
private const val TURN_MODE = 4
private const val RUDDER_LIMIT = 5
private const val OFF_HEADING_LIMIT = 6
private const val RADIUS_OF_TURN = 7
private const val RATE_OF_TURN = 8
private const val HEADING_TO_STEER = 9
private const val OFF_TRACK_LIMIT = 10
private const val TRACK = 11
private const val HEADING_REFERENCE = 12

private fun SentenceFields.readControlFields(talker: TalkerId): Htc =
  Htc(
    talker = talker,
    override = advisoryCodedAt(OVERRIDE, DataStatus.entries),
    commandedRudderAngle = doubleAt(RUDDER_ANGLE),
    commandedRudderDirection = codedAt(RUDDER_DIRECTION, Direction.entries),
    selectedSteeringMode = advisoryCodedAt(STEERING_MODE, SteeringMode.entries),
    turnMode = advisoryCodedAt(TURN_MODE, TurnMode.entries),
    commandedRudderLimit = doubleAt(RUDDER_LIMIT),
    commandedOffHeadingLimit = doubleAt(OFF_HEADING_LIMIT),
    commandedRadiusOfTurn = doubleAt(RADIUS_OF_TURN),
    commandedRateOfTurn = doubleAt(RATE_OF_TURN),
    commandedHeadingToSteer = doubleAt(HEADING_TO_STEER),
    commandedOffTrackLimit = doubleAt(OFF_TRACK_LIMIT),
    commandedTrack = doubleAt(TRACK),
    headingReference = codedAt(HEADING_REFERENCE, BearingReference.entries),
  )

private fun controlFields(htc: Htc): List<String?> =
  listOf(
    htc.override.field(),
    htc.commandedRudderAngle.field(),
    htc.commandedRudderDirection.field(),
    htc.selectedSteeringMode.field(),
    htc.turnMode.field(),
    htc.commandedRudderLimit.field(),
    htc.commandedOffHeadingLimit.field(),
    htc.commandedRadiusOfTurn.field(),
    htc.commandedRateOfTurn.field(),
    htc.commandedHeadingToSteer.field(),
    htc.commandedOffTrackLimit.field(),
    htc.commandedTrack.field(),
    htc.headingReference.field(),
  )
