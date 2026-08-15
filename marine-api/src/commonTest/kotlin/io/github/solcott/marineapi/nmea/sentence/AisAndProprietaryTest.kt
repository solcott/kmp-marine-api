package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.Nmea
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Java suite's `VDMTest` and `VDOTest` constants. */
private object AisExamples {
  const val VDM = "!AIVDM,1,1,,A,403OviQuMGCqWrRO9>E6fE700@GO,0*4D"
  const val VDM_PART1 =
    "!AIVDM,2,1,1,A,55?MbV02;H;s<HtKR20EHE:0@T4@Dn2222222216L961O5Gf0NSQEp6ClRp8,0*1C"
  const val VDM_PART2 = "!AIVDM,2,2,1,A,88888888880,2*25"
  const val VDO = "!AIVDO,1,1,,B,H1c2;qA@PU>0U>060<h5=>0:1Dp,2*7D"
}

class VdmVdoTest {

  private val vdm = parse<Vdm>(AisExamples.VDM)

  @Test
  fun readsTheEnvelope() {
    assertEquals(TalkerId.AI, vdm.talker)
    assertEquals(1, vdm.fragmentCount)
    assertEquals(1, vdm.fragmentNumber)
    assertNull(vdm.messageId, "a single-sentence message needs no sequence id")
    assertEquals("A", vdm.radioChannel)
    assertEquals("403OviQuMGCqWrRO9>E6fE700@GO", vdm.payload)
    assertEquals(0, vdm.fillBits)
  }

  @Test
  fun beginsWithAnExclamationMark() {
    // Encapsulated sentences are delimited differently, and the round trip has to keep that.
    assertEquals(Nmea.ALTERNATIVE_BEGIN_CHAR, vdm.beginChar)
    assertEquals(AisExamples.VDM, vdm.toNmeaString())
    assertEquals(AisExamples.VDO, parse<Vdo>(AisExamples.VDO).toNmeaString())
  }

  @Test
  fun knowsWhereItSitsInItsMessage() {
    assertFalse(vdm.isFragmented)
    assertTrue(vdm.isFirstFragment && vdm.isLastFragment, "one sentence is both ends of itself")

    val first = parse<Vdm>(AisExamples.VDM_PART1)
    val second = parse<Vdm>(AisExamples.VDM_PART2)
    assertTrue(first.isFragmented && second.isFragmented)
    assertTrue(first.isFirstFragment && !first.isLastFragment)
    assertTrue(!second.isFirstFragment && second.isLastFragment)
    assertEquals("1", first.messageId)
  }

  @Test
  fun recognisesTheSentenceThatContinuesIt() {
    val first = parse<Vdm>(AisExamples.VDM_PART1)
    val second = parse<Vdm>(AisExamples.VDM_PART2)
    assertTrue(second.continues(first))
    assertFalse(first.continues(second), "the sequence runs one way")
    assertFalse(first.continues(first), "a fragment does not continue itself")
  }

  @Test
  fun refusesToJoinFragmentsOfDifferentMessages() {
    val first = parse<Vdm>(AisExamples.VDM_PART1)
    val otherMessage = parse<Vdm>(Checksum.append("!AIVDM,2,2,4,B,88888888880,2"))
    assertFalse(otherMessage.continues(first), "different channel and different id")

    // Adjacent fragments need agree on only one of the two, which is what lets a receiver that
    // leaves the message id empty still be followed.
    val sameChannelOnly = parse<Vdm>(Checksum.append("!AIVDM,2,2,4,A,88888888880,2"))
    assertTrue(sameChannelOnly.continues(first))

    val differentLength = parse<Vdm>(Checksum.append("!AIVDM,3,2,1,A,88888888880,2"))
    assertFalse(differentLength.continues(first), "a message of three is not a message of two")
  }

  @Test
  fun requiresBothFragmentsToBeThreeApartToNeedBothIdentifiers() {
    val first = parse<Vdm>(Checksum.append("!AIVDM,3,1,7,A,55?MbV02;H;s,0"))
    val thirdSameChannel = parse<Vdm>(Checksum.append("!AIVDM,3,3,9,A,88888888880,2"))
    assertFalse(thirdSameChannel.continues(first), "a non-adjacent fragment must match on both")

    val thirdBoth = parse<Vdm>(Checksum.append("!AIVDM,3,3,7,A,88888888880,2"))
    assertTrue(thirdBoth.continues(first))
  }

