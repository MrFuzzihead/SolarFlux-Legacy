package com.mrfuzzihead.solarflux.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mockito.MockedStatic;

import com.mrfuzzihead.solarflux.core.utility.ClientUtils;
import com.mrfuzzihead.solarflux.core.utility.Color;
import com.mrfuzzihead.solarflux.support.TestFixture;
import com.mrfuzzihead.solarflux.utility.Lang;

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
    public void theTooltipAsksForShiftWhenItIsNotHeld() {
        UpgradeItem item = new UpgradeItem("upgradeEfficiency", 8, Arrays.asList("detail"));
        List<String> tooltip = new ArrayList<>();

        try (MockedStatic<ClientUtils> shift = mockStatic(ClientUtils.class)) {
            shift.when(ClientUtils::isShiftKeyDown)
                .thenReturn(false);
            item.addInformation(TestFixture.stack(item, 1, 0), null, tooltip, false);
        }

        assertEquals(1, tooltip.size());
        assertTrue(
            tooltip.get(0)
                .contains(Lang.localise("hold.for.info")));
    }

    @Test
    public void theTooltipShowsTheUpgradeDetailsWhenShiftIsHeld() {
        UpgradeItem item = new UpgradeItem("upgradeEfficiency", 8, Arrays.asList("detail one", "detail two"));
        List<String> tooltip = new ArrayList<>();

        try (MockedStatic<ClientUtils> shift = mockStatic(ClientUtils.class)) {
            shift.when(ClientUtils::isShiftKeyDown)
                .thenReturn(true);
            item.addInformation(TestFixture.stack(item, 1, 0), null, tooltip, false);
        }

        assertEquals(4, tooltip.size());
        assertEquals(Color.AQUA + Lang.localise("solar.panel.upgrade") + Color.GREY, tooltip.get(0));
        assertEquals("detail one", tooltip.get(1));
        assertEquals("detail two", tooltip.get(2));
        assertEquals(Lang.localise("maximum") + " 8", tooltip.get(3));
    }
}
