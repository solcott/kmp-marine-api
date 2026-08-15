package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.AngleReference
import io.github.solcott.marineapi.nmea.BearingReference
import io.github.solcott.marineapi.nmea.Checksum
import io.github.solcott.marineapi.nmea.DataStatus
import io.github.solcott.marineapi.nmea.Direction
import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.ReferenceSystem
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.SteeringMode
import io.github.solcott.marineapi.nmea.TalkerId
import io.github.solcott.marineapi.nmea.TurnMode
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class RpmTest {

  @Test
  fun readsEngineRevolutions() {
    // The Java suite's EXAMPLE constant.
    val rpm = parse<Rpm>(Checksum.append("\$IIRPM,E,1,2418.2,10.5,A"))
    assertEquals(RevolutionSource.ENGINE, rpm.source)
    assertEquals(1, rpm.sourceNumber)
    assertEquals(2418.2, rpm.revolutions)
    assertEquals(10.5, rpm.propellerPitch)
    assertEquals(DataStatus.ACTIVE, rpm.status)
  }

  @Test
  fun readsShaftRevolutionsWithAsternPitch() {
    val astern = parse<Rpm>(Checksum.append("\$IIRPM,S,2,1200.0,-15.0,A"))
    assertEquals(RevolutionSource.SHAFT, astern.source)
    assertEquals(-15.0, astern.propellerPitch, "negative pitch is astern")
  }
}

class CurTest {

  private val cur = parse<Cur>(Checksum.append("\$INCUR,A,1,2,10.0,45.5,T,1.5,20.0,90.0,T,B"))

  @Test
  fun readsEveryField() {
    assertEquals(DataStatus.ACTIVE, cur.status)
    assertEquals(1, cur.dataSetNumber)
    assertEquals(2, cur.layerNumber)
    assertEquals(10.0, cur.currentDepth)
    assertEquals(45.5, cur.currentDirection)
    assertEquals(AngleReference.TRUE, cur.directionReference)
    assertEquals(1.5, cur.currentSpeed)
    assertEquals(20.0, cur.referenceLayerDepth)
    assertEquals(90.0, cur.currentHeading)
    assertEquals(BearingReference.TRUE, cur.headingReference)
    assertEquals(ReferenceSystem.BOTTOM_TRACKING_LOG, cur.speedReference)
  }

  @Test
  fun readsARelativeDirectionAgainstTheWater() {
    val relative = parse<Cur>(Checksum.append("\$INCUR,A,1,2,10.0,45.5,R,1.5,20.0,90.0,M,W"))
    assertEquals(AngleReference.RELATIVE, relative.directionReference)
    assertEquals(BearingReference.MAGNETIC, relative.headingReference)
    assertEquals(ReferenceSystem.WATER_REFERENCED, relative.speedReference)
  }

