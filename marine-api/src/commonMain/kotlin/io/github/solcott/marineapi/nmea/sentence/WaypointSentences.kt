package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.NmeaDateTime
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.RouteType
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.Waypoint
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field
import io.github.solcott.marineapi.nmea.positionAt
import io.github.solcott.marineapi.nmea.positionFields
import kotlinx.datetime.LocalTime

/**
 * Bearing from one waypoint to another.
 *
 * Example: `$GPBOD,234.9,T,228.8,M,RUSKI,*1D`
 *
 * This is the bearing along the active leg, from [originWaypointId] to [destinationWaypointId] --
 * **not** the bearing from where the vessel is now, and it does not change as the vessel moves. In
 * GOTO mode there is no origin and the last field is empty, as in the example above.
 *
 * The `T` and `M` fields are fixed markers naming the value before them and are written back as
 * constants.
 *
 * Replaced by BWW in NMEA 4.00.
 *
 * @property bearingTrue bearing in degrees true
 * @property bearingMagnetic bearing in degrees magnetic
 * @property destinationWaypointId waypoint being steered to
 * @property originWaypointId waypoint the leg starts from; `null` in GOTO mode
 */
public data class Bod(
  override val talker: TalkerId,
  val bearingTrue: Double? = null,
  val bearingMagnetic: Double? = null,
  val destinationWaypointId: String? = null,
  val originWaypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        bearingTrue.field(),
        TRUE_MARKER.toString(),
        bearingMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        destinationWaypointId,
        originWaypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "BOD"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'

    private const val BEARING_TRUE = 0
    private const val BEARING_MAGNETIC = 2
    private const val DESTINATION = 4
    private const val ORIGIN = 5

    /** Reads a BOD sentence from its fields. */
    public fun from(fields: SentenceFields): Bod =
      Bod(
        talker = fields.talker,
        bearingTrue = fields.doubleAt(BEARING_TRUE),
        bearingMagnetic = fields.doubleAt(BEARING_MAGNETIC),
        destinationWaypointId = fields.stringAt(DESTINATION),
        originWaypointId = fields.stringAt(ORIGIN),
      )
  }
}

/**
 * A waypoint's name and location.
 *
 * Example: `$GPWPL,5536.200,N,01436.500,E,RUSKI*1F`
 *
 * [position] and [waypointId] are kept as separate properties, with [waypoint] pairing them when
 * both are present. Modelling this as a single non-null [Waypoint] would mean a sentence that
 * carries a name but no coordinates -- or coordinates but no name -- had to be discarded, and
 * neither half is worth throwing away.
 *
 * @property position where the waypoint is
 * @property waypointId the waypoint's name
 */
public data class Wpl(
  override val talker: TalkerId,
  val position: Position? = null,
  val waypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** [position] and [waypointId] together, or `null` when the sentence is missing either. */
  public val waypoint: Waypoint?
    get() = if (position != null && waypointId != null) Waypoint(waypointId, position) else null

  override fun toNmeaString(): String =
    buildNmea(talker, ID, positionFields(position) + listOf(waypointId))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "WPL"

    private const val LATITUDE = 0
    private const val LATITUDE_HEMISPHERE = 1
    private const val LONGITUDE = 2
    private const val LONGITUDE_HEMISPHERE = 3
    private const val WAYPOINT_ID = 4

    /** Reads a WPL sentence from its fields. */
    public fun from(fields: SentenceFields): Wpl =
      Wpl(
        talker = fields.talker,
        position =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        waypointId = fields.stringAt(WAYPOINT_ID),
      )
  }
}

/**
 * A route and the waypoints along it.
 *
 * Example: `$GPRTE,1,1,c,0,MELIN,RUSKI,KNUDAN*25`
 *
 * A route longer than one sentence is split across several: [sentenceCount] says how many and
 * [sentenceIndex] which this is. The waypoint list runs to the end of the sentence, so unlike every
 * other type here the field count is not fixed.
 *
 * @property sentenceCount sentences making up this route
 * @property sentenceIndex which sentence this is, counting from 1
 * @property routeType whether the list is the complete route or the remaining legs
 * @property routeId the route's name
 * @property waypointIds waypoints in order, `null` for an empty field
 */
