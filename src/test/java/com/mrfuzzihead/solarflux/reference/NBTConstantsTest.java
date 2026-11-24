package com.mrfuzzihead.solarflux.reference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NBTConstantsTest {

    @Test
    public void everySaveKeyCarriesTheModPrefix() {
        for (String key : new String[] { NBTConstants.TIER_INDEX, NBTConstants.ITEMS, NBTConstants.SLOT,
            NBTConstants.ENERGY }) {
            assertTrue(key, key.startsWith("SF"));
        }
    }

    @Test
    public void theTooltipKeysUseTheirOwnNamespace() {
        // Item tooltips are written into the item's NBT; keeping them apart means an inventory read cannot pick one up
        // by accident.
        for (String key : new String[] { NBTConstants.TOOLTIP_UPGRADE_COUNT, NBTConstants.TOOLTIP_TRANSFER_RATE,
            NBTConstants.TOOLTIP_CAPACITY }) {
            assertTrue(key, key.startsWith("SFTL"));
        }
    }

    @Test
    public void theKeysAreTheHistoricalOnes() {
        // Changing any of these silently invalidates every saved world, so they are pinned here.
        assertEquals("SFTierIndex", NBTConstants.TIER_INDEX);
        assertEquals("SFItems", NBTConstants.ITEMS);
        assertEquals("SFSlot", NBTConstants.SLOT);
        assertEquals("SFEnergy", NBTConstants.ENERGY);
        assertEquals("SFTLUpgradeCount", NBTConstants.TOOLTIP_UPGRADE_COUNT);
        assertEquals("SFTLTransferRate", NBTConstants.TOOLTIP_TRANSFER_RATE);
        assertEquals("SFTLCapacity", NBTConstants.TOOLTIP_CAPACITY);
    }

    @Test
    public void theKeysAreAllDistinct() {
        String[] keys = { NBTConstants.TIER_INDEX, NBTConstants.ITEMS, NBTConstants.SLOT, NBTConstants.ENERGY,
            NBTConstants.TOOLTIP_UPGRADE_COUNT, NBTConstants.TOOLTIP_TRANSFER_RATE, NBTConstants.TOOLTIP_CAPACITY };
        java.util.Set<String> unique = new java.util.HashSet<>(java.util.Arrays.asList(keys));

        assertEquals(keys.length, unique.size());
    }
}
