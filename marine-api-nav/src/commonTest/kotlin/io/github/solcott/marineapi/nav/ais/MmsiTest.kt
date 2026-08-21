package io.github.solcott.marineapi.nav.ais

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MmsiTest {

  @Test
  fun keepsTheLeadingZerosThatSayWhatKindOfStationItIs() {
    // 002442000 is a coast station and 244200000 is a Dutch vessel. Printed from an Int without
    // padding the first becomes "2442000" and stops being an MMSI at all.
    assertEquals("002442000", Mmsi(2442000).toString())
    assertEquals("244200000", Mmsi(244200000).toString())
  }

  @Test
  fun readsTheStationClassFromTheLeadingDigits() {
    assertEquals(StationClass.SHIP, Mmsi(244200000).stationClass)
    assertEquals(StationClass.COAST_STATION, Mmsi(2442000).stationClass)
    assertEquals(StationClass.GROUP, Mmsi(24420000).stationClass)
    assertEquals(StationClass.SAR_AIRCRAFT, Mmsi(111244001).stationClass)
    assertEquals(StationClass.HANDHELD, Mmsi(824420000).stationClass)
    assertEquals(StationClass.AUXILIARY_CRAFT, Mmsi(982442000).stationClass)
    assertEquals(StationClass.NAVIGATION_AID, Mmsi(992442000).stationClass)
    assertEquals(StationClass.AIS_SART, Mmsi(970244001).stationClass)
    assertEquals(StationClass.MOB_DEVICE, Mmsi(972123456).stationClass)
    assertEquals(StationClass.EPIRB, Mmsi(974123456).stationClass)
  }

  @Test
  fun tellsABuoyFromASmallVesselMovingSlowly() {
    // Both send position reports that look alike; only the identity says one is a mark.
    assertEquals(StationClass.NAVIGATION_AID, Mmsi(992351000).stationClass)
    assertEquals(StationClass.SHIP, Mmsi(235100000).stationClass)
  }

  @Test
  fun findsTheMidWhereEachKindOfStationKeepsIt() {
    // First three digits for a ship, third to fifth for a coast station or a buoy, fourth to sixth
    // for an aircraft -- which is the whole reason not to take the first three and hope.
    assertEquals(244, Mmsi(244200000).mid)
    assertEquals(244, Mmsi(2442000).mid)
    assertEquals(244, Mmsi(24420000).mid)
    assertEquals(244, Mmsi(111244001).mid)
    assertEquals(235, Mmsi(992351000).mid)
    assertEquals(235, Mmsi(823510000).mid)
  }

  @Test
  fun reportsNoMidForTheIdentitiesIssuedFromAGlobalPool() {
    // A man-overboard beacon and an EPIRB are not issued by any administration.
    assertNull(Mmsi(972123456).mid)
    assertNull(Mmsi(974123456).mid)
  }

  @Test
  fun namesTheAdministrationThatIssuedTheIdentity() {
    assertEquals("Netherlands", Mmsi(244200000).flagState)
    assertEquals("United Kingdom", Mmsi(235100000).flagState)
    assertEquals("United States", Mmsi(366123456).flagState)
    assertEquals("Panama", Mmsi(351123456).flagState)
    assertEquals("Singapore", Mmsi(563123456).flagState)
    assertEquals("Japan", Mmsi(431123456).flagState)
  }

  @Test
  fun namesATerritorySeparatelyFromTheStateThatAdministersIt() {
    // 231 is the Faroes' own assignment, not a share of Denmark's 219 and 220.
    assertEquals("Faroe Islands (Denmark)", Mmsi(231123456).flagState)
    assertEquals("Denmark", Mmsi(219123456).flagState)
  }

  @Test
  fun reportsNoFlagStateForAnUnassignedMid() {
    // 217 sits between Armenia's 216 and Germany's 218 and has never been allocated. A wrong flag
    // state would be worse than none.
    assertNull(Mmsi(217123456).flagState)
  }

  @Test
  fun refusesToReadANumberThatIsNotAnMmsiAtAll() {
    // The field is 30 bits wide, so a corrupted message can carry ten digits. Throwing here would
    // take down a flow decoding thousands of messages a minute over one bad bit.
    val corrupt = Mmsi(1073741823)
    assertEquals(StationClass.UNKNOWN, corrupt.stationClass)
    assertNull(corrupt.mid)
    assertNull(corrupt.flagState)
  }

  @Test
  fun readsAnUnallocatedPrefixAsUnknownRatherThanGuessing() {
    // A leading 1 that is not 111, and a leading 9 that is none of the four assigned ranges.
    assertEquals(StationClass.UNKNOWN, Mmsi(123456789).stationClass)
    assertEquals(StationClass.UNKNOWN, Mmsi(912345678).stationClass)
  }
}
