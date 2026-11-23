package com.nauktis.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Before;
import org.junit.Test;

import com.nauktis.solarflux.blocks.modules.SimpleEnergyDispenserModule;
import com.nauktis.solarflux.blocks.modules.TraversalEnergyDispenserModule;
import com.nauktis.solarflux.config.ModConfiguration;
import com.nauktis.solarflux.core.utility.MetricUnits;
import com.nauktis.solarflux.init.ModItems;
import com.nauktis.solarflux.reference.NBTConstants;
import com.nauktis.solarflux.support.TestFixture;

public class SolarPanelTileEntityTest {

    private static final int TIER_TRANSFER = 16;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();
    }

    private SolarPanelTileEntity newPanel(int pTier) {
        return new SolarPanelTileEntity(pTier);
    }

    /**
     * Changes a slot the way the game does: {@code Slot.onSlotChanged} calls {@code markDirty()} on the tile entity,
     * which is what rebuilds the upgrade cache. {@code BaseInventory.markDirty()} is a no-op, so writing straight to
     * the
     * inventory does not refresh anything.
     */
    private static void setSlot(SolarPanelTileEntity pPanel, int pSlot, ItemStack pStack) {
        pPanel.setInventorySlotContents(pSlot, pStack);
        pPanel.markDirty();
    }

    @Test
    public void theDefaultConstructorStartsAtTierZero() {
        SolarPanelTileEntity panel = newPanel(0);

        assertEquals("Solar Panel", panel.getInventoryName());
        assertEquals(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(0), panel.getTierConfiguration());
    }

    @Test
    public void theConstructorAppliesTheTierCapacityAndTransferRate() {
        SolarPanelTileEntity panel = newPanel(2);

        assertEquals(850_000, panel.getMaxEnergyStored());
        assertEquals(
            64 * 8,
            panel.getEnergyStorage()
                .getMaxTransferExtract());
        assertEquals(0, panel.getEnergyStored());
    }

    @Test
    public void everyTierReadsItsOwnConfiguration() {
        for (int tier = 0; tier < ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size(); ++tier) {
            SolarPanelTileEntity panel = newPanel(tier);
            assertSame(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(tier), panel.getTierConfiguration());
            assertEquals(
                ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(tier)
                    .getCapacity(),
                panel.getMaxEnergyStored());
        }
    }

    @Test
    public void energyCanBeExtractedFromEverySideButTheTop() {
        SolarPanelTileEntity panel = newPanel(0);

        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            assertEquals(direction.name(), direction != ForgeDirection.UP, panel.canConnectEnergy(direction));
        }
    }

    @Test
    public void storedEnergyIsReadThroughTheStorage() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(1234);

        assertEquals(1234, panel.getEnergyStored());
        assertEquals(1234, panel.getEnergyStored(ForgeDirection.DOWN));
        assertEquals(1234, panel.getEnergyStored(ForgeDirection.UP));
    }

    @Test
    public void setEnergyStoredClampsToTheTierCapacity() {
        SolarPanelTileEntity panel = newPanel(0);

        panel.setEnergyStored(Integer.MAX_VALUE);

        assertEquals(50_000, panel.getEnergyStored());
    }

    @Test
    public void extractEnergyIsLimitedByTheStorageTransferRateAndNotByTheRequest() {
        // Current behaviour, pinned on purpose: extractEnergy ignores pMaxExtract and always releases as much as the
        // storage allows. The game test covers the equivalent path through a real energy receiver.
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(1000);

        assertEquals(TIER_TRANSFER, panel.extractEnergy(ForgeDirection.DOWN, 1, false));
        assertEquals(1000 - TIER_TRANSFER, panel.getEnergyStored());
    }

    @Test
    public void simulatedExtractionReleasesNothing() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(1000);

        assertEquals(TIER_TRANSFER, panel.extractEnergy(ForgeDirection.DOWN, 100, true));
        assertEquals(1000, panel.getEnergyStored());
    }

    @Test
    public void theEnergyFractionScalesToTheRequestedResolution() {
        SolarPanelTileEntity panel = newPanel(0);

        assertEquals(0, panel.getScaledEnergyStoredFraction(100));
        assertEquals(0, panel.getPercentageEnergyStored());

        panel.setEnergyStored(25_000);
        assertEquals(50, panel.getScaledEnergyStoredFraction(100));
        assertEquals(50, panel.getPercentageEnergyStored());
        assertEquals(125, panel.getScaledEnergyStoredFraction(250));

        panel.setEnergyStored(50_000);
        assertEquals(100, panel.getPercentageEnergyStored());
        assertEquals(1000, panel.getScaledEnergyStoredFraction(1000));
    }

    @Test
    public void anEmptyPanelHasNoUpgrades() {
        SolarPanelTileEntity panel = newPanel(0);

        assertEquals(0, panel.getTotalUpgradeInstalled());
        assertEquals(0, panel.getUpgradeCount(ModItems.mUpgradeEfficiency));
        assertTrue(
            panel.getInventory()
                .isEmpty());
        assertEquals(SolarPanelTileEntity.INVENTORY_SIZE, panel.getSizeInventory());
    }

    @Test
    public void aNullItemHasNoUpgradeCountAndNoRoom() {
        SolarPanelTileEntity panel = newPanel(0);

        assertEquals(0, panel.getUpgradeCount(null));
        assertEquals(0, panel.additionalUpgradeAllowed(null));
    }

    @Test
    public void upgradesAreCountedAcrossAllSlots() {
        SolarPanelTileEntity panel = newPanel(0);

        setSlot(panel, 0, TestFixture.stack(ModItems.mUpgradeEfficiency, 2, 0));
        setSlot(panel, 1, TestFixture.stack(ModItems.mUpgradeEfficiency, 3, 0));

        assertEquals(5, panel.getUpgradeCount(ModItems.mUpgradeEfficiency));
        assertEquals(5, panel.getTotalUpgradeInstalled());
        assertEquals(3, panel.additionalUpgradeAllowed(TestFixture.stack(ModItems.mUpgradeEfficiency, 1, 0)));
    }

    @Test
    public void nonUpgradeItemsAreNeitherCountedNorAccepted() {
        SolarPanelTileEntity panel = newPanel(0);
        ItemStack ordinary = TestFixture.stack(1, 0);

        setSlot(panel, 2, ordinary);

        assertEquals(0, panel.getTotalUpgradeInstalled());
        assertFalse(panel.isItemValidForSlot(2, ordinary));
    }

    @Test
    public void isItemValidForSlotRejectsLargeUpgradeStacks() {
        SolarPanelTileEntity panel = newPanel(0);

        assertTrue(panel.isItemValidForSlot(0, TestFixture.stack(ModItems.mUpgradeCapacity, 16, 0)));
        assertFalse(panel.isItemValidForSlot(0, TestFixture.stack(ModItems.mUpgradeCapacity, 17, 0)));
    }

    @Test
    public void theCapacityUpgradeGrowsTheStorage() {
        SolarPanelTileEntity panel = newPanel(0);
        int base = panel.getMaxEnergyStored();

        setSlot(panel, 0, TestFixture.stack(ModItems.mUpgradeCapacity, 1, 0));

        // 50000 * (1 + 0.1 * 1^1) = 55000
        assertEquals(base + base / 10, panel.getMaxEnergyStored());
    }

    @Test
    public void theTransferRateUpgradeGrowsBothTransferDirections() {
        SolarPanelTileEntity panel = newPanel(0);

        setSlot(panel, 0, TestFixture.stack(ModItems.mUpgradeTransferRate, 1, 0));

        // 16 * (1 + 0.1 * 1^0.9) = 17.6 -> 17
        assertEquals(
            17,
            panel.getEnergyStorage()
                .getMaxTransferReceive());
        assertEquals(
            17,
            panel.getEnergyStorage()
                .getMaxTransferExtract());
    }

    @Test
    public void removingAnUpgradeRestoresTheTierValues() {
        SolarPanelTileEntity panel = newPanel(0);
        setSlot(panel, 0, TestFixture.stack(ModItems.mUpgradeCapacity, 1, 0));
        assertTrue(panel.getMaxEnergyStored() > 50_000);

        setSlot(panel, 0, null);

        assertEquals(50_000, panel.getMaxEnergyStored());
        assertEquals(
            TIER_TRANSFER,
            panel.getEnergyStorage()
                .getMaxTransferExtract());
    }

    @Test
    public void theTraversalUpgradeSwapsTheEnergyDispenserModule() {
        SolarPanelTileEntity panel = newPanel(0);
        assertTrue(TestFixture.getField(panel, "mEnergyDispenserModule") instanceof SimpleEnergyDispenserModule);

        setSlot(panel, 0, TestFixture.stack(ModItems.mUpgradeTraversal, 1, 0));
        assertTrue(TestFixture.getField(panel, "mEnergyDispenserModule") instanceof TraversalEnergyDispenserModule);

        setSlot(panel, 0, null);
        assertTrue(TestFixture.getField(panel, "mEnergyDispenserModule") instanceof SimpleEnergyDispenserModule);
    }

    @Test
    public void atTickRateOnlyMatchesOnAlignedTicks() {
        SolarPanelTileEntity panel = newPanel(0);
        World world = org.mockito.Mockito.mock(World.class);
        TestFixture.setWorld(panel, world);

        int alignedWorldTime = -1;
        for (int time = 0; time < 40; ++time) {
            org.mockito.Mockito.when(world.getTotalWorldTime())
                .thenReturn((long) time);
            if (panel.atTickRate(20)) {
                alignedWorldTime = time;
                break;
            }
        }

        assertTrue("some tick in the first 20 in-game ticks must align", alignedWorldTime >= 0);

        org.mockito.Mockito.when(world.getTotalWorldTime())
            .thenReturn((long) alignedWorldTime);
        assertTrue(panel.atTickRate(20));

        org.mockito.Mockito.when(world.getTotalWorldTime())
            .thenReturn((long) alignedWorldTime + 1);
        assertFalse(panel.atTickRate(20));
    }

    @Test
    public void thePanelDataSurvivesAnNbtRoundTrip() {
        SolarPanelTileEntity panel = newPanel(4);
        panel.setEnergyStored(4321);
        NBTTagCompound tag = new NBTTagCompound();

        panel.addDataToNBT(tag);

        assertEquals(4, tag.getInteger(NBTConstants.TIER_INDEX));
        assertEquals(4321, tag.getInteger(NBTConstants.ENERGY));

        SolarPanelTileEntity restored = newPanel(0);
        restored.loadDataFromNBT(tag);

        assertSame(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(4), restored.getTierConfiguration());
        assertEquals(4321, restored.getEnergyStored());
        assertEquals(16 * MetricUnits.MEGA, restored.getMaxEnergyStored());
    }

    @Test
    public void aFreshPanelSerialisesItsTierZeroData() {
        SolarPanelTileEntity panel = newPanel(0);
        NBTTagCompound tag = new NBTTagCompound();

        panel.addDataToNBT(tag);

        assertEquals(0, tag.getInteger(NBTConstants.TIER_INDEX));
        assertEquals(0, tag.getInteger(NBTConstants.ENERGY));
        assertNotNull(tag.getTag(NBTConstants.ITEMS));
    }

    @Test
    public void toStringReportsTheProductionAndStorage() {
        SolarPanelTileEntity panel = newPanel(0);

        String text = panel.toString();

        assertTrue(text, text.contains("MaxProduction=2"));
        assertTrue(text, text.contains("energyStorage"));
    }
}
