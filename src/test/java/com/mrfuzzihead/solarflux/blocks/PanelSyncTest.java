package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.mrfuzzihead.solarflux.reference.NBTConstants;

public class PanelSyncTest {

    @Test
    public void thePayloadRoundTrips() {
        NBTTagCompound tag = new NBTTagCompound();
        PanelSync.write(tag, 3, 1234, 60_000, 7, 0.5F);

        PanelSync.Data data = PanelSync.read(tag);

        assertEquals(3, data.tier());
        assertEquals(1234, data.energy());
        assertEquals(60_000, data.capacity());
        assertEquals(7, data.generation());
        assertEquals(0.5F, data.sunIntensity(), 0.0F);
    }

    @Test
    public void thePayloadHasNoInventoryOrDiskOnlyKeys() {
        NBTTagCompound tag = new NBTTagCompound();
        PanelSync.write(tag, 0, 0, 1, 0, 0.0F);

        assertFalse(tag.hasKey(NBTConstants.ITEMS));
        assertFalse(tag.hasKey(NBTConstants.FURNACE_ENERGY_BUFFER));
    }

    @Test
    public void anEmptyPayloadDecodesToZeros() {
        PanelSync.Data data = PanelSync.read(new NBTTagCompound());

        assertEquals(0, data.tier());
        assertEquals(0, data.energy());
        assertEquals(0, data.capacity());
        assertEquals(0, data.generation());
        assertEquals(0.0F, data.sunIntensity(), 0.0F);
    }
}
