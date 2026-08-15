package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.CharCoded
import io.github.solcott.marineapi.nmea.GpsFixStatus
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/** How a receiver chose between two- and three-dimensional operation. */
public enum class FixSelection(override val code: Char) : CharCoded {
  /** Forced by the operator to 2D or 3D. */
  MANUAL('M'),
  /** Switched automatically between 2D and 3D. */
  AUTOMATIC('A'),
}

/**
 * Dilution of precision and the satellites used for the active fix.
 *
 * Example: `$GPGSA,A,3,03,05,07,08,10,15,18,19,21,28,,,1.4,0.9,1.1*3A`
 *
 * @property selection whether 2D/3D was chosen manually or automatically
 * @property fixStatus dimensionality of the fix
 * @property satelliteIds the sentence's twelve satellite slots in order, `null` where a slot is
 *   empty. The positions are kept rather than compacted because receivers leave interior gaps --
 *   `,,05,,08,,,18` is one channel per slot, and 920 lines of this project's sample logs look like
 *   that. Compacting them moves satellites between channels and rewrites the sentence. Use
 *   [satellitesUsed] for just the ones present.
 * @property positionDop position (3D) dilution of precision
 * @property horizontalDop horizontal dilution of precision
 * @property verticalDop vertical dilution of precision
 * @property systemId GNSS system this fix came from, added in NMEA 4.11 and absent from older
 *   devices. The Java implementation did not read this field.
 */
public data class Gsa(
  override val talker: TalkerId,
  val selection: FixSelection? = null,
  val fixStatus: GpsFixStatus? = null,
  val satelliteIds: List<String?> = emptyList(),
  val positionDop: Double? = null,
  val horizontalDop: Double? = null,
  val verticalDop: Double? = null,
  val systemId: Int? = null,
) : Sentence {

  init {
    require(satelliteIds.size <= SATELLITE_SLOTS) {
      "GSA carries at most $SATELLITE_SLOTS satellites, got ${satelliteIds.size}"
    }
  }

  override val id: String
    get() = ID

  /** The satellites this fix actually used, with the empty slots left out. */
  public val satellitesUsed: List<String>
    get() = satelliteIds.filterNotNull()

  override fun toNmeaString(): String {
    val slots = List(SATELLITE_SLOTS) { satelliteIds.getOrNull(it) }
    return buildNmea(
      talker,
      ID,
      buildList {
        add(selection.field())
        add(fixStatus.field())
        addAll(slots)
        add(positionDop.field())
        add(horizontalDop.field())
        add(verticalDop.field())
        if (systemId != null) add(systemId.toString())
      },
    )
  }

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "GSA"

    /** Satellite slots the sentence always carries, empty ones included. */
    public const val SATELLITE_SLOTS: Int = 12

    private const val SELECTION = 0
    private const val FIX_STATUS = 1
    private const val FIRST_SATELLITE = 2
    private const val POSITION_DOP = 14
    private const val HORIZONTAL_DOP = 15
    private const val VERTICAL_DOP = 16
    private const val SYSTEM_ID = 17

    /** Reads a GSA sentence from its fields. */
    public fun from(fields: SentenceFields): Gsa =
      Gsa(
        talker = fields.talker,
        selection = fields.advisoryCodedAt(SELECTION, FixSelection.entries),
        fixStatus = fields.advisoryIntCodedAt(FIX_STATUS, GpsFixStatus.entries),
        satelliteIds =
          (FIRST_SATELLITE until FIRST_SATELLITE + SATELLITE_SLOTS).map(fields::stringAt),
        positionDop = fields.doubleAt(POSITION_DOP),
        horizontalDop = fields.doubleAt(HORIZONTAL_DOP),
        verticalDop = fields.doubleAt(VERTICAL_DOP),
        systemId = fields.advisoryIntAt(SYSTEM_ID),
      )
  }
}
