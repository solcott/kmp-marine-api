package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.GpsFixStatus
import io.github.solcott.marineapi.nmea.SatelliteInfo
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.sentence.Gsa
import io.github.solcott.marineapi.nmea.sentence.Gsv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Every satellite one constellation had in view during an update cycle, with the fixes that used
 * them.
 *
 * A `GSV` sentence carries at most four satellites, so a receiver tracking twelve sends three of
 * them and the full picture only exists once the group is complete. See [satellites].
 *
 * @property talker the constellation this view describes -- [TalkerId.GP] for GPS, [TalkerId.GL]
 *   for GLONASS, [TalkerId.GA] for Galileo and so on. A multi-constellation receiver reports each
 *   separately, so it produces one of these per constellation per cycle.
 * @property satellitesInView how many the receiver *says* it can see, which is neither an upper nor
 *   a lower bound on `satellites.size`. The group is numbered by sentence, not by satellite, so a
 *   receiver seeing more than its sentences have room for reports fewer than this; and a Magellan
 *   eXplorist 210 in the conformance corpus says ten and then lists twelve. Reassembly counts
 *   sentences and pays this field no attention, which is why that device reassembles at all.
 * @property satellites the satellites themselves, gathered from the whole group
 * @property fixes the `GSA` sentences from this cycle, empty if the receiver sent none. A list
 *   rather than a single sentence because a multi-constellation receiver sends one per
 *   constellation and the implementation this replaces kept only the last of them.
 */
public data class SatelliteView(
  val talker: TalkerId,
  val satellitesInView: Int? = null,
  val satellites: List<SatelliteInfo> = emptyList(),
  val fixes: List<Gsa> = emptyList(),
) {

  /** Ids of the satellites the fixes actually used, as opposed to merely saw. */
  public val satellitesUsed: List<String>
    get() = fixes.flatMap { it.satellitesUsed }.distinct()

  /** Whether the fix was two- or three-dimensional, from the first fix that reports it. */
  public val fixStatus: GpsFixStatus?
    get() = fixes.firstNotNullOfOrNull { it.fixStatus }

  /** Overall dilution of precision, from the first fix that reports it. */
  public val positionDop: Double?
    get() = fixes.firstNotNullOfOrNull { it.positionDop }

  /** Horizontal dilution of precision, from the first fix that reports it. */
  public val horizontalDop: Double?
    get() = fixes.firstNotNullOfOrNull { it.horizontalDop }

  /** Vertical dilution of precision, from the first fix that reports it. */
  public val verticalDop: Double?
    get() = fixes.firstNotNullOfOrNull { it.verticalDop }
}

/**
 * Reassembles each complete group of `GSV` sentences into a single [SatelliteView].
 *
 * ```
 * source.nmeaSentences()
 *   .satellites()
 *   .collect { view -> skyPlot.show(view.talker, view.satellites) }
 * ```
 *
 * Groups are tracked per talker, so a multi-constellation receiver interleaving `$GPGSV` and
 * `$GLGSV` yields one view for each rather than one confused view of both -- the implementation
 * this replaces counted every `GSV` together regardless of who sent it, and a second constellation
 * was enough to stop it reporting anything at all.
 *
 * A group is emitted when its sentences have arrived in order and all of them are present. One that
 * is interrupted, arrives out of order or restarts partway through is discarded rather than
 * patched: the satellites in a group are only meaningfully one view of the sky if they came from
 * one sweep of it.
 *
 * `GSA` is collected alongside and attached to the views that follow it, but is **not** required.
 * The predecessor reported nothing without one, which discards the satellites in view from every
 * receiver that reports them without also reporting a fix -- including one that is still searching,
 * which is exactly when knowing what it can see is worth something.
 */
public fun Flow<Sentence>.satellites(): Flow<SatelliteView> = flow {
  val cycle = SatelliteCycle()
  collect { sentence -> cycle.add(sentence)?.let { emit(it) } }
}

/** The `GSV` groups in progress, one per talker, and the `GSA` sentences to attach to them. */
private class SatelliteCycle {

  private val groups = mutableMapOf<TalkerId, MutableList<Gsv>>()
  private val fixes = mutableListOf<Gsa>()

  /**
   * Set once a view has been reported, so the next `GSA` starts a fresh set instead of piling on.
   */
  private var fixesAreSpent = false

  fun add(sentence: Sentence): SatelliteView? =
    when (sentence) {
      is Gsa -> {
        if (fixesAreSpent) {
          fixes.clear()
          fixesAreSpent = false
        }
        fixes += sentence
        null
      }
      is Gsv -> addToGroup(sentence)
      else -> null
    }

  private fun addToGroup(gsv: Gsv): SatelliteView? {
    val count = gsv.sentenceCount
    val index = gsv.sentenceIndex
    if (count == null || index == null || index < 1 || index > count) {
      // A group that cannot say where in itself this sentence belongs cannot be reassembled.
      groups.remove(gsv.talker)
      return null
    }

    val group =
      if (index == 1) {
        mutableListOf(gsv).also { groups[gsv.talker] = it }
      } else {
        val started = groups[gsv.talker]
        if (
          started == null || started.size != index - 1 || started.first().sentenceCount != count
        ) {
          groups.remove(gsv.talker)
          return null
        }
        started.also { it += gsv }
      }

    if (group.size < count) return null
    groups.remove(gsv.talker)
    fixesAreSpent = true
    return SatelliteView(
      talker = gsv.talker,
      satellitesInView = group.first().satellitesInView,
      satellites = group.flatMap { it.satellites },
      fixes = fixes.toList(),
    )
  }
}
