package io.github.solcott.marineapi.nav.ais

import io.github.solcott.marineapi.ais.AisAidToNavigationReport
import io.github.solcott.marineapi.ais.AisMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance

/**
 * The navigation aids reporting that they are not where they should be.
 *
 * ```
 * source.nmeaSentences().aisMessages().messages().offPositionAids().collect { warn(it.name) }
 * ```
 *
 * A lit buoy that has dragged its mooring has stopped being a mark and started being a hazard: it
 * is unlit-chart-position now, it may be in the channel, and anything that took a bearing off it is
 * wrong. This is the flag with which it says so, and it is the reason a type 21 message is worth
 * decoding at all on a vessel that is not a chart plotter.
 *
 * A *virtual* aid -- one broadcast by a shore station with no physical mark in the water, used to
 * mark a new wreck before a buoy can be laid -- cannot drift, and the flag is not meaningful on
 * one. Filter [AisAidToNavigationReport.isVirtual] out if that distinction matters; it is left in
 * here because a virtual aid asserting it is off position is a misconfigured shore station, which
 * is itself worth seeing.
 *
 * The general case needs no operator: `filterIsInstance<AisAidToNavigationReport>()` gives every
 * aid the feed carries.
 */
public fun Flow<AisMessage>.offPositionAids(): Flow<AisAidToNavigationReport> =
  filterIsInstance<AisAidToNavigationReport>().filter { it.isOffPosition }
