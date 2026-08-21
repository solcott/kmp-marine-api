package io.github.solcott.marineapi.example

import io.github.solcott.marineapi.nmea.Nmea
import io.github.solcott.marineapi.nmea.ParseResult

/**
 * A tally of everything on a feed that did not become a sentence, split by whether it ever claimed
 * to be one.
 *
 * `nmeaResults()` reports every line but a blank one, and a demo that counts all of them as
 * failures is adding up two unrelated things. **A line that never claimed to be a sentence is not
 * corruption.** A `#` header is the ordinary convention in a capture rather than anything unusual
 * -- gpsd's own corpus logs carry them, thirteen in `ait250.log` -- and three of those captures
 * interleave JSON control records with the NMEA. What deserves alarm is a line that opens with `$`
 * or `!`, promising a sentence, and then fails to parse: a checksum that does not match, a
 * truncated read, two sentences spliced together by a serial capture.
 *
 * Every corpus test in `:marine-api` already draws this line with `Nmea.isBeginChar` before
 * counting anything. `CorpusFlowTest` draws it twice over and pins both halves -- 120 corrupt
 * sentences against 5 lines that were never NMEA -- which is the distinction reproduced here.
 *
 * Getting it wrong is not academic. Before this existed the demos turned a log's two-line
 * provenance header into two phantom transmission errors, so the same AIS capture read in a browser
 * reported three failures where the JVM reported one.
 */
internal class FeedNoise {
  private var corrupt = 0
  private var notNmea = 0

  /** Records one result. Anything that parsed is not noise, and is ignored. */
  fun record(result: ParseResult) {
    if (result is ParseResult.Ok) return
    // Trimmed first: one capture in the corpus carries a sentence with a stray leading space, and
    // that is a damaged sentence rather than a line that was never one.
    val first = result.line.trim().firstOrNull()
    if (first != null && Nmea.isBeginChar(first)) corrupt++ else notNmea++
  }

  /** Prints what the feed carried, and nothing at all when it carried nothing but sentences. */
  fun report() {
    when {
      corrupt == 1 -> println("(1 sentence arrived corrupt)")
      corrupt > 1 -> println("($corrupt sentences arrived corrupt)")
    }
    when {
      notNmea == 1 -> println("(1 line was not NMEA at all)")
      notNmea > 1 -> println("($notNmea lines were not NMEA at all)")
    }
  }
}