public data class Rte(
  override val talker: TalkerId,
  val sentenceCount: Int? = null,
  val sentenceIndex: Int? = null,
  val routeType: RouteType? = null,
  val routeId: String? = null,
  val waypointIds: List<String?> = emptyList(),
) : Sentence {

  override val id: String
    get() = ID

  /**
   * The waypoints this sentence actually names, empty fields dropped.
   *
   * [waypointIds] keeps the empties so the sentence re-encodes to itself; this is the list to
   * iterate.
   */
  public val waypoints: List<String>
    get() = waypointIds.filterNotNull()

  /** True if this is the first sentence of the route. */
  public val isFirst: Boolean
    get() = sentenceIndex == 1

  /** True if this is the last sentence of the route. */
  public val isLast: Boolean
    get() = sentenceIndex != null && sentenceIndex == sentenceCount

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        sentenceCount?.toString(),
        sentenceIndex?.toString(),
        routeType.field(),
        routeId,
      ) + waypointIds,
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "RTE"

    private const val SENTENCE_COUNT = 0
    private const val SENTENCE_INDEX = 1
    private const val ROUTE_TYPE = 2
    private const val ROUTE_ID = 3
    private const val FIRST_WAYPOINT = 4

    /** Reads an RTE sentence from its fields. */
    public fun from(fields: SentenceFields): Rte =
      Rte(
        talker = fields.talker,
        sentenceCount = fields.intAt(SENTENCE_COUNT),
        sentenceIndex = fields.intAt(SENTENCE_INDEX),
        routeType = fields.advisoryCodedAt(ROUTE_TYPE, RouteType.entries),
        routeId = fields.stringAt(ROUTE_ID),
        waypointIds = fields.stringsFrom(FIRST_WAYPOINT),
      )
  }
}

/**
 * Bearing and great-circle distance to a waypoint, with the waypoint's position and the time.
 *
 * Example: `$GPBWC,220516,5130.02,N,00046.34,W,213.8,T,218.0,M,0004.6,N,EGLM*11`
 *
 * The sentence [Bod] was replaced by in NMEA 4.00, and it says considerably more: where the
 * waypoint is, how far off it is, and when that was true. Unlike BOD's leg bearing, this is
 * measured from where the vessel is now, so it changes as the vessel moves.
 *
 * A receiver with no active waypoint still emits it, empty apart from the markers, as in
 * `$GPBWC,125106,,,,,,T,,M,,N,,S`.
 *
 * The `T`, `M` and `N` fields are fixed markers and are written back as constants.
 *
 * @property time UTC of the observation
 * @property waypointPosition where the waypoint is
 * @property bearingTrue bearing to the waypoint in degrees true
 * @property bearingMagnetic bearing to the waypoint in degrees magnetic
 * @property distanceNauticalMiles great-circle distance to the waypoint
 * @property waypointId the waypoint's name
 * @property faaMode FAA mode indicator, added in NMEA 2.3
 */
public data class Bwc(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val waypointPosition: Position? = null,
  val bearingTrue: Double? = null,
  val bearingMagnetic: Double? = null,
  val distanceNauticalMiles: Double? = null,
  val waypointId: String? = null,
  val faaMode: FaaMode? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** [waypointPosition] and [waypointId] together, or `null` when either is missing. */
  public val waypoint: Waypoint?
    get() =
      if (waypointPosition != null && waypointId != null) {
        Waypoint(waypointId, waypointPosition)
      } else {
        null
      }

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(time?.let { NmeaDateTime.formatTime(it) }) +
        positionFields(waypointPosition) +
        listOf(
          bearingTrue.field(),
          TRUE_MARKER.toString(),
          bearingMagnetic.field(),
          MAGNETIC_MARKER.toString(),
          distanceNauticalMiles.field(),
          NAUTICAL_MILES_MARKER.toString(),
          waypointId,
          faaMode.field(),
        ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "BWC"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val NAUTICAL_MILES_MARKER = 'N'

    private const val TIME = 0
    private const val LATITUDE = 1
    private const val LATITUDE_HEMISPHERE = 2
    private const val LONGITUDE = 3
    private const val LONGITUDE_HEMISPHERE = 4
    private const val BEARING_TRUE = 5
    private const val BEARING_MAGNETIC = 7
    private const val DISTANCE = 9
    private const val WAYPOINT_ID = 11
    private const val FAA_MODE = 12

    /** Reads a BWC sentence from its fields. */
    public fun from(fields: SentenceFields): Bwc =
      Bwc(
        talker = fields.talker,
        time = fields.timeAt(TIME),
        waypointPosition =
          fields.positionAt(LATITUDE, LATITUDE_HEMISPHERE, LONGITUDE, LONGITUDE_HEMISPHERE),
        bearingTrue = fields.doubleAt(BEARING_TRUE),
        bearingMagnetic = fields.doubleAt(BEARING_MAGNETIC),
        distanceNauticalMiles = fields.doubleAt(DISTANCE),
        waypointId = fields.stringAt(WAYPOINT_ID),
        faaMode = fields.advisoryCodedAt(FAA_MODE, FaaMode.entries),
      )
  }
}

