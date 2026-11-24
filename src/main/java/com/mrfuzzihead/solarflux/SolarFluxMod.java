package com.mrfuzzihead.solarflux;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.gui.GuiHandler;
import com.mrfuzzihead.solarflux.init.ModBlocks;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.init.ModRecipes;
import com.mrfuzzihead.solarflux.reference.Reference;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(
    modid = Reference.MOD_ID,
    name = Reference.MOD_NAME,
    version = Reference.VERSION,
    guiFactory = Reference.GUI_FACTORY,
    dependencies = "required-after:" + Reference.COFH_CORE_MOD_ID
        + ";after:"
        + Reference.THERMAL_EXPANSION_MOD_ID
        + ";after:"
        + Reference.THERMAL_FOUNDATION_MOD_ID)
public class SolarFluxMod {

    @Mod.Instance(Reference.MOD_ID)
    public static SolarFluxMod mInstance;

    public static final Logger log = LogManager.getLogger(Reference.MOD_ID);

    @EventHandler
    public void preInit(FMLPreInitializationEvent pEvent) {
        log.info("Pre Initialization");
        ModConfiguration.initialize(pEvent.getSuggestedConfigurationFile());
        // Legacy id first, canonical id last: TileEntity.writeToNBT uses the last mapping, so new chunks are namespaced
        // while old chunks (saved as "solar") still resolve to the same class. The registry accepts one class under
        // several ids as long as the ids differ.
        GameRegistry.registerTileEntity(SolarPanelTileEntity.class, "solar");
        GameRegistry.registerTileEntity(SolarPanelTileEntity.class, "solarflux:solar");
        ModBlocks.initialize();
        ModItems.initialize();
    }

    @EventHandler
    public void init(FMLInitializationEvent pEvent) {
        log.info("Initialization");
        ModRecipes.initialize();
        log.info("Registering GUI Handler");
        NetworkRegistry.INSTANCE.registerGuiHandler(mInstance, new GuiHandler());
    }
}
