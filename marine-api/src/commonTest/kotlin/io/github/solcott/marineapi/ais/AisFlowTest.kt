package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentence.Vdm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/** A two-sentence type 5 static report, and a single-sentence type 1, from the Java suite. */
private object Sentences {
  const val POSITION = "!AIVDM,1,1,,B,177KQJ5000G?tO`K>RA1wUbN0TKH,0*5C"
  const val STATIC_1 =
    "!AIVDM,2,1,1,A,55?MbV02;H;s<HtKR20EHE:0@T4@Dn2222222216L961O5Gf0NSQEp6ClRp8,0*1C"
  const val STATIC_2 = "!AIVDM,2,2,1,A,88888888880,2*25"
  const val GGA = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
}

private fun sentencesOf(vararg lines: String): Flow<Sentence> = flow {
  for (line in lines) {
    val result = SentenceRegistry.Default.parse(line)
    emit(assertIs<ParseResult.Ok>(result, "fixture did not parse: $result").sentence)
  }
}

class AisFlowTest {

  @Test
  fun decodesASingleSentenceMessage() = runTest {
    val results = sentencesOf(Sentences.POSITION).aisMessages().toList()
    val message = assertIs<AisResult.Ok>(results.single()).message
    assertIs<AisPositionReport>(message)
    assertEquals(1, message.messageType)
  }

  @Test
  fun joinsTheTwoSentencesOfAStaticReport() = runTest {
    // 424 bits will not fit in one sentence, so a type 5 always arrives split. Neither half is a
    // message on its own: the name is in the first fragment and the destination in the second, so
    // reading both proves the two were actually joined rather than decoded separately.
    val results =
      sentencesOf(Sentences.STATIC_1, Sentences.STATIC_2).aisMessages().messages().toList()
    val report = assertIs<AisStaticAndVoyageData>(results.single())
    assertEquals("EVER DIADEM", report.name)
    assertEquals(351_759_000, report.mmsi)
    assertEquals("NEW YORK", report.destination, "which only the second fragment carries")
  }

  @Test
  fun emitsNothingUntilTheLastFragmentArrives() = runTest {
    assertEquals(emptyList(), sentencesOf(Sentences.STATIC_1).aisMessages().toList())
  }

  @Test
  fun takesTheFillBitsFromTheLastFragmentOnly() = runTest {
    // Only the final fragment can end mid-character; the others are whole by construction. The
    // second sentence here declares 2 fill bits and the first declares 0.
    val results = sentencesOf(Sentences.STATIC_1, Sentences.STATIC_2).aisMessages().toList()
    val payload = assertIs<AisResult.Ok>(results.single()).payload
    assertEquals(2, payload.fillBits)
    assertEquals(424, payload.size, "which is exactly the length of a type 5")
  }

  @Test
  fun ignoresSentencesThatAreNotAis() = runTest {
    // AIS arrives interleaved with the receiver's own NMEA, so this has to compose with the rest
    // of a feed rather than needing one of its own.
    val results =
      sentencesOf(Sentences.GGA, Sentences.POSITION, Sentences.GGA).aisMessages().toList()
    assertEquals(1, results.size)
  }

  @Test
  fun discardsASequenceThatIsInterrupted() = runTest {
    // A fragment lost to interference means the rest of that message can never be assembled. The
    // pieces that did arrive are dropped rather than decoded into a message that was never sent.
    val results =
      sentencesOf(Sentences.STATIC_1, Sentences.POSITION, Sentences.STATIC_2).aisMessages().toList()
    assertEquals(1, results.size, "only the single-sentence message survives")
    assertIs<AisPositionReport>(assertIs<AisResult.Ok>(results.single()).message)
  }

  @Test
  fun discardsASequenceThatNeverEnds() = runTest {
    // Held state is bounded: a first fragment that is never followed is dropped when the next
    // sequence begins, and anything still held when the feed ends is dropped with it.
    val results =
      sentencesOf(Sentences.STATIC_1, Sentences.STATIC_1, Sentences.STATIC_1).aisMessages().toList()
    assertEquals(emptyList(), results)
  }

  @Test
  fun ignoresAContinuationWithNoBeginning() = runTest {
    // A feed joined partway through starts with the middle of a message. There is nothing to
    // attach it to, so it is skipped rather than decoded as though it were a whole one.
    assertEquals(emptyList(), sentencesOf(Sentences.STATIC_2).aisMessages().toList())
  }

  @Test
  fun narrowsToOneMessageTypeWithoutReflection() = runTest {
    // What replaces AbstractAISMessageListener and its GenericTypeResolver.
    val feed =
      sentencesOf(Sentences.POSITION, Sentences.STATIC_1, Sentences.STATIC_2, Sentences.POSITION)
    val positions = feed.aisMessages().messages().filterIsInstance<AisPositionReport>().toList()
    assertEquals(2, positions.size)
    assertTrue(positions.all { it.position != null })
  }

  @Test
  fun reportsAnUnsupportedTypeRatherThanDroppingIt() = runTest {
    // A type 8 binary broadcast: not decoded, but not silently discarded either.
    val binary = Checksum.append("!AIVDM,1,1,,A,801uc0qNTPP0>PUPPP1BUPP0,0")
    val results = sentencesOf(binary).aisMessages().toList()
    assertEquals(8, assertIs<AisResult.Unsupported>(results.single()).messageType)
    assertEquals(emptyList(), sentencesOf(binary).aisMessages().messages().toList())
  }

  @Test
  fun decodesASingleSentenceDirectly() {
    // For a caller holding one sentence rather than a flow.
    val sentence =
      assertIs<Vdm>(
        assertIs<ParseResult.Ok>(SentenceRegistry.Default.parse(Sentences.POSITION)).sentence
      )
    assertIs<AisPositionReport>(assertIs<AisResult.Ok>(sentence.aisMessageOrNull()).message)

    val fragment =
      assertIs<Vdm>(
        assertIs<ParseResult.Ok>(SentenceRegistry.Default.parse(Sentences.STATIC_1)).sentence
      )
    assertNull(fragment.aisMessageOrNull(), "one fragment of a message is not a message")
  }
}
