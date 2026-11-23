package com.mrfuzzihead.solarflux.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import net.minecraftforge.common.config.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.support.MapConfigSource;
import com.mrfuzzihead.solarflux.support.TestFixture;

/**
 * Exercises the real loaders through {@link ConfigSource}; until the seam existed these methods could only run inside
 * an FML launch.
 */
public class ModConfigurationLoadTest {

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
    }

    @After
    public void tearDown() {
        // The loaders write the same statics the rest of the suite reads.
        TestFixture.applyDefaultConfiguration();
    }

    @Test
    public void aMapSourceDrivesTheRestartSafeValues() {
        MapConfigSource source = new MapConfigSource().put(Configuration.CATEGORY_GENERAL, "SolarPanelHeight", 0.5F)
            .put(Configuration.CATEGORY_GENERAL, "BalanceEnergy", false)
            .put(Configuration.CATEGORY_GENERAL, "RainProductionFactor", 0.25F)
            .put(Configuration.CATEGORY_GENERAL, "DispenserUpdateRate", 7);

        ModConfiguration.loadRestartSafeConfiguration(source);

        assertEquals(0.5F, ModConfiguration.getSolarPanelHeight(), 0.0F);
        assertFalse(ModConfiguration.doesAutoBalanceEnergy());
        assertEquals(0.25F, ModConfiguration.getRainGenerationFactor(), 0.0F);
        assertEquals(7, ModConfiguration.getDispenserUpdateRate());
    }

    @Test
    public void anEmptySourceKeepsTheDocumentedDefaults() {
        ModConfiguration.loadRestartSafeConfiguration(new MapConfigSource());

        assertEquals(0.375F, ModConfiguration.getSolarPanelHeight(), 0.0F);
        assertTrue(ModConfiguration.doesAutoBalanceEnergy());
        assertEquals(40, ModConfiguration.getDispenserUpdateRate());
    }

    @Test
    public void theUpgradeValuesComeFromTheSource() {
        MapConfigSource source = new MapConfigSource().put("upgrades", "EfficiencyUpgradeMax", 3)
            .put("upgrades", "EfficiencyUpgradeIncrease", 0.2F)
            .put("upgrades", "FurnaceUpgradeHeatingConsumption", 16);

        ModConfiguration.loadUpgradesConfiguration(source);

        assertEquals(3, ModConfiguration.getEfficiencyUpgradeMax());
        assertEquals(0.2F, ModConfiguration.getEfficiencyUpgradeIncrease(), 0.0F);
        assertEquals(16, ModConfiguration.getFurnaceUpgradeHeatingConsumption());
    }

    @Test
    public void aGapInActiveFlagsKeepsEveryIndex() {
        MapConfigSource source = new MapConfigSource();
        for (int tier = 0; tier < 6; ++tier) {
            source.put("solar_panel_tier" + tier, "Active", tier != 1);
        }

        List<TierConfiguration> tiers = ModConfiguration.parseTierConfigurations(source);

        assertEquals(6, tiers.size());
        assertTrue(
            tiers.get(0)
                .isActive());
        assertFalse(
            tiers.get(1)
                .isActive());
        assertTrue(
            tiers.get(2)
                .isActive());
        assertEquals(
            ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(0)
                .getCapacity(),
            tiers.get(0)
                .getCapacity());
    }

    @Test
    public void aTierBeyondTheDefaultsIsReadOnlyWhenItsCategoryExists() {
        MapConfigSource source = new MapConfigSource();
        for (int tier = 0; tier < 6; ++tier) {
            source.put("solar_panel_tier" + tier, "Active", true);
        }
        source.put("solar_panel_tier6", "Active", true);

        List<TierConfiguration> tiers = ModConfiguration.parseTierConfigurations(source);

        assertEquals(7, tiers.size());
        TierConfiguration last = tiers.get(6);
        TierConfiguration base = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(5);
        assertTrue(last.isActive());
        assertEquals((int) (base.getMaximumEnergyGeneration() * 2.0), last.getMaximumEnergyGeneration());
        assertEquals((int) (base.getMaximumEnergyTransfer() * 2.0), last.getMaximumEnergyTransfer());
        assertEquals((int) (base.getCapacity() * 1.2), last.getCapacity());
    }
}
