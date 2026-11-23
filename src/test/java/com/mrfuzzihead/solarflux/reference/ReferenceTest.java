package com.mrfuzzihead.solarflux.reference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mrfuzzihead.solarflux.Tags;

public class ReferenceTest {

    @Test
    public void theModIdentifiersMatchTheFileNameOfTheLangFiles() {
        assertEquals("SolarFlux", Reference.MOD_ID);
        assertEquals("SolarFlux", Reference.MOD_NAME);
    }

    @Test
    public void theVersionComesFromTheGeneratedTagsClass() {
        assertEquals(Tags.VERSION, Reference.VERSION);
        assertTrue("the build must inject a version", Tags.VERSION != null && !Tags.VERSION.isEmpty());
    }

    @Test
    public void theGuiFactoryPointsAtTheClassTheModAnnotationLoads() throws Exception {
        assertEquals("com.mrfuzzihead.solarflux.config.ConfigurationGuiFactory", Reference.GUI_FACTORY);
        Class.forName(Reference.GUI_FACTORY);
    }

    @Test
    public void theOptionalIntegrationModIdsAreTheForgeOnes() {
        assertEquals("CoFHCore", Reference.COFH_CORE_MOD_ID);
        assertEquals("ThermalFoundation", Reference.THERMAL_FOUNDATION_MOD_ID);
        assertEquals("ThermalExpansion", Reference.THERMAL_EXPANSION_MOD_ID);
    }
}
