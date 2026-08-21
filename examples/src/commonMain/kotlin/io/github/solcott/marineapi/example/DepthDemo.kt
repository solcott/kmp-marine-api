package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nav.instrument.DepthReference
import io.github.solcott.marineapi.nav.instrument.depths
import io.github.solcott.marineapi.nav.instrument.shallowerThan
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import kotlinx.coroutines.flow.toList
import kotlinx.io.Source

/** Where the alarm sounds, in metres below the transducer. */
private const val ALARM_METRES = 4.0

/**
 * Soundings, and the reason a depth means nothing until you say what it is measured from.
 *
 * Try it on `marine-api/src/jvmTest/resources/data/sample1.txt`, whose sounder sends `DBT` (below
 * the transducer) and `DPT` (below the transducer, plus the offset to another datum).
 *
 * The interesting output is the empty one. Ask this feed for the depth below the **keel** and it
 * yields nothing at all: `DBT` has no idea where the keel is, and this `DPT`'s offset is positive,
 * which measures *up* to the waterline rather than down to the keel. `depths` will not split the
 * difference, and that is deliberate -- 4 metres below the transducer on a boat drawing 2 is a
 * comfortable margin, and the same 4 metres read as being below the keel while it is really below
 * the waterline is a grounding. This is one of the few places in the library where the honest
 * answer is no answer, so `depths` takes its datum as a required argument with no default.
 */
suspend fun demoDepth(open: () -> Source) {
  for (reference in DepthReference.entries) {
    println("-- below the ${reference.name.lowercase()} --")
    val soundings = open().nmeaSentences().depths(reference).toList()
    if (soundings.isEmpty()) {
      println("  nothing in this feed can answer that")
      continue
    }
    // Feet and fathoms are conversions of the same reading, not different readings. Changing the
    // unit is always safe; changing the datum is the thing that is not.
    for (depth in soundings) {
      println(
        "  ${depth.metres.toHundredths()} m" +
          "   ${depth.feet.toHundredths()} ft" +
          "   ${depth.fathoms.toHundredths()} fathoms"
      )
    }
  }

  val pings = open().nmeaSentences().depths(DepthReference.TRANSDUCER).toList().size
  println()
  // Formatted rather than interpolated: Kotlin/JS renders the Double 4.0 as "4", so a bare
  // $ALARM_METRES makes the same demo print differently on Node than it does on the JVM.
  println("-- alarm at ${ALARM_METRES.toHundredths()} m below the transducer --")
  var changes = 0
  open()
    .nmeaSentences()
    .depths(DepthReference.TRANSDUCER)
    .shallowerThan(metres = ALARM_METRES)
    .collect { alarm ->
      changes++
      val state = if (alarm.isShallow) "SHALLOW" else "clear"
      println("  $state at ${alarm.depth.metres.toHundredths()} m")
    }

  // Once it has sounded the water has to reach 4.8 m -- 20% deeper -- before it stops. Without
  // that gap an echo sounder over an uneven bottom, or a chop lifting the transducer, chatters
  // across the threshold ping after ping, and an alarm that chatters gets switched off.
  if (pings == 0) println("  no soundings below the transducer in this feed")
  else println("  ($changes changes of state out of $pings soundings)")
}
