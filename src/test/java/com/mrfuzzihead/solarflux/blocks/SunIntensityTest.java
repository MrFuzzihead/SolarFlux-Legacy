package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SunIntensityTest {

    private static final float RAIN = 0.4F;
    private static final float THUNDER = 0.4F;
    private static final float DELTA = 0.001F;
    private static final float PI = (float) Math.PI;

    private static float sun(float pAngle) {
        return SunIntensity.calculate(true, pAngle, false, false, 0, RAIN, THUNDER);
    }

    @Test
    public void noonIsFullIntensity() {
        assertEquals(1.0F, sun(0.0F), 0.0F);
    }

    @Test
    public void midnightIsDark() {
        assertEquals(0.0F, sun(PI), 0.0F);
    }

    @Test
    public void theMidAfternoonValueMatchesTheDocumentedCurve() {
        assertEquals(0.3882F, sun(PI / 2.0F), DELTA);
    }

    @Test
    public void theNightHalfIsFoldedOntoTheDayHalf() {
        assertEquals(sun(PI / 2.0F), sun(2.0F * PI - PI / 2.0F), DELTA);
    }

    @Test
    public void aPanelThatCannotSeeTheSkyIsDark() {
        assertEquals(0.0F, SunIntensity.calculate(false, 0.0F, false, false, 0, RAIN, THUNDER), 0.0F);
    }

    @Test
    public void rainAndThunderMultiplyTheIntensity() {
        assertEquals(0.4F, SunIntensity.calculate(true, 0.0F, true, false, 0, RAIN, THUNDER), DELTA);
        assertEquals(0.4F, SunIntensity.calculate(true, 0.0F, false, true, 0, RAIN, THUNDER), DELTA);
        assertEquals(0.16F, SunIntensity.calculate(true, 0.0F, true, true, 0, RAIN, THUNDER), DELTA);
    }

    @Test
    public void weatherDoesNotTurnTheNightIntoDaylight() {
        assertEquals(0.0F, SunIntensity.calculate(true, PI, true, true, 0, RAIN, THUNDER), 0.0F);
    }

    @Test
    public void lowLightUpgradesTradeNoonOutputForAWiderDaylightWindow() {
        float noonBase = sun(0.0F);
        float noonLowLight = SunIntensity.calculate(true, 0.0F, false, false, 8, RAIN, THUNDER);

        assertTrue("low light must dim noon", noonLowLight < noonBase);
        assertEquals(0.0F, sun(3.0F * PI / 4.0F), 0.0F);
        assertTrue(
            "low light must still see the sun near dusk",
            SunIntensity.calculate(true, 3.0F * PI / 4.0F, false, false, 8, RAIN, THUNDER) > 0.0F);
    }
}
