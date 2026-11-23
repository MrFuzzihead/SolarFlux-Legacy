package com.mrfuzzihead.solarflux.init;

import java.util.List;

import com.google.common.collect.Lists;
import com.mrfuzzihead.solarflux.SolarFluxMod;
import com.mrfuzzihead.solarflux.blocks.SolarPanelBlock;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.core.block.BaseModBlock;
import com.mrfuzzihead.solarflux.core.block.icon.ConnectedIconHandler;
import com.mrfuzzihead.solarflux.core.block.icon.IBlockIconHandler;
import com.mrfuzzihead.solarflux.core.block.icon.SingleIconHandler;
import com.mrfuzzihead.solarflux.core.block.icon.TopIconHandler;
import com.mrfuzzihead.solarflux.items.SolarPanelItemBlock;
import com.mrfuzzihead.solarflux.reference.Reference;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    private static final List<BaseModBlock> mSolarPanels = Lists.newArrayList();

    private ModBlocks() {}

    public static void initialize() {
        SolarFluxMod.log.info("Registering blocks");
        mSolarPanels.clear();
        for (int tierIndex = 0; tierIndex < ModConfiguration.getTierConfigurations()
            .size(); tierIndex++) {
            int registeredTierName = tierIndex + 1;

            // Texture
            // We need to reuse the highest texture we have available if tier is too high.
            int textureTierIndex = Math.min(tierIndex, ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size() - 1);
            IBlockIconHandler topHandler = new SingleIconHandler(Reference.MOD_ID, "solar" + textureTierIndex + "_0");

            if (ModConfiguration.useConnectedTextures()) {
                topHandler = new ConnectedIconHandler(Reference.MOD_ID, "solar" + textureTierIndex + "_");
            }

            SingleIconHandler sideHandler = new SingleIconHandler(
                Reference.MOD_ID,
                "solar" + textureTierIndex + "_side");
            TopIconHandler iconHandler = new TopIconHandler(topHandler, sideHandler);

            SolarPanelBlock block = new SolarPanelBlock("solar" + tierIndex, iconHandler, tierIndex);
            if (!ModConfiguration.getTierConfiguration(tierIndex)
                .isActive()) {
                // Inactive tiers keep their block so existing worlds and the tier indexes survive, but they are not
                // offered in the creative tab; they also get no recipe (see the recipe registrars).
                block.setCreativeTab(null);
            }
            GameRegistry.registerBlock(block, SolarPanelItemBlock.class, "solar" + registeredTierName);
            mSolarPanels.add(block);
        }
    }

    public static List<BaseModBlock> getSolarPanels() {
        return mSolarPanels;
    }

    /**
     * Re-applies the block state that is allowed to change at runtime (the panel height). Registration-time settings,
     * such as the icon handler and connected textures, still need a restart.
     */
    public static void refreshBlockConfiguration() {
        for (BaseModBlock block : mSolarPanels) {
            if (block instanceof SolarPanelBlock) {
                ((SolarPanelBlock) block).applyBlockBounds();
            }
        }
    }
}
