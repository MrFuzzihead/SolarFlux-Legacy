package com.mrfuzzihead.solarflux.config;

import java.io.File;
import java.util.List;
import java.util.Objects;

import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mrfuzzihead.solarflux.SolarFluxMod;
import com.mrfuzzihead.solarflux.core.utility.MetricUnits;
import com.mrfuzzihead.solarflux.init.ModBlocks;
import com.mrfuzzihead.solarflux.reference.Reference;

import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class ModConfiguration {

    public static final List<TierConfiguration> DEFAULT_TIER_CONFIGURATIONS = ImmutableList.of(
        new TierConfiguration(2, 50 * MetricUnits.KILO),
        new TierConfiguration(2 * 8, 250 * MetricUnits.KILO),
        new TierConfiguration(2 * 8 * 4, 850 * MetricUnits.KILO),
        new TierConfiguration(2 * 8 * 4 * 4, 4 * MetricUnits.MEGA),
        new TierConfiguration(2 * 8 * 4 * 4 * 4, 16 * MetricUnits.MEGA),
        new TierConfiguration(2 * 8 * 4 * 4 * 4 * 4, 60 * MetricUnits.MEGA));
    private static final String UPGRADE_CATEGORY = "upgrades";
    /** Hard ceiling on the number of tiers the configuration may define. */
    private static final int MAX_TIERS = 16;
    private static Configuration mConfiguration;
    private static float mSolarPanelHeight;
    private static boolean mAutoBalanceEnergy;
    private static boolean mUseThermalExpansionRecipes;
    private static boolean mKeepEnergyWhenDismantled;
    private static boolean mKeepInventoryWhenDismantled;
    private static float mRainGenerationFactor;
    private static float mThunderGenerationFactor;
    private static boolean mInfoOnSneakClick;
    private static int mDispenserUpdateRate;
    private static List<TierConfiguration> mTierConfigurations;
    private static boolean mEfficiencyUpgradeActive;
    private static float mEfficiencyUpgradeIncrease;
    private static float mEfficiencyUpgradeReturnsToScale;
    private static int mEfficiencyUpgradeMax;
    private static boolean mLowLightUpgradeActive;
    private static int mLowLightUpgradeMax;
    private static boolean mTraversalUpgradeActive;
    private static int mTraversalUpgradeIncrease;
    private static int mTraversalUpgradeUpdateRate;
    private static int mTraversalUpgradeMax;
    private static boolean mTransferRateUpgradeActive;
    private static float mTransferRateUpgradeIncrease;
    private static float mTransferRateUpgradeReturnsToScale;
    private static int mTransferRateUpgradeMax;
    private static boolean mCapacityUpgradeActive;
    private static float mCapacityUpgradeIncrease;
    private static float mCapacityUpgradeReturnsToScale;
    private static int mCapacityUpgradeMax;
    private static boolean mFurnaceUpgradeActive;
    private static int mFurnaceUpgradeHeatingConsumption;
    private static boolean mConnectedTextures;
    /**
     * Bumped whenever the restart-safe values are (re)loaded. Tile entities that cache derived values compare it, so a
     * config reload is never masked by an unchanged inventory.
     */
    private static int sConfigurationVersion;
    /** The source the loaders read from; the Forge file in production, a map in tests. */
    private static ConfigSource mConfigSource;

    public static void initialize(File pConfigFile) {
        SolarFluxMod.log.info("Initialization of configuration");
        if (mConfiguration == null) {
            mConfiguration = new Configuration(pConfigFile);
        }
        mConfigSource = new ForgeConfigSource(mConfiguration);
        FMLCommonHandler.instance()
            .bus()
            .register(new ModConfiguration());
        loadConfiguration();
    }

    private static void loadConfiguration() {
        ConfigSource source = Objects.requireNonNull(mConfigSource);
        loadRestartSafeConfiguration(source);
        loadTierConfigurations(source);
        loadUpgradesConfiguration(source);
        saveConfigurationIfChanged();
    }

    /**
     * Re-applies the configuration after a runtime change.
     *
     * <p>
     * Tier and registration decisions are made at startup, so they are deliberately not re-read here: rebuilding the
     * tier list while the blocks stay registered would change the meaning of every saved tier index. Only the
     * restart-safe values and the panel bounds are refreshed.
     * </p>
     */
    public static void reload() {
        ConfigSource source = Objects.requireNonNull(mConfigSource, "reload() called before initialize()");
        loadRestartSafeConfiguration(source);
        loadUpgradesConfiguration(source);
        ModBlocks.refreshBlockConfiguration();
        SolarFluxMod.log.info(
            "Configuration reloaded. Solar panel tier and registration changes require a restart and were ignored.");
        saveConfigurationIfChanged();
    }

    static void loadRestartSafeConfiguration(ConfigSource pSource) {
        Objects.requireNonNull(pSource);
        ++sConfigurationVersion;
        mSolarPanelHeight = pSource.getFloat(
            "SolarPanelHeight",
            Configuration.CATEGORY_GENERAL,
            0.375F,
            0.01F,
            1,
            "The height of the Solar Panel blocks.");
        mAutoBalanceEnergy = pSource.getBoolean(
            "BalanceEnergy",
            Configuration.CATEGORY_GENERAL,
            true,
            "Neighbor solar panels share their energy if set to true.");
        mUseThermalExpansionRecipes = pSource.getBoolean(
            "UseThermalExpansionRecipes",
            Configuration.CATEGORY_GENERAL,
            true,
            "Use Thermal Expansion recipes.");
        mKeepEnergyWhenDismantled = pSource.getBoolean(
            "KeepEnergyWhenDismantled",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether or not the solar panels keep their internal energy when dismantled with a wrench.");
        mKeepInventoryWhenDismantled = pSource.getBoolean(
            "KeepInventoryWhenDismantled",
            Configuration.CATEGORY_GENERAL,
            true,
            "Whether or not the solar panels keep their internal inventory when dismantled with a wrench.");
        mRainGenerationFactor = pSource.getFloat(
            "RainProductionFactor",
            Configuration.CATEGORY_GENERAL,
            0.4f,
            0,
            1,
            "Factor used to reduce the energy generation during rainy weather.");
        mThunderGenerationFactor = pSource.getFloat(
            "ThunderProductionFactor",
            Configuration.CATEGORY_GENERAL,
            0.4f,
            0,
            1,
            "Factor used to reduce the energy generation during stormy weather.");
        mInfoOnSneakClick = pSource.getBoolean(
            "SneakClickInfo",
            Configuration.CATEGORY_GENERAL,
            false,
            "Display Solar Panel information on right click while sneaking.");
        mConnectedTextures = pSource.getBoolean(
            "ConnectedTextures",
            Configuration.CATEGORY_GENERAL,
            true,
            "Use connected textures for the solar panels.");
        mDispenserUpdateRate = pSource.getInt(
            "DispenserUpdateRate",
            Configuration.CATEGORY_GENERAL,
            40,
            1,
            20 * 60,
            "How often, in ticks, a solar panel looks for adjacent energy receivers. Increase to reduce lag, at the cost of slower discovery.");
    }

    private static void saveConfigurationIfChanged() {
        if (mConfiguration.hasChanged()) {
            SolarFluxMod.log.info("Configuration saved");
            mConfiguration.save();
        }
    }

    static void loadUpgradesConfiguration(ConfigSource pSource) {
        //@formatter:off

        // Efficiency upgrade
        mEfficiencyUpgradeActive = pSource.getBoolean("EfficiencyUpgradeActive", UPGRADE_CATEGORY, true, "Whether or not efficiency upgrades should be added to the game.");
        mEfficiencyUpgradeIncrease = pSource.getFloat("EfficiencyUpgradeIncrease", UPGRADE_CATEGORY, 0.05F, 0.01F, 10, "Factor by which the energy production is increased per upgrade.");
        mEfficiencyUpgradeReturnsToScale = pSource.getFloat("EfficiencyUpgradeReturnsToScale", UPGRADE_CATEGORY, 0.9F, 0.1F, 2, "Returns to scale. How does the efficiency scales as you add more upgrades. 1 is linear. Below 1 reduces the efficiency as you add upgrades, Above 1 does the opposite.");
        mEfficiencyUpgradeMax = pSource.getInt("EfficiencyUpgradeMax", UPGRADE_CATEGORY, 8, 1, 256, "Maximum number of efficiency upgrade that can be added to a single solar panel.");

        // Low light upgrade
        mLowLightUpgradeActive = pSource.getBoolean("LowLightUpgradeActive", UPGRADE_CATEGORY, true, "Whether or not low light upgrades should be added to the game.");
        mLowLightUpgradeMax = pSource.getInt("LowLightUpgradeMax", UPGRADE_CATEGORY, 8, 1, 256, "Maximum number of low light upgrade that can be added to a single solar panel.");

        // Traversal upgrade
        mTraversalUpgradeActive = pSource.getBoolean("TraversalUpgradeActive", UPGRADE_CATEGORY, true, "Whether or not traversal upgrades should be added to the game.");
        mTraversalUpgradeIncrease = pSource.getInt("TraversalUpgradeIncrease", UPGRADE_CATEGORY, 1, 1, 10, "How many extra machines can be traversed per extra upgrade.");
        mTraversalUpgradeUpdateRate = pSource.getInt("TraversalUpgradeUpdateRate", UPGRADE_CATEGORY, 25, 1, 20 * 60, "Update rate of traversal. Increase to reduce lag. But machines will be discovered slower.");
        mTraversalUpgradeMax = pSource.getInt("TraversalUpgradeMax", UPGRADE_CATEGORY, 64, 1, 256, "Maximum number of traversal upgrade that can be added to a single solar panel.");

        // Transfer upgrade
        mTransferRateUpgradeActive = pSource.getBoolean("TransferRateUpgradeActive", UPGRADE_CATEGORY, true, "Whether or not transfer rate upgrades should be added to the game.");
        mTransferRateUpgradeIncrease = pSource.getFloat("TransferRateUpgradeIncrease", UPGRADE_CATEGORY, 0.1F, 0.01F, 10, "Factor by which the transfer rate is increased per upgrade.");
        mTransferRateUpgradeReturnsToScale = pSource.getFloat("TransferRateUpgradeReturnsToScale", UPGRADE_CATEGORY, 0.9F, 0.1F, 2, "Returns to scale. How does the transfer rate scales as you add more upgrades. 1 is linear. Below 1 reduces the efficiency as you add upgrades, Above 1 does the opposite.");
        mTransferRateUpgradeMax = pSource.getInt("TransferRateUpgradeMax", UPGRADE_CATEGORY, 8, 1, 256, "Maximum number of transfer rate upgrade that can be added to a single solar panel.");

        // Capacity upgrade
        mCapacityUpgradeActive = pSource.getBoolean("CapacityUpgradeActive", UPGRADE_CATEGORY, true, "Whether or not capacity upgrades should be added to the game.");
        mCapacityUpgradeIncrease = pSource.getFloat("CapacityUpgradeIncrease", UPGRADE_CATEGORY, 0.1F, 0.01F, 10, "Factor by which the capacity is increased per upgrade.");
        mCapacityUpgradeReturnsToScale = pSource.getFloat("CapacityUpgradeReturnsToScale", UPGRADE_CATEGORY, 1, 0.1F, 2, "Returns to scale. How does the transfer rate scales as you add more upgrades. 1 is linear. Below 1 reduces the efficiency as you add upgrades, Above 1 does the opposite.");
        mCapacityUpgradeMax = pSource.getInt("CapacityUpgradeMax", UPGRADE_CATEGORY, 16, 1, 256, "Maximum number of capacity upgrade that can be added to a single solar panel.");

        // Furnace upgrade
        mFurnaceUpgradeActive = pSource.getBoolean("FurnaceUpgradeActive", UPGRADE_CATEGORY, true, "Whether or not furnace upgrades should be added to the game.");
        mFurnaceUpgradeHeatingConsumption = pSource.getInt("FurnaceUpgradeHeatingConsumption", UPGRADE_CATEGORY, 8, 1, 64000, "Amount of RF per tick used to heat up a furnace.");

        //@formatter:on
    }

    private static void loadTierConfigurations(ConfigSource pSource) {
        mTierConfigurations = parseTierConfigurations(pSource);
    }

    /**
     * Parses the tier sections of a configuration.
     *
     * <p>
     * Every tier up to the last configured one is kept, active or not: the tier index is what each panel stores in
     * NBT, so dropping an inactive entry would silently renumber every higher tier and reinterpret existing worlds. An
     * inactive tier simply gets no recipe (the block stays registered).
     * </p>
     *
     * <p>
     * Kept separate from the static state so the in-game test can feed it a crafted configuration and check a
     * gapped {@code Active} layout without touching the running server.
     * </p>
     */
    public static List<TierConfiguration> parseTierConfigurations(ConfigSource pSource) {
        Objects.requireNonNull(pSource);
        List<TierConfiguration> tiers = Lists.newArrayList();
        for (int tier = 0; tier < MAX_TIERS; ++tier) {
            TierConfiguration config = loadTierConfiguration(pSource, tier);
            if (config == null) {
                break;
            }
            tiers.add(config);
        }
        return tiers;
    }

    private static TierConfiguration loadTierConfiguration(ConfigSource pSource, int pTierNumber) {
        String category = "solar_panel_tier" + pTierNumber;
        boolean defaultTier = pTierNumber < DEFAULT_TIER_CONFIGURATIONS.size();
        // Tiers beyond the six defaults only exist if the pack author added the category. Asking otherwise would create
        // a category for every tier up to the hard cap in every fresh configuration file.
        if (!defaultTier && !pSource.hasCategory(category)) {
            return null;
        }

        boolean active = pSource.getBoolean(
            "Active",
            category,
            defaultTier,
            "Whether this tier can be crafted. Disabling a tier keeps its block (and any existing panel) and only removes the recipe; higher tiers are unaffected.");

        // Find a default config for the default values
        TierConfiguration defaultConfig;
        if (pTierNumber < DEFAULT_TIER_CONFIGURATIONS.size()) {
            defaultConfig = DEFAULT_TIER_CONFIGURATIONS.get(pTierNumber);
        } else {
            int deltaTier = pTierNumber - DEFAULT_TIER_CONFIGURATIONS.size() + 1;
            TierConfiguration lastConfig = DEFAULT_TIER_CONFIGURATIONS.get(DEFAULT_TIER_CONFIGURATIONS.size() - 1);
            defaultConfig = new TierConfiguration(
                (int) (lastConfig.getMaximumEnergyGeneration() * Math.pow(2, deltaTier)),
                (int) (lastConfig.getMaximumEnergyTransfer() * Math.pow(2, deltaTier)),
                (int) (lastConfig.getCapacity() * Math.pow(1.2, deltaTier)));
        }

        return new TierConfiguration(
            pSource.getInt(
                "MaximumEnergyGeneration",
                category,
                defaultConfig.getMaximumEnergyGeneration(),
                1,
                2 * MetricUnits.GIGA,
                "Maximum amount of RF generated per tick."),
            pSource.getInt(
                "MaximumEnergyTransfer",
                category,
                defaultConfig.getMaximumEnergyTransfer(),
                1,
                2 * MetricUnits.GIGA,
                "Maximum amount of RF transferred per tick."),
            pSource.getInt(
                "Capacity",
                category,
                defaultConfig.getCapacity(),
                1,
                2 * MetricUnits.GIGA,
                "Amount of RF that can be stored."),
            active);
    }

    public static Configuration getConfiguration() {
        return mConfiguration;
    }

    public static float getSolarPanelHeight() {
        return mSolarPanelHeight;
    }

    public static boolean isSolarPanelFullBlock() {
        return mSolarPanelHeight == 1.0F;
    }

    public static boolean doesAutoBalanceEnergy() {
        return mAutoBalanceEnergy;
    }

    public static boolean useThermalExpansionRecipes() {
        return mUseThermalExpansionRecipes;
    }

    public static boolean doesKeepEnergyWhenDismantled() {
        return mKeepEnergyWhenDismantled;
    }

    public static boolean doesKeepInventoryWhenDismantled() {
        return mKeepInventoryWhenDismantled;
    }

    public static float getRainGenerationFactor() {
        return mRainGenerationFactor;
    }

    public static float getThunderGenerationFactor() {
        return mThunderGenerationFactor;
    }

    public static boolean infoOnSneakClick() {
        return mInfoOnSneakClick;
    }

    public static int getDispenserUpdateRate() {
        return mDispenserUpdateRate;
    }

    public static int getConfigurationVersion() {
        return sConfigurationVersion;
    }

    public static List<TierConfiguration> getTierConfigurations() {
        return mTierConfigurations;
    }

    /**
     * Every category the configuration GUI should show. The GUI used to pass only the general category, which left the
     * upgrade and tier settings file-only.
     */
    public static List<IConfigElement> getConfigGuiElements() {
        Objects.requireNonNull(mConfiguration, "getConfigGuiElements() called before initialize()");
        List<IConfigElement> elements = Lists.newArrayList();
        elements.add(new ConfigElement(mConfiguration.getCategory(Configuration.CATEGORY_GENERAL)));
        if (mConfiguration.hasCategory(UPGRADE_CATEGORY)) {
            elements.add(new ConfigElement(mConfiguration.getCategory(UPGRADE_CATEGORY)));
        }
        for (int tier = 0; mConfiguration.hasCategory("solar_panel_tier" + tier); ++tier) {
            elements.add(new ConfigElement(mConfiguration.getCategory("solar_panel_tier" + tier)));
        }
        return elements;
    }

    public static int clampTierIndex(int pTierIndex) {
        int lastIndex = mTierConfigurations.size() - 1;
        if (lastIndex < 0) {
            throw new IllegalStateException("No solar panel tier is configured.");
        }
        int index = Math.max(0, Math.min(pTierIndex, lastIndex));
        if (index != pTierIndex) {
            SolarFluxMod.log.warn(
                "Solar panel tier index {} is out of range [0, {}]; using {} instead.",
                pTierIndex,
                lastIndex,
                index);
        }
        return index;
    }

    public static TierConfiguration getTierConfiguration(int pTierIndex) {
        return mTierConfigurations.get(clampTierIndex(pTierIndex));
    }

    public static boolean isEfficiencyUpgradeActive() {
        return mEfficiencyUpgradeActive;
    }

    public static float getEfficiencyUpgradeIncrease() {
        return mEfficiencyUpgradeIncrease;
    }

    public static int getEfficiencyUpgradeMax() {
        return mEfficiencyUpgradeMax;
    }

    public static float getEfficiencyUpgradeReturnsToScale() {
        return mEfficiencyUpgradeReturnsToScale;
    }

    public static boolean isLowLightUpgradeActive() {
        return mLowLightUpgradeActive;
    }

    public static int getLowLightUpgradeMax() {
        return mLowLightUpgradeMax;
    }

    public static boolean isTraversalUpgradeActive() {
        return mTraversalUpgradeActive;
    }

    public static int getTraversalUpgradeIncrease() {
        return mTraversalUpgradeIncrease;
    }

    public static int getTraversalUpgradeMax() {
        return mTraversalUpgradeMax;
    }

    public static int getTraversalUpgradeUpdateRate() {
        return mTraversalUpgradeUpdateRate;
    }

    public static boolean isTransferRateUpgradeActive() {
        return mTransferRateUpgradeActive;
    }

    public static float getTransferRateUpgradeIncrease() {
        return mTransferRateUpgradeIncrease;
    }

    public static int getTransferRateUpgradeMax() {
        return mTransferRateUpgradeMax;
    }

    public static float getTransferRateUpgradeReturnsToScale() {
        return mTransferRateUpgradeReturnsToScale;
    }

    public static boolean isCapacityUpgradeActive() {
        return mCapacityUpgradeActive;
    }

    public static float getCapacityUpgradeIncrease() {
        return mCapacityUpgradeIncrease;
    }

    public static int getCapacityUpgradeMax() {
        return mCapacityUpgradeMax;
    }

    public static float getCapacityUpgradeReturnsToScale() {
        return mCapacityUpgradeReturnsToScale;
    }

    public static boolean isFurnaceUpgradeActive() {
        return mFurnaceUpgradeActive;
    }

    public static int getFurnaceUpgradeHeatingConsumption() {
        return mFurnaceUpgradeHeatingConsumption;
    }

    public static boolean useConnectedTextures() {
        return mConnectedTextures;
    }

    @SubscribeEvent
    public void onConfigurationChangedEvent(ConfigChangedEvent.OnConfigChangedEvent pEvent) {
        if (pEvent.modID.equalsIgnoreCase(Reference.MOD_ID)) {
            reload();
        }
    }
}
