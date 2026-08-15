package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.UnknownSentence
import io.github.solcott.marineapi.nmea.sentence.Gga
import io.github.solcott.marineapi.nmea.sentence.Rmc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString

private const val GGA = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
private const val RMC =
  "\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,A,S*74"
private const val GLL = "\$GPGLL,6011.552,N,02501.941,E,120045,A*26"

private fun sourceOf(text: String): Source = Buffer().also { it.writeString(text) }

class NmeaLineReaderTest {

  private fun linesOf(text: String): List<String> {
    val reader = NmeaLineReader(sourceOf(text))
    return buildList { while (true) add(reader.readLine() ?: break) }
  }

  @Test
  fun splitsOnCrLf() {
    assertEquals(listOf(GGA, RMC), linesOf("$GGA\r\n$RMC\r\n"))
  }

  @Test
  fun splitsOnLineFeedAlone() {
    assertEquals(listOf(GGA, RMC), linesOf("$GGA\n$RMC\n"))
  }

  @Test
  fun splitsOnCarriageReturnAlone() {
    // Several of the device logs in the conformance corpus are terminated this way throughout.
    assertEquals(listOf(GGA, RMC), linesOf("$GGA\r$RMC\r"))
  }

  @Test
  fun splitsAFileThatMixesBoth() {
    // At least one capture does this within a single file.
    assertEquals(listOf(GGA, RMC, GLL), linesOf("$GGA\r\n$RMC\r$GLL\n"))
  }

  @Test
  fun returnsTheLastLineWhenItHasNoTerminator() {
    assertEquals(listOf(GGA, RMC), linesOf("$GGA\r\n$RMC"))
  }

  @Test
  fun distinguishesAnEmptyLineFromTheSecondHalfOfCrLf() {
    // "\r\n\r\n" is two terminators, so one empty line between them -- not two.
    assertEquals(listOf(GGA, "", RMC), linesOf("$GGA\r\n\r\n$RMC\r\n"))
    assertEquals(listOf(GGA, "", RMC), linesOf("$GGA\n\n$RMC\n"))
  }

  @Test
  fun readsACrThatIsTheLastByteAvailable() {
    // The flag exists for this: on a live feed the CR of a CRLF can be the last byte read, with
    // the LF arriving only on the next read. Peeking ahead would block or misreport.
    val buffer = Buffer()
    val reader = NmeaLineReader(buffer)
    buffer.writeString("$GGA\r")
    assertEquals(GGA, reader.readLine())
    assertEquals(null, reader.readLine(), "nothing more has arrived yet")

    buffer.writeString("\n$RMC\r\n")
    assertEquals(RMC, reader.readLine(), "the stray LF belongs to the previous CR")
    assertEquals(null, reader.readLine())
  }

  @Test
  fun readsNothingFromAnEmptySource() {
    assertEquals(emptyList(), linesOf(""))
  }
}

class NmeaFlowTest {

  @Test
  fun readsEveryResultIncludingTheFailures() = runTest {
    val feed = "$GGA\r\n\$GPGGA,bad,checksum*00\r\n$RMC\r\n"
    val results = sourceOf(feed).nmeaResults().toList()
    assertEquals(3, results.size)
    assertIs<ParseResult.Ok>(results[0])
    assertIs<ParseResult.BadChecksum>(results[1])
    assertIs<ParseResult.Ok>(results[2])
  }

  @Test
  fun readsOnlyTheSentencesThatParsed() = runTest {
    val feed = "$GGA\r\n\$GPGGA,bad,checksum*00\r\n$RMC\r\n"
    val sentences = sourceOf(feed).nmeaSentences().toList()
    assertEquals(listOf("GGA", "RMC"), sentences.map { it.id })
  }

  @Test
  fun theTwoAgreeOnWhatParsed() {
    // nmeaSentences is nmeaResults with the failures dropped, and says so by construction.
    runTest {
      val feed = "$GGA\r\n\$GPGGA,bad,checksum*00\r\n$RMC\r\n$GLL\r\n"
      val viaResults = sourceOf(feed).nmeaResults().sentences().toList()
      val direct = sourceOf(feed).nmeaSentences().toList()
      assertEquals(viaResults, direct)
    }
  }

  @Test
  fun narrowsToOneSentenceTypeWithoutReflection() = runTest {
    // What replaces AbstractSentenceListener and its GenericTypeResolver.
    val feed = "$GGA\r\n$RMC\r\n$GLL\r\n$RMC\r\n"
    val fixes = sourceOf(feed).nmeaSentences().filterIsInstance<Rmc>().toList()
    assertEquals(2, fixes.size)
    assertTrue(fixes.all { it.position != null })
  }

  @Test
  fun skipsBlankLinesButNothingElse() {
    runTest {
      // A blank line carries nothing, so it is not worth a Malformed. Anything else is reported,
      // including a line that is not NMEA at all.
      val feed = "$GGA\r\n\r\n   \r\nnot a sentence\r\n$RMC\r\n"
      val results = sourceOf(feed).nmeaResults().toList()
      assertEquals(3, results.size)
      assertIs<ParseResult.Malformed>(results[1])
      assertTrue("begin with" in (results[1] as ParseResult.Malformed).reason)
    }
  }

  @Test
  fun keepsTheRawLineAlongsideTheParse() = runTest {
    val feed = "$GGA\r\n$RMC\r\n"
    assertEquals(listOf(GGA, RMC), sourceOf(feed).nmeaResults().lines().toList())
  }

  @Test
  fun unregisteredTypesArriveAsUnknownRatherThanAsFailures() = runTest {
    val feed = "$GGA\r\n"
    val results = sourceOf(feed).nmeaResults(SentenceRegistry.Empty).toList()
    val only = assertIs<ParseResult.Ok>(results.single())
    assertIs<UnknownSentence>(only.sentence)
  }

  @Test
  fun readsALongFeedWithoutLosingALine() = runTest {
    val feed = buildString { repeat(500) { append(GGA).append("\r\n").append(RMC).append("\r\n") } }
    assertEquals(1000, sourceOf(feed).nmeaResults().count())
    assertEquals(500, sourceOf(feed).nmeaSentences().filterIsInstance<Gga>().count())
  }

  @Test
  fun theFlowIsColdSoCollectingTwiceReadsTwice() = runTest {
    // Cold by construction: the source is read when collected. A Buffer is drained by the first
    // collection, which is exactly the behaviour a socket would show.
    val source = sourceOf("$GGA\r\n$RMC\r\n")
    val flow = source.nmeaResults()
    assertEquals(2, flow.count())
    assertEquals(0, flow.count(), "the buffer was drained by the first collection")
  }
}
