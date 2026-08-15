package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceFields
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.buildNmea
import io.github.solcott.marineapi.nmea.field

/**
 * Track made good and ground speed.
 *
 * Example: `$GPVTG,360.0,T,348.7,M,000.0,N,000.0,K,A*3D`
 *
 * NMEA 3.01 introduced the form with the `T`, `M`, `N` and `K` marker fields; before that the
 * sentence was four bare numbers. The two are told apart by whether field 1 holds the literal `T`,
 * and [isLegacyFormat] reports which one arrived. Only the modern form is written back out.
 *
 * @property courseTrue course over ground, degrees true
 * @property courseMagnetic course over ground, degrees magnetic
 * @property speedKnots speed over ground, knots
 * @property speedKmh speed over ground, km/h
 * @property faaMode FAA mode indicator, added in NMEA 2.3
 */
public data class Vtg(
  override val talker: TalkerId,
  val courseTrue: Double? = null,
  val courseMagnetic: Double? = null,
  val speedKnots: Double? = null,
  val speedKmh: Double? = null,
  val faaMode: FaaMode? = null,
  val isLegacyFormat: Boolean = false,
) : Sentence {

  override val id: String
    get() = ID

  override fun toNmeaString(): String =
    buildNmea(
      talker,
      ID,
      listOf(
        courseTrue.field(),
        TRUE_MARKER.toString(),
        courseMagnetic.field(),
        MAGNETIC_MARKER.toString(),
        speedKnots.field(),
        KNOTS_MARKER.toString(),
        speedKmh.field(),
        KMH_MARKER.toString(),
        faaMode.field(),
      ),
    )

  public companion object {
    /** Sentence type code. */
    public const val ID: String = "VTG"

    private const val TRUE_MARKER = 'T'
    private const val MAGNETIC_MARKER = 'M'
    private const val KNOTS_MARKER = 'N'
    private const val KMH_MARKER = 'K'

    private const val COURSE_TRUE = 0
    private const val TRUE_INDICATOR = 1
    private const val COURSE_MAGNETIC = 2
    private const val SPEED_KNOTS = 4
    private const val SPEED_KMH = 6
    private const val FAA_MODE = 8

    // Positions in the pre-3.01 form, which carries no marker fields.
    private const val LEGACY_COURSE_MAGNETIC = 1
    private const val LEGACY_SPEED_KNOTS = 2
    private const val LEGACY_SPEED_KMH = 3

    /**
     * Fewest fields the modern form has: through the `K` marker, before the NMEA 2.3 FAA mode.
     *
     * The pre-3.01 form is four bare numbers, so anything this long is the modern one whatever its
     * marker fields say.
     */
    private const val MODERN_MINIMUM_FIELDS = 8

    /** Reads a VTG sentence from its fields, in either the modern or the pre-3.01 form. */
    public fun from(fields: SentenceFields): Vtg {
      // The `T` marker alone is not enough to tell the forms apart. A Bluetooth receiver in the
      // sample logs sends `$GPVTG,,,,,,,,,N` -- the modern nine-field form with every value empty,
      // markers included -- and reading that as the legacy form dropped its FAA mode and made the
      // sentence re-encode into the other format. Length settles it when the marker is absent.
      val legacy =
        fields.stringAt(TRUE_INDICATOR) != TRUE_MARKER.toString() &&
          fields.size < MODERN_MINIMUM_FIELDS
      return Vtg(
        talker = fields.talker,
        courseTrue = fields.doubleAt(COURSE_TRUE),
        courseMagnetic = fields.doubleAt(if (legacy) LEGACY_COURSE_MAGNETIC else COURSE_MAGNETIC),
        speedKnots = fields.doubleAt(if (legacy) LEGACY_SPEED_KNOTS else SPEED_KNOTS),
        speedKmh = fields.doubleAt(if (legacy) LEGACY_SPEED_KMH else SPEED_KMH),
        faaMode = if (legacy) null else fields.advisoryCodedAt(FAA_MODE, FaaMode.entries),
        isLegacyFormat = legacy,
      )
    }
  }
}
