package com.mrfuzzihead.solarflux.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.mrfuzzihead.solarflux.core.utility.MetricUnits;

public class TierConfigurationTest {

    @Test
    public void theExplicitConstructorKeepsItsOwnTransferRate() {
        TierConfiguration configuration = new TierConfiguration(10, 20, 30);

        assertEquals(10, configuration.getMaximumEnergyGeneration());
        assertEquals(20, configuration.getMaximumEnergyTransfer());
        assertEquals(30, configuration.getCapacity());
    }

    @Test
    public void theDerivedConstructorUsesEightTimesTheGenerationAsTransferRate() {
        TierConfiguration configuration = new TierConfiguration(7, 500);

        assertEquals(7, configuration.getMaximumEnergyGeneration());
        assertEquals(56, configuration.getMaximumEnergyTransfer());
        assertEquals(500, configuration.getCapacity());
    }

    @Test
    public void theDefaultTiersCoverSixSolarPanels() {
        assertEquals(6, ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size());
    }

    @Test
    public void theDefaultTiersUseTheDocumentedGenerationRamp() {
        List<TierConfiguration> tiers = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS;

        assertEquals(
            2,
            tiers.get(0)
                .getMaximumEnergyGeneration());
        assertEquals(
            16,
            tiers.get(1)
                .getMaximumEnergyGeneration());
        assertEquals(
            64,
            tiers.get(2)
                .getMaximumEnergyGeneration());
        assertEquals(
            256,
            tiers.get(3)
                .getMaximumEnergyGeneration());
        assertEquals(
            1024,
            tiers.get(4)
                .getMaximumEnergyGeneration());
        assertEquals(
            4096,
            tiers.get(5)
                .getMaximumEnergyGeneration());
    }

    @Test
    public void theDefaultTiersUseTheDocumentedCapacityRamp() {
        List<TierConfiguration> tiers = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS;

        assertEquals(
            50 * MetricUnits.KILO,
            tiers.get(0)
                .getCapacity());
        assertEquals(
            250 * MetricUnits.KILO,
            tiers.get(1)
                .getCapacity());
        assertEquals(
            850 * MetricUnits.KILO,
            tiers.get(2)
                .getCapacity());
        assertEquals(
            4 * MetricUnits.MEGA,
            tiers.get(3)
                .getCapacity());
        assertEquals(
            16 * MetricUnits.MEGA,
            tiers.get(4)
                .getCapacity());
        assertEquals(
            60 * MetricUnits.MEGA,
            tiers.get(5)
                .getCapacity());
    }

    @Test
    public void theDefaultTiersTransferEightTimesTheirGeneration() {
        for (TierConfiguration tier : ModConfiguration.DEFAULT_TIER_CONFIGURATIONS) {
            assertEquals(tier.getMaximumEnergyGeneration() * 8, tier.getMaximumEnergyTransfer());
        }
    }

    @Test
    public void everyDefaultTierIsStrictlyBetterThanThePreviousOne() {
        List<TierConfiguration> tiers = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS;

        for (int i = 1; i < tiers.size(); ++i) {
            TierConfiguration previous = tiers.get(i - 1);
            TierConfiguration current = tiers.get(i);
            assertTrue(
                "tier " + i + " must generate more than tier " + (i - 1),
                current.getMaximumEnergyGeneration() > previous.getMaximumEnergyGeneration());
            assertTrue(
                "tier " + i + " must transfer more than tier " + (i - 1),
                current.getMaximumEnergyTransfer() > previous.getMaximumEnergyTransfer());
            assertTrue(
                "tier " + i + " must store more than tier " + (i - 1),
                current.getCapacity() > previous.getCapacity());
        }
    }

    @Test
    public void theDefaultTiersStayInsideTheConfiguredUpperBound() {
        // loadTierConfiguration clamps every value to 2 GIGA, so the defaults have to fit that too.
        for (TierConfiguration tier : ModConfiguration.DEFAULT_TIER_CONFIGURATIONS) {
            assertTrue(tier.getMaximumEnergyGeneration() <= 2 * MetricUnits.GIGA);
            assertTrue(tier.getMaximumEnergyTransfer() <= 2 * MetricUnits.GIGA);
            assertTrue(tier.getCapacity() <= 2 * MetricUnits.GIGA);
        }
    }
}
