package io.github.solcott.marineapi.example.android.ui.common

import io.github.solcott.marineapi.nmea.FaaMode
import io.github.solcott.marineapi.nmea.GpsFixQuality
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.AccuracySource
import io.github.solcott.marineapi.nmea.io.FixAccuracy
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The accuracy shown on the GPS screen is a pure function of a fix, so it is tested here rather
 * than through the ViewModel. The distinction it draws -- a measured error against one this app
 * derived from dilution of precision -- is the whole point of the feature, so it is what these
 * assert.
 */
class FormattingTest {

  private fun fix(
    accuracy: FixAccuracy? = null,
    horizontalDilution: Double? = null,
    fixQuality: GpsFixQuality? = null,
    faaMode: FaaMode? = null,
  ) =
    PositionFix(
      position = Position(60.0, 25.0),
      accuracy = accuracy,
      horizontalDilution = horizontalDilution,
      fixQuality = fixQuality,
      faaMode = faaMode,
    )

  @Test
  fun reportsWhatTheReceiverMeasuredWhenItMeasuredAnything() {
    val estimate =
      assertNotNull(
        fix(
            accuracy = FixAccuracy(horizontal = 0.85, source = AccuracySource.GST),
            horizontalDilution = 0.95,
          )
          .accuracyEstimate()
      )

    assertTrue(estimate.measured)
    assertEquals(0.85, estimate.metres, "the HDOP beside it was not used")
  }

  @Test
  fun derivesAnEstimateFromDilutionWhenNothingWasMeasured() {
    val estimate = assertNotNull(fix(horizontalDilution = 0.64).accuracyEstimate())

    assertFalse(estimate.measured)
    assertEquals(0.64 * 19.0, estimate.metres)
  }

  @Test
  fun assumesLessErrorOnACorrectedFix() {
    // The same geometry, corrected, is a better fix -- which is the whole reason gpsd keeps two
    // constants rather than one.
    val autonomous = assertNotNull(fix(horizontalDilution = 1.1).accuracyEstimate())
    val differential =
      assertNotNull(
        fix(horizontalDilution = 1.1, fixQuality = GpsFixQuality.DGPS).accuracyEstimate()
      )

    assertEquals(1.1 * 4.75, differential.metres)
    assertTrue(differential.metres < autonomous.metres)
  }

  @Test
  fun readsTheCorrectionFromEitherSentencesField() {
    // A cycle can carry a GGA without an RMC or the reverse, so neither field alone can be relied
    // on to say a fix was corrected.
    val fromGga = assertNotNull(fix(horizontalDilution = 1.0, fixQuality = GpsFixQuality.DGPS))
    val fromRmc = assertNotNull(fix(horizontalDilution = 1.0, faaMode = FaaMode.DGPS))

    assertEquals(4.75, assertNotNull(fromGga.accuracyEstimate()).metres)
    assertEquals(4.75, assertNotNull(fromRmc.accuracyEstimate()).metres)
  }

  @Test
  fun treatsRtkAsCorrectedToo() {
    val rtk = assertNotNull(fix(horizontalDilution = 1.0, fixQuality = GpsFixQuality.RTK))
    assertEquals(4.75, assertNotNull(rtk.accuracyEstimate()).metres)
  }

  @Test
  fun offersNothingWhenTheReceiverGaveNoBasisForSayingAnything() {
    // Not a zero and not a dash: those read as a measurement of something. The screen shows no row.
    assertNull(fix().accuracyEstimate())
  }

  @Test
  fun roundsToOneDecimalWithoutConsultingTheLocale() {
    // String.format would print "12,2" on a phone set to half the world's locales.
    val estimate = assertNotNull(fix(horizontalDilution = 0.64).accuracyEstimate())
    assertEquals("12.2", estimate.metresToOneDecimal())
  }
}
