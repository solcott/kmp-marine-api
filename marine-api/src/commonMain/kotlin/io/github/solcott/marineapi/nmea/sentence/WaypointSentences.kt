package io.github.solcott.marineapi.nmea.sentence

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
