package com.mrfuzzihead.solarflux.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.support.TestFixture;

/**
 * Reads back the configuration surface the game sees.
 *
 * <p>
 * {@code ModConfiguration.initialize} cannot run headlessly (see {@link TestFixture}), so this pins the getters to the
 * documented defaults and checks that every accessor is actually wired to a field. A getter that silently returns the
 * wrong field would otherwise only show up in game.
 * </p>
 */
public class ModConfigurationSurfaceTest {

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
    }

    @Test
    public void thePanelGeometryDefaultsToAPartialBlock() {
        assertEquals(0.375F, ModConfiguration.getSolarPanelHeight(), 0.0F);
        assertFalse(ModConfiguration.isSolarPanelFullBlock());
        assertTrue(ModConfiguration.useConnectedTextures());
    }

    @Test
    public void aFullHeightConfigurationIsReportedAsAFullBlock() {
        TestFixture.setStatic(ModConfiguration.class, "mSolarPanelHeight", 1.0F);

        assertTrue(ModConfiguration.isSolarPanelFullBlock());
    }

    @Test
    public void theGeneralTogglesMatchTheirDocumentedDefaults() {
        assertTrue(ModConfiguration.doesAutoBalanceEnergy());
        assertTrue(ModConfiguration.useThermalExpansionRecipes());
        assertTrue(ModConfiguration.doesKeepEnergyWhenDismantled());
        assertTrue(ModConfiguration.doesKeepInventoryWhenDismantled());
        assertFalse(ModConfiguration.infoOnSneakClick());
        assertEquals(40, ModConfiguration.getDispenserUpdateRate());
    }

    @Test
    public void theWeatherFactorsDefaultToJustUnderHalf() {
        assertEquals(0.4F, ModConfiguration.getRainGenerationFactor(), 0.0F);
        assertEquals(0.4F, ModConfiguration.getThunderGenerationFactor(), 0.0F);
    }

    @Test
    public void theEfficiencyUpgradeDefaultsMatchTheDocumentedValues() {
        assertTrue(ModConfiguration.isEfficiencyUpgradeActive());
        assertEquals(0.05F, ModConfiguration.getEfficiencyUpgradeIncrease(), 0.0F);
        assertEquals(0.9F, ModConfiguration.getEfficiencyUpgradeReturnsToScale(), 0.0F);
        assertEquals(8, ModConfiguration.getEfficiencyUpgradeMax());
    }

    @Test
    public void theLowLightUpgradeDefaultsMatchTheDocumentedValues() {
        assertTrue(ModConfiguration.isLowLightUpgradeActive());
        assertEquals(8, ModConfiguration.getLowLightUpgradeMax());
    }

    @Test
    public void theTraversalUpgradeDefaultsMatchTheDocumentedValues() {
        assertTrue(ModConfiguration.isTraversalUpgradeActive());
        assertEquals(1, ModConfiguration.getTraversalUpgradeIncrease());
        assertEquals(25, ModConfiguration.getTraversalUpgradeUpdateRate());
        assertEquals(64, ModConfiguration.getTraversalUpgradeMax());
    }

    @Test
    public void theTransferRateUpgradeDefaultsMatchTheDocumentedValues() {
        assertTrue(ModConfiguration.isTransferRateUpgradeActive());
        assertEquals(0.1F, ModConfiguration.getTransferRateUpgradeIncrease(), 0.0F);
        assertEquals(0.9F, ModConfiguration.getTransferRateUpgradeReturnsToScale(), 0.0F);
        assertEquals(8, ModConfiguration.getTransferRateUpgradeMax());
    }

    @Test
    public void theCapacityUpgradeDefaultsMatchTheDocumentedValues() {
        assertTrue(ModConfiguration.isCapacityUpgradeActive());
        assertEquals(0.1F, ModConfiguration.getCapacityUpgradeIncrease(), 0.0F);
        assertEquals(1.0F, ModConfiguration.getCapacityUpgradeReturnsToScale(), 0.0F);
        assertEquals(16, ModConfiguration.getCapacityUpgradeMax());
    }

    @Test
    public void theFurnaceUpgradeDefaultsMatchTheDocumentedValues() {
        assertTrue(ModConfiguration.isFurnaceUpgradeActive());
        assertEquals(8, ModConfiguration.getFurnaceUpgradeHeatingConsumption());
    }

    @Test
    public void theTierListIsHandedOutDirectly() {
        assertSame(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS, ModConfiguration.getTierConfigurations());
        assertSame(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(3), ModConfiguration.getTierConfiguration(3));
        assertEquals(
            6,
            ModConfiguration.getTierConfigurations()
                .size());
    }

    @Test
    public void theForgeConfigurationIsAbsentBeforeInitialization() {
        // initialize() is what assigns it, and that needs a Forge launch.
        assertNull(ModConfiguration.getConfiguration());
    }

    @Test
    public void anOutOfRangeTierIndexFallsBackToTheLastTier() {
        int last = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size() - 1;
        TierConfiguration lastTier = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(last);

        assertSame(lastTier, ModConfiguration.getTierConfiguration(last + 1));
        assertSame(lastTier, ModConfiguration.getTierConfiguration(1000));
    }

    @Test
    public void aNegativeTierIndexFallsBackToTheFirstTier() {
        assertSame(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(0), ModConfiguration.getTierConfiguration(-1));
        assertSame(
            ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(0),
            ModConfiguration.getTierConfiguration(Integer.MIN_VALUE));
    }
}
