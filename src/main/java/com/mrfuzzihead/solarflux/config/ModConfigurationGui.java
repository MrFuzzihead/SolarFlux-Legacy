package com.mrfuzzihead.solarflux.config;

import net.minecraft.client.gui.GuiScreen;

import com.mrfuzzihead.solarflux.reference.Reference;

import cpw.mods.fml.client.config.GuiConfig;

public class ModConfigurationGui extends GuiConfig {

    public ModConfigurationGui(GuiScreen pGuiScreen) {
        super(
            pGuiScreen,
            ModConfiguration.getConfigGuiElements(),
            Reference.MOD_ID,
            true,
            true,
            GuiConfig.getAbridgedConfigPath(
                ModConfiguration.getConfiguration()
                    .toString()));
    }
}