/**
 * Bearing and distance to a waypoint along a rhumb line: [Bwc] over a different kind of course.
 *
 * Example: `$GPBWR,220516,5130.02,N,00046.34,W,213.8,T,218.0,M,0004.6,N,EGLM,A*hh`
 *
 * Field for field identical to [Bwc], and it is the difference in meaning that makes both worth
 * having. BWC gives the great circle, the shortest path across a sphere, whose bearing changes
 * continuously as you fly it. This gives the rhumb line, which crosses every meridian at the same
 * angle -- longer, but steerable on one compass course. Over short legs the two agree; over an
 * ocean they do not.
 *
 * @property waypointPosition where the waypoint is
 * @property bearingTrue degrees true along the rhumb line
 * @property bearingMagnetic the same bearing, magnetic
 * @property distanceNauticalMiles rhumb line distance, longer than [Bwc]'s
 * @property faaMode how the fix behind this was obtained, absent before NMEA 2.3
 */
public data class Bwr(
  override val talker: TalkerId,
  val time: LocalTime? = null,
  val waypointPosition: Position? = null,
  val bearingTrue: Double? = null,
  val bearingMagnetic: Double? = null,
  val distanceNauticalMiles: Double? = null,
  val waypointId: String? = null,
  val faaMode: FaaMode? = null,
) : Sentence {

  override val id: String
    get() = ID

  /** [waypointPosition] and [waypointId] together, or `null` when either is missing. */
  public val waypoint: Waypoint?
    get() =
      if (waypointPosition != null && waypointId != null) {
        Waypoint(waypointId, waypointPosition)
      } else {
        null
      }

  override fun toNmeaString(): String = bearingToWaypointNmea(talker, ID, this.asBearingFields())

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "BWR"

    /** Reads a BWR sentence from its fields. */
    public fun from(fields: SentenceFields): Bwr =
      with(fields.bearingToWaypointAt()) {
        Bwr(
          talker = fields.talker,
          time = time,
          waypointPosition = waypointPosition,
          bearingTrue = bearingTrue,
          bearingMagnetic = bearingMagnetic,
          distanceNauticalMiles = distance,
          waypointId = waypointId,
          faaMode = faaMode,
        )
      }
  }
}

/**
 * Bearing from one waypoint to the next, with the vessel's own position nowhere in it.
 *
 * Example: `$GPBWW,213.8,T,218.0,M,DEST,ORIGIN*hh`
 *
 * A property of the route rather than of the voyage: it says which way the leg runs, not how far
 * along it you are. [Bod] carries the same bearing for the leg currently being steered; this can
 * describe any pair.
 *
 * @property bearingTrue degrees true from [fromWaypointId] to [toWaypointId]
 * @property bearingMagnetic the same bearing, magnetic
 * @property toWaypointId the waypoint the leg runs to
 * @property fromWaypointId the waypoint it runs from
 */
public data class Bww(
  override val talker: TalkerId,
  val bearingTrue: Double? = null,
  val bearingMagnetic: Double? = null,
  val toWaypointId: String? = null,
  val fromWaypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        bearingTrue.field(),
        TRUE_MARKER.toString(),
        bearingMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        toWaypointId,
        fromWaypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "BWW"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val BEARING_TRUE = 0
    private const val BEARING_MAGNETIC = 2
    private const val TO_WAYPOINT = 4
    private const val FROM_WAYPOINT = 5

    /** Reads a BWW sentence from its fields. */
    public fun from(fields: SentenceFields): Bww =
      Bww(
        talker = fields.talker,
        bearingTrue = fields.doubleAt(BEARING_TRUE),
        bearingMagnetic = fields.doubleAt(BEARING_MAGNETIC),
        toWaypointId = fields.stringAt(TO_WAYPOINT),
        fromWaypointId = fields.stringAt(FROM_WAYPOINT),
      )
  }
}

