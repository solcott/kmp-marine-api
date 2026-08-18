package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.BearingReference
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.sentence.Hdg
import io.github.solcott.marineapi.nmea.sentence.Hdm
import io.github.solcott.marineapi.nmea.sentence.Hdt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Which way the vessel is pointing, and what north that is measured from.
 *
 * @property degrees heading, 0 to 360
 * @property reference [BearingReference.TRUE] or [BearingReference.MAGNETIC]. Worth reading rather
 *   than assuming: the difference between the two is the local magnetic variation, tens of degrees
 *   in the high latitudes.
 */
public data class Heading(val degrees: Double, val reference: BearingReference)

/**
 * The heading a sentence reports, or `null` if it reports none.
 *
 * `HDT` is true; `HDM` and `HDG` are magnetic, `HDG` being the raw sensor reading that its own
 * deviation and variation fields exist to correct. Any other sentence type, and any of these three
 * with an empty heading field, gives `null`.
 */
public fun Sentence.headingOrNull(): Heading? =
  when (this) {
    is Hdt -> heading?.let { Heading(it, BearingReference.TRUE) }
    is Hdm -> heading?.let { Heading(it, BearingReference.MAGNETIC) }
    is Hdg -> heading?.let { Heading(it, BearingReference.MAGNETIC) }
    else -> null
  }

/**
 * The heading from every `HDT`, `HDM` and `HDG` in this flow.
 *
 * ```
 * source.nmeaSentences().headings().collect { autopilot.steer(it) }
 * ```
 *
 * Unlike [positions] there is no cycle to correlate: each of these sentences carries a complete
 * heading on its own, so one sentence yields one [Heading]. A vessel carrying both a gyro and a
 * magnetic compass emits `HDT` and `HDM` in the same cycle and so produces two headings from it,
 * distinguished by their [Heading.reference].
 */
public fun Flow<Sentence>.headings(): Flow<Heading> = mapNotNull { it.headingOrNull() }
