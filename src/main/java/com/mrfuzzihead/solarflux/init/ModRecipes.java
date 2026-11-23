package com.mrfuzzihead.solarflux.init;

import com.mrfuzzihead.solarflux.SolarFluxMod;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.init.recipes.AbstractRecipeRegistrar;
import com.mrfuzzihead.solarflux.init.recipes.ThermalExpansionRecipeRegistrar;
import com.mrfuzzihead.solarflux.init.recipes.VanillaRecipeRegistrar;
import com.mrfuzzihead.solarflux.reference.Reference;

import cpw.mods.fml.common.Loader;

public class ModRecipes {

    private ModRecipes() {}

    public static void initialize() {
        SolarFluxMod.log.info("Registering recipes");
        AbstractRecipeRegistrar registrar = new VanillaRecipeRegistrar();
        if (ModConfiguration.useThermalExpansionRecipes() && Loader.isModLoaded(Reference.THERMAL_EXPANSION_MOD_ID)) {
            registrar = new ThermalExpansionRecipeRegistrar();
        }
        registrar.registerRecipes();
    }
}
