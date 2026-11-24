package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PanelEnergyTest {

    @Test
    public void theStorageUsesTheTierValues() {
        PanelEnergy energy = new PanelEnergy(1000, 40);

        assertEquals(
            1000,
            energy.storage()
                .getMaxEnergyStored());
        assertEquals(
            40,
            energy.storage()
                .getMaxTransferReceive());
        assertEquals(
            0,
            energy.storage()
                .getEnergyStored());
        assertEquals(0, energy.currentGeneration());
        assertEquals(0.0F, energy.sunIntensity(), 0.0F);
    }

    @Test
    public void theDisplayValuesAreKept() {
        PanelEnergy energy = new PanelEnergy(1000, 40);

        energy.setCurrentGeneration(7);
        energy.setSunIntensity(0.5F);

        assertEquals(7, energy.currentGeneration());
        assertEquals(0.5F, energy.sunIntensity(), 0.0F);
    }
}
