package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GGA = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
private const val AIVDM = "!AIVDM,1,1,,A,403OviQuMGCqWrRO9>E6fE700@GO,0*4D"
private const val NO_CHECKSUM = "\$IIMWV,125.1,T,5.5,M,A"
private const val PROPRIETARY = "\$PGRMZ,93,f,3*21"

class SentenceRegistryTest {

  // Empty rather than Default: these cover the parsing mechanics, so every sentence
  // should reach UnknownSentence regardless of which types happen to be registered.
  private val registry = SentenceRegistry.Empty

  @Test
  fun parsesTalkerAndType() {
    val fields = assertIs<UnknownSentence>(registry.parse(GGA).sentenceOrNull()).data
    assertEquals(TalkerId.GP, fields.talker)
    assertEquals("GGA", fields.id)
    assertEquals(Nmea.BEGIN_CHAR, fields.beginChar)
  }

  @Test
  fun parsesEncapsulatedSentence() {
    val sentence = assertIs<UnknownSentence>(registry.parse(AIVDM).sentenceOrNull())
    assertEquals(TalkerId.AI, sentence.talker)
    assertEquals("VDM", sentence.id)
    assertEquals(Nmea.ALTERNATIVE_BEGIN_CHAR, sentence.beginChar)
  }

  @Test
  fun parsesProprietarySentence() {
    // '$P' means the rest of the tag is a manufacturer mnemonic, not a three-character type.
    val sentence = assertIs<UnknownSentence>(registry.parse(PROPRIETARY).sentenceOrNull())
    assertEquals(TalkerId.P, sentence.talker)
    assertEquals("GRMZ", sentence.id)
    assertTrue(sentence.data.isProprietary)
  }

  @Test
  fun acceptsSentenceWithoutChecksum() {
    assertIs<ParseResult.Ok>(registry.parse(NO_CHECKSUM))
  }

  @Test
  fun acceptsTrailingLineTerminator() {
    assertIs<ParseResult.Ok>(registry.parse("$GGA\r\n"))
    assertIs<ParseResult.Ok>(registry.parse("$GGA\n"))
  }

  @Test
  fun reportsChecksumMismatch() {
    val result = assertIs<ParseResult.BadChecksum>(registry.parse(GGA.dropLast(2) + "00"))
    assertEquals("63", result.expected)
    assertEquals("00", result.actual)
  }

  @Test
  fun acceptsLowerCaseChecksum() {
    // The format specifies upper case, but accepting either costs nothing and devices vary.
    assertIs<ParseResult.Ok>(registry.parse(AIVDM.dropLast(2) + "4d"))
  }

  @Test
  fun reportsMalformedInput() {
    assertIs<ParseResult.Malformed>(registry.parse(""))
    assertIs<ParseResult.Malformed>(registry.parse("not a sentence"))
    assertIs<ParseResult.Malformed>(registry.parse("\$GPGGA")) // no fields
    assertIs<ParseResult.Malformed>(registry.parse("\$GP,1,2")) // no type
  }

  @Test
  fun unregisteredTypeParsesAsUnknownRatherThanFailing() {
    val sentence =
      assertIs<UnknownSentence>(registry.parse(Checksum.append("\$GPZZZ,1,2")).sentenceOrNull())
    assertEquals("ZZZ", sentence.id)
    assertEquals("1", sentence.data.stringAt(0))
  }

  @Test
  fun unknownTalkerDoesNotFail() {
    // The Java implementation threw IllegalArgumentException from TalkerId.parse here.
    val sentence =
      assertIs<UnknownSentence>(registry.parse(Checksum.append("\$XXGGA,1")).sentenceOrNull())
    assertEquals(TalkerId.of("XX"), sentence.talker)
  }

  @Test
  fun registeredFactoryIsUsed() {
    val custom = registry.with("GGA") { fields -> TestSentence(fields.talker, fields.doubleAt(0)) }
    val sentence = assertIs<TestSentence>(custom.parse(GGA).sentenceOrNull())
    assertEquals(120044.567, sentence.value)
    assertTrue("GGA" in custom.types)
  }

  @Test
  fun registryIsImmutable() {
    registry.with("GGA") { UnknownSentence(it) }
    assertTrue("GGA" !in registry.types, "with() must not mutate the receiver")
  }

  @Test
  fun badFieldTypeIsReportedAsMalformed() {
    val custom = registry.with("GGA") { fields -> TestSentence(fields.talker, fields.doubleAt(5)) }
    // Field 5 of the GGA fixture is the fix quality "1"; field 9 is "M", not a number.
    val bad = custom.with("GGA") { fields -> TestSentence(fields.talker, fields.doubleAt(9)) }
    val result = assertIs<ParseResult.Malformed>(bad.parse(GGA))
    assertTrue("not a number" in result.reason, result.reason)
    assertIs<ParseResult.Ok>(custom.parse(GGA))
  }

  @Test
  fun missingFieldsReadAsNull() {
    val fields = assertIs<UnknownSentence>(registry.parse(GGA).sentenceOrNull()).data
    assertNull(fields.stringAt(12)) // empty DGPS station id
    assertNull(fields.stringAt(99)) // beyond the end of the sentence
    assertNull(fields.doubleAt(12))
  }

  @Test
  fun unknownSentenceRoundTrips() {
    assertEquals(GGA, registry.parse(GGA).sentenceOrNull()?.toNmeaString())
    assertEquals(AIVDM, registry.parse(AIVDM).sentenceOrNull()?.toNmeaString())
    assertEquals(PROPRIETARY, registry.parse(PROPRIETARY).sentenceOrNull()?.toNmeaString())
  }

  private class TestSentence(override val talker: TalkerId, val value: Double?) : Sentence {
    override val id: String = "GGA"

    override fun toNmeaString(): String = "\$${talker}GGA,$value"
  }
}
