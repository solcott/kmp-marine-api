package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.IntCoded
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/*
 * Sentences from a trawl's net-monitoring sensors: where the net is, how wide it is open, and
 * whether it has caught anything.
 *
 * Two things about the whole group, rather than repeated on each type.
 *
 * gpsd sources all seven from a single GLOBALSAT document, where they are "shown with a '@II'
 * leader rather than '$GP'". This library reads them as ordinary '$' sentences: NMEA defines '$'
 * and '!' as the only start delimiters, Nmea.isBeginChar accepts those two, and no capture in this
 * project's corpus uses '@'. If a device really does send an '@' leader these will not parse, and
 * that is a deliberate choice rather than an oversight.
 *
 * They are also the most weakly attested types here. There is no device data for any of them, and
 * gpsd's tables rest on one vendor document rather than on the standard.
 */

/** Whether a catch sensor on the net has been triggered. */
public enum class CatchStatus(override val code: Int) : IntCoded {
  /** Not triggered: nothing in the net at that point yet. */
  OFF(0),
  /** Triggered. */
  ON(1),
  /** The sensor did not answer, which is not the same as saying it is off. */
  NO_ANSWER(2),
}

/**
 * How far the trawl doors are apart: the width of the net's mouth.
 *
 * Example: `$IITDS,120.5,M*hh`
 *
 * The doors are the vanes that hold the net open as it is towed. Their spread is the single number
 * that says whether the net is fishing properly or has collapsed.
 *
 * @property spread distance between the doors, metres, 0 to 300
 */
public data class Tds(override val talker: TalkerId, val spread: Double? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(spread.field(), METRES_MARKER.toString()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TDS"

    private const val METRES_MARKER = 'M'
    private const val SPREAD = 0

    /** Reads a TDS sentence from its fields. */
    public fun from(fields: SentenceFields): Tds =
      Tds(talker = fields.talker, spread = fields.doubleAt(SPREAD))
  }
}

/**
 * A second door-spread measurement, from a second pair of sensors.
 *
 * Example: `$IIITS,118.2,M*hh`
 *
 * @property spread the second spread distance, metres
 */
public data class Its(override val talker: TalkerId, val spread: Double? = null) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(talker, ID, listOf(spread.field(), METRES_MARKER.toString()))

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "ITS"

    private const val METRES_MARKER = 'M'
    private const val SPREAD = 0

    /** Reads an ITS sentence from its fields. */
    public fun from(fields: SentenceFields): Its =
      Its(talker = fields.talker, spread = fields.doubleAt(SPREAD))
  }
}

/**
 * How far the net's mouth is open vertically, and how far its headrope is off the bottom.
 *
 * Example: `$IIHFB,12.5,M,3.2,M*hh`
 *
 * The headrope is the top edge of the net's mouth and the footrope the bottom. Together with
 * [Tds]'s door spread these give the mouth's full size; [headropeToBottom] is what says whether the
 * gear is about to be dragged through the seabed.
 *
 * @property headropeToFootrope vertical opening of the net's mouth, metres, 0 to 100
 * @property headropeToBottom clearance from the headrope down to the seabed, metres, 0 to 100
 */
public data class Hfb(
  override val talker: TalkerId,
  val headropeToFootrope: Double? = null,
  val headropeToBottom: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        headropeToFootrope.field(),
        METRES_MARKER.toString(),
        headropeToBottom.field(),
        METRES_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "HFB"

    private const val METRES_MARKER = 'M'
    private const val TO_FOOTROPE = 0
    private const val TO_BOTTOM = 2

    /** Reads an HFB sentence from its fields. */
    public fun from(fields: SentenceFields): Hfb =
      Hfb(
        talker = fields.talker,
        headropeToFootrope = fields.doubleAt(TO_FOOTROPE),
        headropeToBottom = fields.doubleAt(TO_BOTTOM),
      )
  }
}

/**
 * Whether the net is filling, from up to three catch sensors along its length.
 *
 * Example: `$IITFI,1,0,0*hh`
 *
 * The sensors sit at intervals down the net and trip as the catch reaches them, so the pattern says
 * roughly how full it is. A sensor that fails to answer reports [CatchStatus.NO_ANSWER], which is
 * deliberately not the same as reporting empty.
 *
 * @property sensors the three catch sensors, in order down the net
 */