/**
 * Distance between two waypoints, in both units at once.
 *
 * Example: `$GPWNC,4.6,N,8.5,K,DEST,ORIGIN*hh`
 *
 * The distance counterpart to [Bww]: that gives the bearing of a leg, this gives its length.
 *
 * @property distanceNauticalMiles the leg's length in nautical miles
 * @property distanceKilometres the same length in kilometres
 * @property toWaypointId the waypoint the leg runs to
 * @property fromWaypointId the waypoint it runs from
 */
public data class Wnc(
  override val talker: TalkerId,
  val distanceNauticalMiles: Double? = null,
  val distanceKilometres: Double? = null,
  val toWaypointId: String? = null,
  val fromWaypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        distanceNauticalMiles.field(),
        NAUTICAL_MILES_MARKER.toString(),
        distanceKilometres.field(),
        KILOMETRES_MARKER.toString(),
        toWaypointId,
        fromWaypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "WNC"

    private const val NAUTICAL_MILES_MARKER = 'N'
    private const val KILOMETRES_MARKER = 'K'
    private const val DISTANCE_NAUTICAL = 0
    private const val DISTANCE_KILOMETRES = 2
    private const val TO_WAYPOINT = 4
    private const val FROM_WAYPOINT = 5

    /** Reads a WNC sentence from its fields. */
    public fun from(fields: SentenceFields): Wnc =
      Wnc(
        talker = fields.talker,
        distanceNauticalMiles = fields.doubleAt(DISTANCE_NAUTICAL),
        distanceKilometres = fields.doubleAt(DISTANCE_KILOMETRES),
        toWaypointId = fields.stringAt(TO_WAYPOINT),
        fromWaypointId = fields.stringAt(FROM_WAYPOINT),
      )
  }
}

/**
 * How fast the vessel is closing on a waypoint, which is not how fast it is going.
 *
 * Example: `$GPWCV,4.5,N,DEST,A*hh`
 *
 * The component of velocity along the bearing to the waypoint. A vessel making six knots across the
 * leg rather than along it closes at nearly nothing, and this is the number that says so -- it is
 * what an estimated time of arrival should be computed from.
 *
 * @property velocityKnots closing velocity, negative when the waypoint is receding
 * @property waypointId the waypoint being closed on
 * @property faaMode how the fix behind this was obtained, absent before NMEA 3
 */
public data class Wcv(
  override val talker: TalkerId,
  val velocityKnots: Double? = null,
  val waypointId: String? = null,
  val faaMode: FaaMode? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(velocityKnots.field(), KNOTS_MARKER.toString(), waypointId, faaMode.field()),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "WCV"

    private const val KNOTS_MARKER = 'N'
    private const val VELOCITY = 0
    private const val WAYPOINT_ID = 2
    private const val FAA_MODE = 3

    /** Reads a WCV sentence from its fields. */
    public fun from(fields: SentenceFields): Wcv =
      Wcv(
        talker = fields.talker,
        velocityKnots = fields.doubleAt(VELOCITY),
        waypointId = fields.stringAt(WAYPOINT_ID),
        faaMode = fields.advisoryCodedAt(FAA_MODE, FaaMode.entries),
      )
  }
}

/**
 * Whether the vessel has arrived at a waypoint, and by which of two tests.
 *
 * Example: `$GPAAM,A,A,0.10,N,DEST*hh`
 *
 * Arrival is two separate questions and this answers both. [circleEntered] is whether the vessel
 * came within [arrivalCircleRadius] of the mark; [perpendicularPassed] is whether it crossed the
 * line through the mark at right angles to the leg. A vessel that passes wide of a waypoint trips
 * the second without ever tripping the first, which is what stops a route stalling on a mark that
 * was never quite reached.
 *
 * @property circleEntered [DataStatus.ACTIVE] once inside the arrival circle
 * @property perpendicularPassed [DataStatus.ACTIVE] once past the perpendicular
 * @property arrivalCircleRadius radius of the circle, in nautical miles
 * @property waypointId the waypoint being arrived at
 */