  @Test
  fun vdmAndVdoAreDistinctTypes() {
    // The difference is who the report is about, which is the whole reason for two type codes.
    assertIs<Vdm>(SentenceRegistry.Default.parse(AisExamples.VDM).sentenceOrNull())
    assertIs<Vdo>(SentenceRegistry.Default.parse(AisExamples.VDO).sentenceOrNull())
  }

  @Test
  fun rejectsAnEnvelopeThatCannotDescribeAFragment() {
    // These fields are not optional data. Without them there is nothing to reassemble, so an
    // empty one is corruption rather than a receiver being terse.
    val noPayload = SentenceRegistry.Default.parse(Checksum.append("!AIVDM,1,1,,A,,0"))
    assertTrue("no payload" in assertIs<ParseResult.Malformed>(noPayload).reason)

    val noCount = SentenceRegistry.Default.parse(Checksum.append("!AIVDM,,1,,A,403Ovi,0"))
    assertTrue("fragment count" in assertIs<ParseResult.Malformed>(noCount).reason)

    val impossibleFragment =
      SentenceRegistry.Default.parse(Checksum.append("!AIVDM,2,3,1,A,403Ovi,0"))
    assertTrue("not one of 2" in assertIs<ParseResult.Malformed>(impossibleFragment).reason)

    val tooMuchPadding = SentenceRegistry.Default.parse(Checksum.append("!AIVDM,1,1,,A,403Ovi,6"))
    assertTrue("Fill bits" in assertIs<ParseResult.Malformed>(tooMuchPadding).reason)
  }
}

class UbxTest {

  // A u-blox position report, message id 00.
  private val line =
    "\$PUBX,00,125926.00,4717.11337,N,00833.91163,E,111.500,GLL,20,15,0.007,0.00,,,,3.00,3.00,3.00,,,,,,,"

  @Test
  fun readsTheMessageIdAndKeepsTheRestAsText() {
    val ubx = parse<Ubx>(Checksum.append(line))
    assertEquals(TalkerId.P, ubx.talker, "proprietary sentences have no talker of their own")
    assertEquals(0, ubx.messageId)
    assertEquals("125926.00", ubx.stringAt(1))
    assertEquals(15, ubx.intAt(9))
    assertEquals(0.007, ubx.doubleAt(10))
    assertNull(ubx.stringAt(12), "an empty field is not a value")
    assertNull(ubx.stringAt(99), "nor is one past the end")
  }

  @Test
  fun roundTripsEveryFieldIncludingTheTrailingEmptyOnes() {
    // A device that sends 25 fields is saying something different from one that sends 12, so the
    // empty ones at the end are kept rather than trimmed.
    val ubx = parse<Ubx>(Checksum.append(line))
    assertEquals(Checksum.append(line), ubx.toNmeaString())
  }

  @Test
  fun rejectsASentenceWithNoMessageId() {
    // Every other field's meaning depends on this one, so a UBX without it cannot be read at all.
    val result = SentenceRegistry.Default.parse(Checksum.append("\$PUBX,,125926.00"))
    assertTrue("message id" in assertIs<ParseResult.Malformed>(result).reason)
  }
}

class StalkTest {

  @Test
  fun readsTheCommandAndItsParameters() {
    val stalk = parse<Stalk>("\$STALK,52,A1,00,00*36")
    assertEquals(TalkerId.ST, stalk.talker)
    assertEquals("52", stalk.command)
    assertEquals(listOf("A1", "00", "00"), stalk.parameters)
  }

  @Test
  fun roundTrips() {
    assertEquals("\$STALK,52,A1,00,00*36", parse<Stalk>("\$STALK,52,A1,00,00*36").toNmeaString())
  }

  @Test
  fun readsACommandWithNoParameters() {
    val bare = parse<Stalk>(Checksum.append("\$STALK,52"))
    assertEquals("52", bare.command)
    assertEquals(emptyList(), bare.parameters)
  }

  @Test
  fun isAlwaysSentByTheSeaTalkTalker() {
    // The tag is $STALK -- talker ST, type ALK. An ALK from anyone else means the tag was misread.
    val wrongTalker = SentenceRegistry.Default.parse(Checksum.append("\$GPALK,52,A1"))
    assertTrue("always \$STALK" in assertIs<ParseResult.Malformed>(wrongTalker).reason)
  }
}
