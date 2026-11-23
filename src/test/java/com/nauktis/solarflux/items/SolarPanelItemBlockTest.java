package com.nauktis.solarflux.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Before;
import org.junit.Test;

import com.nauktis.solarflux.blocks.SolarPanelBlock;
import com.nauktis.solarflux.config.ModConfiguration;
import com.nauktis.solarflux.core.block.icon.SingleIconHandler;
import com.nauktis.solarflux.core.utility.MetricUnits;
import com.nauktis.solarflux.reference.NBTConstants;
import com.nauktis.solarflux.support.TestFixture;

public class SolarPanelItemBlockTest {

    private SolarPanelItemBlock item;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        item = new SolarPanelItemBlock(
            new SolarPanelBlock("solar0", new SingleIconHandler("SolarFlux", "solar0_0"), 0));
    }

    private List<String> tooltipFor(ItemStack pStack) {
        List<String> lines = new ArrayList<>();
        item.addInformation(pStack, null, lines, false);
        return lines;
    }

    @Test
    public void aPlainPanelListsCapacityGenerationAndTransfer() {
        List<String> lines = tooltipFor(TestFixture.stack(item, 1, 0));

        assertEquals(3, lines.size());
        assertTrue(
            lines.toString(),
            lines.get(0)
                .contains("50,000"));
        assertTrue(
            lines.toString(),
            lines.get(1)
                .contains("2"));
        assertTrue(
            lines.toString(),
            lines.get(2)
                .contains("16"));
    }

    @Test
    public void aChargeTagAddsAStoredEnergyLine() {
        ItemStack stack = TestFixture.stack(item, 1, 0);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBTConstants.ENERGY, 12345);

        List<String> lines = tooltipFor(stack);

        assertEquals(4, lines.size());
        assertTrue(
            lines.toString(),
            lines.toString()
                .contains("12,345"));
    }

    @Test
    public void anUpgradeTagAddsAnUpgradeLine() {
        ItemStack stack = TestFixture.stack(item, 1, 0);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBTConstants.TOOLTIP_UPGRADE_COUNT, 3);

        List<String> lines = tooltipFor(stack);

        assertTrue(
            lines.toString(),
            lines.toString()
                .contains("3"));
    }

    @Test
    public void theStoredCapacityOverridesTheTierValue() {
        ItemStack stack = TestFixture.stack(item, 1, 0);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBTConstants.TOOLTIP_CAPACITY, 4 * MetricUnits.MEGA);

        List<String> lines = tooltipFor(stack);

        assertTrue(
            lines.toString(),
            lines.get(0)
                .contains("4,000,000"));
    }

    @Test
    public void theStoredTransferRateOverridesTheTierValue() {
        ItemStack stack = TestFixture.stack(item, 1, 0);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(NBTConstants.TOOLTIP_TRANSFER_RATE, 4096);

        List<String> lines = tooltipFor(stack);

        assertTrue(
            lines.toString(),
            lines.get(2)
                .contains("4,096"));
        assertFalse(
            lines.toString(),
            lines.get(2)
                .contains(",16"));
    }

    @Test
    public void aTagEqualToTheTierValueChangesNothing() {
        ItemStack stack = TestFixture.stack(item, 1, 0);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setInteger(
                NBTConstants.TOOLTIP_CAPACITY,
                ModConfiguration.getTierConfiguration(0)
                    .getCapacity());

        assertTrue(
            tooltipFor(stack).get(0)
                .contains("50,000"));
    }

    @Test
    public void aPlainPanelHasNoStoredEnergyLine() {
        // hasNbtTag guards on the compound being present, so an untagged stack must not add a line.
        assertEquals(3, tooltipFor(TestFixture.stack(item, 1, 0)).size());
    }
}