  @Test
  fun rejectsADirectionReferenceItCannotRead() {
    // The reference decides whether the direction is measured from true north or from the bow,
    // so it changes what the number means and stays strict.
    val result =
      SentenceRegistry.Default.parse(Checksum.append("\$INCUR,A,1,2,10.0,45.5,X,1.5,20.0,90.0,T,B"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("one of [R, T]" in malformed.reason, malformed.reason)
  }
}

class HtcHtdTest {

  // The Java suite's HTD EXAMPLE constant.
  private val htd = parse<Htd>("\$AGHTD,V,0.1,R,M,,15.0,15.0,,,,,,T,A,A,A,90.3,*39")

  @Test
  fun readsTheCommandHalf() {
    val command = htd.command
    assertEquals(DataStatus.VOID, command.override)
    assertEquals(0.1, command.commandedRudderAngle)
    assertEquals(Direction.RIGHT, command.commandedRudderDirection)
    assertEquals(SteeringMode.MANUAL, command.selectedSteeringMode)
    assertNull(command.turnMode)
    assertEquals(15.0, command.commandedRudderLimit)
    assertEquals(15.0, command.commandedOffHeadingLimit)
    assertEquals(BearingReference.TRUE, command.headingReference)
  }

  @Test
  fun readsTheFourFieldsThatMakeItAReport() {
    assertEquals(DataStatus.ACTIVE, htd.rudderStatus)
    assertEquals(DataStatus.ACTIVE, htd.offHeadingStatus)
    assertEquals(DataStatus.ACTIVE, htd.offTrackStatus)
    assertEquals(90.3, htd.heading)
  }

  @Test
  fun htcIsTheCommandOnItsOwn() {
    val htc = parse<Htc>(Checksum.append("\$AGHTC,V,0.1,R,M,T,15.0,15.0,,,270.0,,270.0,T"))
    assertEquals(TurnMode.TURN_RATE_CONTROLLED, htc.turnMode, "T is rate, R is radius")
    assertEquals(270.0, htc.commandedHeadingToSteer)
    assertEquals(270.0, htc.commandedTrack)
  }

  @Test
  fun theTwoShareTheirFirstThirteenFields() {
    // HTD echoes HTC's command, so the same thirteen fields read the same way in both. Sharing
    // the reader is what keeps them from drifting apart the way the Java pair could.
    val body = "V,0.1,R,M,T,15.0,15.0,,,270.0,,270.0,T"
    val htc = parse<Htc>(Checksum.append("\$AGHTC,$body"))
    val asReport = parse<Htd>(Checksum.append("\$AGHTD,$body,A,A,A,90.3"))
    assertEquals(htc.copy(talker = TalkerId.AG), asReport.command)
  }

  @Test
  fun bothAreDistinctTypes() {
    assertIs<Htc>(
      SentenceRegistry.Default.parse(Checksum.append("\$AGHTC,V,0.1,R,M,,15.0,15.0,,,,,,T"))
        .sentenceOrNull()
    )
    assertIs<Htd>(
      SentenceRegistry.Default.parse("\$AGHTD,V,0.1,R,M,,15.0,15.0,,,,,,T,A,A,A,90.3,*39")
        .sentenceOrNull()
    )
  }
}

class DtaDtbTest {

  @Test
  fun readsTheEightFieldFormWithAChannel() {
    // The Java suite's EXAMPLE_MC constant.
    val dta = parse<Dta>("\$GFDTA,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3C")
    assertEquals(1, dta.channelNumber)
    assertEquals(1.5, dta.gasConcentration)
    assertEquals(99.0, dta.confidenceFactor)
    assertEquals(600.0, dta.distance)
    assertEquals(11067.0, dta.lightLevel)
    assertEquals(LocalDateTime(2002, 3, 1, 0, 30, 28), dta.dateTime)
    assertEquals("HF-1xxx", dta.serialNumber)
    assertEquals(1, dta.statusCode)
  }

  @Test
  fun readsTheSevenFieldFormWithNoChannel() {
    // The Java suite's EXAMPLE2 constant: units that report no channel shift everything left.
    val dta = parse<Dta>("\$GFDTA,7.7,98,600,5527,2011/01/27 13:29:28,HFH2O-1xxx,1*2B")
    assertNull(dta.channelNumber)
    assertEquals(7.7, dta.gasConcentration)
    assertEquals(98.0, dta.confidenceFactor)
    assertEquals(LocalDateTime(2011, 1, 27, 13, 29, 28), dta.dateTime)
    assertEquals("HFH2O-1xxx", dta.serialNumber)
  }

  @Test
  fun keepsATimestampThatIsNotInNmeaFormatAtAll() {
    // yyyy/MM/dd HH:mm:ss, spaces and slashes and all, inside a single NMEA field. The rest of
    // the sentence is not compared as text: the instrument writes 99 where these are Double
    // fields, so they come back as 99.0. The timestamp is the part that has to survive verbatim.
    val line = "\$GFDTA,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3C"
    val dta = parse<Dta>(line)
    assertTrue("2002/03/01 00:30:28" in dta.toNmeaString(), dta.toNmeaString())
    assertEquals(dta, SentenceRegistry.Default.parse(dta.toNmeaString()).sentenceOrNull())
  }

  @Test
  fun rejectsATimestampItCannotRead() {
    val result =
      SentenceRegistry.Default.parse(Checksum.append("\$GFDTA,1,1.5,99,600,11067,not-a-date,HF,1"))
    val malformed = assertIs<ParseResult.Malformed>(result)
    assertTrue("yyyy/MM/dd HH:mm:ss" in malformed.reason, malformed.reason)
  }

  @Test
  fun dtbReadsItsChannelFromTheSentence() {
    // The implementation this replaces returned a hardcoded 2 here whatever the sentence said.
    val dtb = parse<Dtb>("\$GFDTB,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3F")
    assertEquals(1, dtb.channelNumber, "the sentence says channel 1, not 2")
    assertEquals(1.5, dtb.gasConcentration)
  }

  @Test
  fun theTwoAreDistinctTypes() {
    assertIs<Dta>(
      SentenceRegistry.Default.parse("\$GFDTA,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3C")
        .sentenceOrNull()
    )
    assertIs<Dtb>(
      SentenceRegistry.Default.parse("\$GFDTB,1,1.5,99,600,11067,2002/03/01 00:30:28,HF-1xxx,1*3F")
        .sentenceOrNull()
    )
  }
}
