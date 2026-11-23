package com.mrfuzzihead.solarflux.items;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;

import com.mrfuzzihead.solarflux.support.TestFixture;

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
}
