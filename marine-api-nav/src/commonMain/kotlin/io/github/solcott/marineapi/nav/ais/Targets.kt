package io.github.solcott.marineapi.nav.ais

import io.github.solcott.marineapi.ais.AisMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Every AIS target the feed has described, fused by MMSI and emitted as each one changes.
 *
 * ```
 * source.nmeaSentences()
 *   .aisMessages()
 *   .messages()
 *   .aisTargets()
 *   .collect { vessel -> plot(vessel.name ?: vessel.mmsi.toString(), vessel.position) }
 * ```
 *
 * One emission per message that says something about a vessel, carrying her whole accumulated
 * picture rather than the increment -- so the type 5 static report that arrives six minutes after
 * she was first seen emits her name *together with* the position from thirty seconds ago. Messages
 * that describe something other than a vessel pass through without emitting: a base station (type
 * 4), a search-and-rescue aircraft (type 9) and a navigation aid (type 21) each have their own
 * message class, and folding them into [Vessel] would put buoys on a collision-avoidance display.
 * See [offPositionAids] for the type 21 case that matters most.
 *
 * **[expireAfter] bounds the memory as much as it bounds the staleness.** A busy waterway puts
 * thousands of distinct MMSIs through a receiver in a day and a long passage puts through tens of
 * thousands, so a registry that never forgets grows without limit. It also stops a name learned
 * hours ago being attached to a position report from a different vessel that has since been issued
 * the same identity, which happens with the cheaper transponders.
 *
 * Age is measured with [timeSource], which is elapsed time and not wall-clock time -- expiry should
 * not move because the host synchronised its clock. Replaying a log runs far faster than the feed
 * did, so nothing expires during a replay; that is usually the right answer for a log, and where it
 * is not, pass a [TimeSource] driven by the feed's own timestamps.
 *
 * @param expireAfter how long a target is kept after her last report
 * @param timeSource where elapsed time comes from; pass a fake one to test expiry
 */
public fun Flow<AisMessage>.aisTargets(
  expireAfter: Duration = TargetRegistry.DEFAULT_EXPIRY,
  timeSource: TimeSource = TimeSource.Monotonic,
): Flow<Vessel> = aisTargets(TargetRegistry(expireAfter, timeSource))

/**
 * The same, into a [registry] the caller holds, so the whole current plot can be read as well as
 * the changes to it.
 *
 * ```
 * val traffic = TargetRegistry()
 * scope.launch { source.nmeaSentences().aisMessages().messages().aisTargets(traffic).collect { … } }
 * // elsewhere, on the same dispatcher: traffic.vessels
 * ```
 *
 * **The registry is not thread-safe**, and this fills it from whichever coroutine collects. Read it
 * from there too, or from a dispatcher confined to a single thread; a chart that reads it from a UI
 * thread while a background one writes will see a torn picture on the platforms that have threads
 * at all.
 */
public fun Flow<AisMessage>.aisTargets(registry: TargetRegistry): Flow<Vessel> = flow {
  collect { message -> registry.update(message)?.let { emit(it) } }
}

/**
 * The current AIS picture: every target still reporting, fused from her messages and keyed by MMSI.
 *
 * The state behind [aisTargets], exposed for the callers that need the whole plot rather than a
 * stream of changes -- a chart drawing every target in view has to iterate them, and re-emitting a
 * list of some thousands of vessels on every one of the dozens of messages a second a busy VHF
 * channel carries would be most of the work the process does.
 *
 * **Not thread-safe.** Confine it to the coroutine that collects the flow feeding it.
 *
 * Targets older than [expireAfter] are excluded from every read, and are actually dropped by a
 * sweep that runs no more often than once per [expireAfter] -- so the memory held is bounded by
 * what the feed reported in the last two expiry periods, while what is *reported* is never stale.
 * [expire] forces the sweep and names what went, which is what a display needs in order to rub the
 * targets out.
 *
 * @param expireAfter how long a target is kept after her last report
 * @param timeSource where elapsed time comes from; pass a fake one to test expiry
 */
public class TargetRegistry(
  public val expireAfter: Duration = DEFAULT_EXPIRY,
  private val timeSource: TimeSource = TimeSource.Monotonic,
) {

  private class Tracked(var vessel: Vessel, var seen: TimeMark)

  private val tracked = mutableMapOf<Mmsi, Tracked>()
  private var lastSweep: TimeMark = timeSource.markNow()

  init {
    require(expireAfter > Duration.ZERO) { "expireAfter must be positive, was $expireAfter" }
  }

  /**
   * Folds [message] into the target it describes and returns her, or `null` if it describes no
   * vessel.
   *
   * A message from a target whose entry has already expired starts a fresh one rather than reviving
   * the old: the point of the expiry is that what was known then is no longer to be trusted now.
   */
  public fun update(message: AisMessage): Vessel? {
    if (!message.describesAVessel()) return null
    sweepIfDue()

    val mmsi = Mmsi(message.mmsi)
    val existing = tracked[mmsi]?.takeIf { !it.hasExpired() }
    val updated = (existing?.vessel ?: Vessel(mmsi)).updatedBy(message)
    tracked[mmsi] = Tracked(updated, timeSource.markNow())
    return updated
  }

  /** The target with this identity, or `null` if she is unknown or has aged out. */
  public operator fun get(mmsi: Mmsi): Vessel? = tracked[mmsi]?.takeIf { !it.hasExpired() }?.vessel

  /**
   * How long since this target last reported, or `null` if she is unknown or has aged out.
   *
   * The counterpart to [Vessel] carrying no timestamp of its own. A target seen four minutes ago is
   * still on the plot and is no longer where the plot says she is.
   */
  public fun ageOf(mmsi: Mmsi): Duration? =
    tracked[mmsi]?.takeIf { !it.hasExpired() }?.seen?.elapsedNow()

  /** Every target still reporting, in no particular order. */
  public val vessels: List<Vessel>
    get() = tracked.values.filter { !it.hasExpired() }.map { it.vessel }

  /** How many targets are still reporting. */
  public val size: Int
    get() = tracked.values.count { !it.hasExpired() }

  /**
   * Drops the targets that have aged out and names them, so a display can rub them off.
   *
   * Safe to call as often as a repaint: it is the only thing that walks the whole map, and the
   * reads above filter without it.
   */
  public fun expire(): List<Mmsi> {
    lastSweep = timeSource.markNow()
    val gone = tracked.filterValues { it.hasExpired() }.keys.toList()
    gone.forEach { tracked.remove(it) }
    return gone
  }

  /** Forgets every target. */
  public fun clear() {
    tracked.clear()
  }

  private fun sweepIfDue() {
    if (lastSweep.elapsedNow() >= expireAfter) expire()
  }

  private fun Tracked.hasExpired(): Boolean = seen.elapsedNow() > expireAfter

  public companion object {
    /**
     * Ten minutes, which is a little longer than the slowest schedule AIS transmits on.
     *
     * A Class A vessel sends her static report every 6 minutes and a Class B unit at anchor sends a
     * position every 3, so a target silent for ten has stopped transmitting, gone out of range, or
     * switched off -- all of which mean the same thing to a plot.
     */
    public val DEFAULT_EXPIRY: Duration = 10.minutes
  }
}