public data class Tfi(
  override val talker: TalkerId,
  val sensors: List<CatchStatus?> = emptyList(),
) : Sentence {

  init {
    require(sensors.size <= SENSOR_COUNT) {
      "TFI carries at most $SENSOR_COUNT catch sensors, got ${sensors.size}"
    }
  }

  override val id: String
    get() = ID

  /** True when any sensor has tripped. */
  public val isFilling: Boolean
    get() = sensors.any { it == CatchStatus.ON }

  override fun toNmeaString(): String =
    buildNmea(talker, ID, List(SENSOR_COUNT) { sensors.getOrNull(it)?.code?.toString() })

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TFI"

    /** Catch sensors one TFI sentence describes. */
    public const val SENSOR_COUNT: Int = 3

    /** Reads a TFI sentence from its fields. */
    public fun from(fields: SentenceFields): Tfi =
      Tfi(
        talker = fields.talker,
        sensors = List(SENSOR_COUNT) { fields.advisoryIntCodedAt(it, CatchStatus.entries) },
      )
  }
}

/**
 * Where the trawl is, as offsets from the vessel.
 *
 * Example: `$IITPC,12.5,M,180.3,M,55.0,M*hh`
 *
 * Cartesian rather than polar: how far the net is off to one side, how far astern, and how deep.
 * [Tpr] and [Tpt] give the same position as a range and a bearing.
 *
 * @property offCentreLine horizontal distance from the vessel's centre line, metres
 * @property alongCentreLine horizontal distance from the transducer to the trawl along that line,
 *   metres
 * @property depth depth of the trawl below the surface, metres
 */
public data class Tpc(
  override val talker: TalkerId,
  val offCentreLine: Double? = null,
  val alongCentreLine: Double? = null,
  val depth: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        offCentreLine.field(),
        METRES_MARKER.toString(),
        alongCentreLine.field(),
        METRES_MARKER.toString(),
        depth.field(),
        METRES_MARKER.toString(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TPC"

    private const val METRES_MARKER = 'M'
    private const val OFF_CENTRE = 0
    private const val ALONG_CENTRE = 2
    private const val DEPTH = 4

    /** Reads a TPC sentence from its fields. */
    public fun from(fields: SentenceFields): Tpc =
      Tpc(
        talker = fields.talker,
        offCentreLine = fields.doubleAt(OFF_CENTRE),
        alongCentreLine = fields.doubleAt(ALONG_CENTRE),
        depth = fields.doubleAt(DEPTH),
      )
  }
}

/**
 * Where the trawl is, as a range and a bearing **relative to the vessel's heading**.
 *
 * Example: `$IITPR,120.5,M,15.3,P,55.0,M*hh`
 *
 * The bearing is measured from the bow, so it moves with the vessel. [Tpt] gives the same position
 * bearing from true north instead.
 *
 * @property range horizontal range to the trawl, metres, 0 to 4000
 * @property bearing bearing to the trawl relative to the vessel's heading, degrees
 * @property depth depth of the trawl below the surface, metres, 0 to 2000
 */
public data class Tpr(
  override val talker: TalkerId,
  val range: Double? = null,
  val bearing: Double? = null,
  val depth: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = trawlPositionFields(talker, ID, range, bearing, depth)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TPR"

    /** Reads a TPR sentence from its fields. */
    public fun from(fields: SentenceFields): Tpr =
      with(fields.trawlPositionAt()) { Tpr(fields.talker, range, bearing, depth) }
  }
}

/**
 * Where the trawl is, as a range and a **true** bearing.
 *
 * Example: `$IITPT,120.5,M,225.7,P,55.0,M*hh`
 *
 * [Tpr] with the bearing referred to north rather than to the bow, so it stays put as the vessel
 * turns.
 *
 * @property range horizontal range to the trawl, metres, 0 to 4000
 * @property bearing true bearing to the trawl, degrees from north
 * @property depth depth of the trawl below the surface, metres, 0 to 2000
 */
public data class Tpt(
  override val talker: TalkerId,
  val range: Double? = null,
  val bearing: Double? = null,
  val depth: Double? = null,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String = trawlPositionFields(talker, ID, range, bearing, depth)

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "TPT"

    /** Reads a TPT sentence from its fields. */
    public fun from(fields: SentenceFields): Tpt =
      with(fields.trawlPositionAt()) { Tpt(fields.talker, range, bearing, depth) }
  }
}

/** The three values TPR and TPT both carry. */
internal class TrawlPosition(val range: Double?, val bearing: Double?, val depth: Double?)

/**
 * Reads the six fields TPR and TPT share.
 *
 * Field 4 is what gpsd's table calls a "Separator" rather than a unit marker, which is how a
 * bearing ends up flanked by metre markers on either side. It is written back as the `P` the vendor
 * document shows.
 */
internal fun SentenceFields.trawlPositionAt(): TrawlPosition =
  TrawlPosition(range = doubleAt(0), bearing = doubleAt(2), depth = doubleAt(4))

/** The same six fields on the way out. */
internal fun trawlPositionFields(
  talker: TalkerId,
  id: String,
  range: Double?,
  bearing: Double?,
  depth: Double?,
): String =
  buildNmea(talker, id, listOf(range.field(), "M", bearing.field(), "P", depth.field(), "M"))
