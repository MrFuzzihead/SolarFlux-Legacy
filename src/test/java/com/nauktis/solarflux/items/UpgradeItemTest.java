package com.nauktis.solarflux.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;

import org.junit.Test;

import com.nauktis.solarflux.support.TestFixture;

public class UpgradeItemTest {

    @Test
    public void theMaximumPerPanelIsKept() {
        UpgradeItem upgrade = new UpgradeItem("upgradeEfficiency", 8, new ArrayList<String>());

        assertEquals(8, upgrade.getMaximumPerSolarPanel());
    }

    @Test
    public void theUnlocalizedNameIsNamespacedWithTheLowerCaseModId() {
        UpgradeItem upgrade = new UpgradeItem("upgradeEfficiency", 8, new ArrayList<String>());

        assertEquals("item.solarflux:upgradeEfficiency", upgrade.getUnlocalizedName());
        assertEquals("item.solarflux:upgradeEfficiency", upgrade.getUnlocalizedName(TestFixture.stack(upgrade, 1, 0)));
        assertEquals("solarflux", upgrade.getModId());
    }

    @Test
    public void aCraftingItemIsAnItemWithAModNamespacedName() {
        CraftingItem mirror = new CraftingItem("mirror");

        assertEquals("item.solarflux:mirror", mirror.getUnlocalizedName());
        assertEquals("solarflux", mirror.getModId());
    }

    @Test
    public void theWaferSawIsItsOwnContainerItem() {
        WaferSawItem saw = new WaferSawItem();
        ItemStack stack = TestFixture.stack(saw, 1, 0);

        assertTrue(saw.hasContainerItem(stack));
        assertFalse(saw.doesContainerItemLeaveCraftingGrid(stack));
        assertFalse(saw.isRepairable());
        assertEquals(1, stack.getMaxStackSize());
        assertEquals(5, stack.getMaxDamage());
    }

    @Test
    public void usingTheWaferSawInACraftDamagesItInsteadOfConsumingIt() {
        WaferSawItem saw = new WaferSawItem();
        ItemStack stack = TestFixture.stack(saw, 1, 0);

        ItemStack returned = saw.getContainerItem(stack);

        assertSame(stack, returned);
        assertEquals(1, returned.getItemDamage());
        assertSame(stack, saw.getContainerItem(returned));
        assertEquals(2, returned.getItemDamage());
    }
}
