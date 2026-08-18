package io.github.solcott.marineapi.nmea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fixtures taken from the JUnit suite of the Java implementation, checksums and all. */
private const val GGA = "\$GPGGA,120044.567,6011.552,N,02501.941,E,1,00,2.0,28.0,M,19.6,M,,*63"
private const val RMC =
  "\$GPRMC,120044.567,A,6011.552,N,02501.941,E,000.0,360.0,160705,006.1,E,A,S*74"
private const val AIVDM = "!AIVDM,1,1,,A,403OviQuMGCqWrRO9>E6fE700@GO,0*4D"
private const val NO_CHECKSUM = "\$IIMWV,125.1,T,5.5,M,A"

class ChecksumTest {

  @Test
  fun calculatesChecksumOfSentenceThatCarriesOne() {
    assertEquals("63", Checksum.calculate(GGA))
    assertEquals("74", Checksum.calculate(RMC))
  }

  @Test
  fun calculatesChecksumOfEncapsulatedSentence() {
    // The '!' start delimiter is excluded from the sum just as '$' is.
    assertEquals("4D", Checksum.calculate(AIVDM))
  }

  @Test
  fun calculatesChecksumOfSentenceWithoutOne() {
    assertEquals("3F", Checksum.calculate(NO_CHECKSUM))
  }

  @Test
  fun calculatingIsIdempotentOverAppend() {
    val appended = Checksum.append(NO_CHECKSUM)
    assertEquals("$NO_CHECKSUM*3F", appended)
    assertEquals(appended, Checksum.append(appended))
  }

  @Test
  fun appendReplacesAnExistingChecksum() {
    assertEquals(GGA, Checksum.append(GGA))
    assertEquals(GGA, Checksum.append(GGA.substringBefore('*') + "*00"))
  }

  @Test
  fun xorIgnoresNothingBetweenTheDelimiters() {
    // Worked by hand: 'A' is 0x41, ',' is 0x2C, so 'A' xor ',' is 0x6D.
    assertEquals("6D", Checksum.xor("A,"))
    assertEquals("00", Checksum.xor(""))
  }

  @Test
  fun xorStaysTwoCharactersAboveAscii() {
    // The Java implementation sign-extended here and produced eight characters.
    assertEquals(2, Checksum.xor("ÿ").length)
  }

  @Test
  fun readsTheCarriedChecksum() {
    assertEquals("63", Checksum.read(GGA))
    assertEquals("63", Checksum.read("$GGA\r\n"))
    assertNull(Checksum.read(NO_CHECKSUM))
  }

  @Test
  fun delimiterIndexIsLengthWhenAbsent() {
    assertEquals(NO_CHECKSUM.length, Checksum.delimiterIndex(NO_CHECKSUM))
    assertEquals(GGA.length - 3, Checksum.delimiterIndex(GGA))
  }
}
