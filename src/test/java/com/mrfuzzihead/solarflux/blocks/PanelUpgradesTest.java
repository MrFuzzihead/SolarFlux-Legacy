package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.core.inventory.BaseInventory;
import com.mrfuzzihead.solarflux.items.UpgradeItem;
import com.mrfuzzihead.solarflux.support.TestFixture;

public class PanelUpgradesTest {

    private BaseInventory mInventory;
    private PanelUpgrades mUpgrades;
    private UpgradeItem mEfficiency;
    private UpgradeItem mCapacity;

    @Before
    public void setUp() {
        mInventory = new BaseInventory("test", 5);
        mUpgrades = new PanelUpgrades(mInventory);
        mEfficiency = new UpgradeItem("efficiency", 8, new ArrayList<String>());
        mCapacity = new UpgradeItem("capacity", 16, new ArrayList<String>());
    }

    @Test
    public void countsAreSummedAcrossSlots() {
        mInventory.setInventorySlotContents(0, new ItemStack(mEfficiency, 3));
        mInventory.setInventorySlotContents(1, new ItemStack(mEfficiency, 2));
        mInventory.setInventorySlotContents(2, new ItemStack(mCapacity, 1));

        assertTrue(mUpgrades.refreshIfNeeded());

        assertEquals(5, mUpgrades.getCount(mEfficiency));
        assertEquals(1, mUpgrades.getCount(mCapacity));
        assertEquals(6, mUpgrades.getTotal());
    }

    @Test
    public void anUnchangedInventoryIsNotRescanned() {
        mInventory.setInventorySlotContents(0, new ItemStack(mEfficiency, 3));

        assertTrue(mUpgrades.refreshIfNeeded());
        assertFalse(mUpgrades.refreshIfNeeded());
        assertFalse(mUpgrades.refreshIfNeeded());
    }

    @Test
    public void anInPlaceSizeChangeIsDetected() {
        ItemStack stack = new ItemStack(mEfficiency, 1);
        mInventory.setInventorySlotContents(0, stack);
        assertTrue(mUpgrades.refreshIfNeeded());

        stack.stackSize = 4;

        assertTrue(mUpgrades.refreshIfNeeded());
        assertEquals(4, mUpgrades.getCount(mEfficiency));
    }

    @Test
    public void replacingAStackIsDetected() {
        mInventory.setInventorySlotContents(0, new ItemStack(mEfficiency, 1));
        assertTrue(mUpgrades.refreshIfNeeded());

        mInventory.setInventorySlotContents(0, new ItemStack(mCapacity, 2));

        assertTrue(mUpgrades.refreshIfNeeded());
        assertEquals(0, mUpgrades.getCount(mEfficiency));
        assertEquals(2, mUpgrades.getCount(mCapacity));
    }

    @Test
    public void nonUpgradesAreIgnored() {
        mInventory.setInventorySlotContents(0, TestFixture.stack(64, 0));

        assertTrue(mUpgrades.refreshIfNeeded());

        assertEquals(0, mUpgrades.getTotal());
        assertEquals(0, mUpgrades.getCount(null));
    }
}