public data class Aam(
  override val talker: TalkerId,
  val circleEntered: DataStatus? = null,
  val perpendicularPassed: DataStatus? = null,
  val arrivalCircleRadius: Double? = null,
  val waypointId: String? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        circleEntered.field(),
        perpendicularPassed.field(),
        arrivalCircleRadius.field(),
        NAUTICAL_MILES_MARKER.toString(),
        waypointId,
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "AAM"

    private const val NAUTICAL_MILES_MARKER = 'N'
    private const val CIRCLE_ENTERED = 0
    private const val PERPENDICULAR_PASSED = 1
    private const val RADIUS = 2
    private const val WAYPOINT_ID = 4

    /** Reads an AAM sentence from its fields. */
    public fun from(fields: SentenceFields): Aam =
      Aam(
        talker = fields.talker,
        circleEntered = fields.advisoryCodedAt(CIRCLE_ENTERED, DataStatus.entries),
        perpendicularPassed = fields.advisoryCodedAt(PERPENDICULAR_PASSED, DataStatus.entries),
        arrivalCircleRadius = fields.doubleAt(RADIUS),
        waypointId = fields.stringAt(WAYPOINT_ID),
      )
  }
}

/**
 * The waypoints of the active route, by name, in order.
 *
 * Example: `$GPR00,MELIN,RUSKI,KNUDAN*hh`
 *
 * A Garmin sentence that gpsd documents alongside the standard ones. It is [Rte] with everything
 * but the names removed -- no sentence numbering, no route id, no complete-or-working flag -- so a
 * route too long for one sentence has nowhere to say so.
 *
 * @property waypointIds the names, in the order the route visits them
 */
public data class R00(override val talker: TalkerId, val waypointIds: List<String> = emptyList()) :
  Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = buildNmea(talker, ID, waypointIds)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "R00"

    /** Reads an R00 sentence from its fields. */
    public fun from(fields: SentenceFields): R00 =
      R00(talker = fields.talker, waypointIds = List(fields.size) { fields.stringAt(it).orEmpty() })
  }
}

/** The eight fields [Bwc] and [Bwr] share, read once. */
internal class BearingToWaypoint(
  val time: LocalTime?,
  val waypointPosition: Position?,
  val bearingTrue: Double?,
  val bearingMagnetic: Double?,
  val distance: Double?,
  val waypointId: String?,
  val faaMode: FaaMode?,
)

/**
 * Reads the fields of a bearing-and-distance-to-waypoint sentence.
 *
 * [Bwc] and [Bwr] have identical layouts and differ only in whether the bearing describes a great
 * circle or a rhumb line, so the reading is shared and the meaning is not.
 */
internal fun SentenceFields.bearingToWaypointAt(): BearingToWaypoint =
  BearingToWaypoint(
    time = timeAt(0),
    waypointPosition = positionAt(1, 2, 3, 4),
    bearingTrue = doubleAt(5),
    bearingMagnetic = doubleAt(7),
    distance = doubleAt(9),
    waypointId = stringAt(11),
    faaMode = advisoryCodedAt(12, FaaMode.entries),
  )

/** The same eight fields, on the way back out. */
internal fun Bwr.asBearingFields(): BearingToWaypoint =
  BearingToWaypoint(
    time,
    waypointPosition,
    bearingTrue,
    bearingMagnetic,
    distanceNauticalMiles,
    waypointId,
    faaMode,
  )

/** Renders a bearing-and-distance-to-waypoint sentence. */
internal fun bearingToWaypointNmea(
  talker: TalkerId,
  id: String,
  fields: BearingToWaypoint,
): String =
  buildNmea(
    talker,
    id,
    listOf(fields.time?.let { NmeaDateTime.formatTime(it) }) +
      positionFields(fields.waypointPosition) +
      listOf(
        fields.bearingTrue.field(),
        "T",
        fields.bearingMagnetic.field(),
        "M",
        fields.distance.field(),
        "N",
        fields.waypointId,
        fields.faaMode.field(),
      ),
  )
