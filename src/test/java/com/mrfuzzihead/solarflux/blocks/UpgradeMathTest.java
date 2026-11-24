package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpgradeMathTest {

    @Test
    public void noUpgradesMeansTheBaseValue() {
        assertEquals(100, UpgradeMath.apply(100, 0, 0.1F, 1.0F));
    }

    @Test
    public void linearReturnsToScaleAddTheIncreasePerUpgrade() {
        assertEquals(110, UpgradeMath.apply(100, 1, 0.1F, 1.0F));
        assertEquals(140, UpgradeMath.apply(100, 4, 0.1F, 1.0F));
    }

    @Test
    public void decreasingReturnsAreTruncatedAfterThePower() {
        // 100 * (1 + 0.1 * 4^0.9) = 134.82...
        assertEquals(134, UpgradeMath.apply(100, 4, 0.1F, 0.9F));
    }

    @Test
    public void theDefaultCapacityCurveGivesSixtyThousandForTwoUpgrades() {
        assertEquals(60_000, UpgradeMath.apply(50_000, 2, 0.1F, 1.0F));
    }

    @Test
    public void theMultiplierIsOneWithoutUpgrades() {
        assertEquals(1.0D, UpgradeMath.multiplier(0, 0.1F, 0.9F), 1.0E-9D);
    }

    @Test
    public void theMultiplierFollowsTheReturnsToScale() {
        assertEquals(1.34822D, UpgradeMath.multiplier(4, 0.1F, 0.9F), 1.0E-5D);
    }
}
