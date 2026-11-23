package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.blocks.modules.SimpleEnergyDispenserModule;
import com.mrfuzzihead.solarflux.blocks.modules.TraversalEnergyDispenserModule;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.config.TierConfiguration;
import com.mrfuzzihead.solarflux.core.utility.MetricUnits;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.reference.NBTConstants;
import com.mrfuzzihead.solarflux.support.TestFixture;

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

        assertEquals("container.solarflux.panel", panel.getInventoryName());
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
    public void setEnergyStoredOnlyClampsNegativeValues() {
        SolarPanelTileEntity panel = newPanel(0);

        panel.setEnergyStored(Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, panel.getEnergyStored());

        panel.setEnergyStored(-1);
        assertEquals(0, panel.getEnergyStored());
    }

    @Test
    public void isItemValidForSlotRejectsANullStack() {
        SolarPanelTileEntity panel = newPanel(0);

        assertFalse(panel.isItemValidForSlot(0, null));
    }

    @Test
    public void isItemValidForSlotFollowsThePerPanelMaximum() {
        SolarPanelTileEntity panel = newPanel(0);

        assertTrue(panel.isItemValidForSlot(0, new ItemStack(ModItems.mUpgradeEfficiency, 8)));
        assertFalse(panel.isItemValidForSlot(0, TestFixture.stack(1, 0)));

        panel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeEfficiency, 4));

        assertTrue(panel.isItemValidForSlot(1, new ItemStack(ModItems.mUpgradeEfficiency, 4)));
        assertFalse(panel.isItemValidForSlot(1, new ItemStack(ModItems.mUpgradeEfficiency, 5)));
    }

    @Test
    public void theEnergyGaugeStaysFullWhenStoredEnergyExceedsTheCapacity() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(2 * panel.getMaxEnergyStored());

        assertEquals(100, panel.getScaledEnergyStoredFraction(100));
        assertEquals(100, panel.getPercentageEnergyStored());
    }

    @Test
    public void generationIsNotCappedByTheTransferRate() {
        SolarPanelTileEntity panel = newPanel(0);
        int generation = 2 * panel.getEnergyStorage()
            .getMaxTransferReceive();

        panel.setCurrentEnergyGeneration(generation);
        panel.generateEnergy();

        assertEquals(generation, panel.getEnergyStored());
    }

    @Test
    public void generationIsStillCappedByTheFreeCapacity() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(panel.getMaxEnergyStored() - 10);
        panel.setCurrentEnergyGeneration(64);

        panel.generateEnergy();

        assertEquals(panel.getMaxEnergyStored(), panel.getEnergyStored());
    }

    @Test
    public void removingACapacityUpgradeKeepsTheStoredEnergy() {
        SolarPanelTileEntity panel = newPanel(0);
        int baseCapacity = panel.getMaxEnergyStored();

        setSlot(panel, 0, new ItemStack(ModItems.mUpgradeCapacity, 1));
        int upgradedCapacity = panel.getMaxEnergyStored();
        assertTrue(upgradedCapacity > baseCapacity);
        panel.setEnergyStored(upgradedCapacity);

        // The player takes the upgrade back out of the panel.
        setSlot(panel, 0, null);

        assertEquals(baseCapacity, panel.getMaxEnergyStored());
        assertEquals("energy must not be deleted, only drained", upgradedCapacity, panel.getEnergyStored());
        assertTrue(
            panel.getEnergyStorage()
                .isFull());
    }

    @Test
    public void anUnknownTierIndexFallsBackToAValidTier() {
        SolarPanelTileEntity panel = new SolarPanelTileEntity(999);
        TierConfiguration lastTier = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS
            .get(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size() - 1);

        assertSame(lastTier, panel.getTierConfiguration());
        assertEquals(lastTier.getCapacity(), panel.getMaxEnergyStored());
    }

    @Test
    public void aNegativeTierIndexFallsBackToTheFirstTier() {
        SolarPanelTileEntity panel = new SolarPanelTileEntity(-3);

        assertSame(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(0), panel.getTierConfiguration());
    }

    @Test
    public void aSavedTierIndexOutOfRangeIsClampedOnceOnLoad() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBTConstants.TIER_INDEX, 999);

        SolarPanelTileEntity restored = newPanel(0);
        restored.readFromNBT(tag);

        TierConfiguration lastTier = ModConfiguration.DEFAULT_TIER_CONFIGURATIONS
            .get(ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size() - 1);
        assertSame(lastTier, restored.getTierConfiguration());
        assertEquals(lastTier.getCapacity(), restored.getMaxEnergyStored());

        // The index itself is clamped, so subsequent getters do not fall back (and log) again.
        NBTTagCompound reloaded = new NBTTagCompound();
        restored.addDataToNBT(reloaded);
        assertEquals(
            ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.size() - 1,
            reloaded.getInteger(NBTConstants.TIER_INDEX));
    }

    @Test
    public void theClientDoesNotGenerateEnergy() {
        SolarPanelTileEntity panel = newPanel(0);
        World world = mock(World.class);
        world.isRemote = true;
        TestFixture.setWorld(panel, world);
        panel.setCurrentEnergyGeneration(100);

        panel.updateEntity();

        assertEquals(0, panel.getEnergyStored());
    }

    @Test
    public void theServerGeneratesEnergy() {
        SolarPanelTileEntity panel = newPanel(0);
        World world = mock(World.class);
        world.isRemote = false;
        TestFixture.setWorld(panel, world);
        panel.setCurrentEnergyGeneration(100);

        panel.updateEntity();

        assertEquals(100, panel.getEnergyStored());
    }

    @Test
    public void theUpgradeCacheIsRefreshedWithoutAnExplicitMarkDirty() {
        SolarPanelTileEntity panel = newPanel(0);

        panel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeEfficiency, 3));

        assertEquals(3, panel.getUpgradeCount(ModItems.mUpgradeEfficiency));
    }

    @Test
    public void theFurnaceEnergyBufferIsPersistedAndClamped() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setFurnaceEnergyBuffer(123);
        NBTTagCompound tag = new NBTTagCompound();
        panel.addDataToNBT(tag);

        SolarPanelTileEntity restored = newPanel(0);
        restored.loadDataFromNBT(tag);

        assertEquals(123, restored.getFurnaceEnergyBuffer());

        restored.setFurnaceEnergyBuffer(-5);
        assertEquals(0, restored.getFurnaceEnergyBuffer());
    }

    @Test
    public void theUpgradeCacheIsOnlyRebuiltWhenTheInventoryChanges() {
        SolarPanelTileEntity panel = newPanel(0);

        setSlot(panel, 0, new ItemStack(ModItems.mUpgradeEfficiency, 1));
        int rebuilds = panel.getUpgradeCacheRebuilds();

        panel.markDirty();
        panel.markDirty();

        assertEquals("an unrelated markDirty must not rebuild the cache", rebuilds, panel.getUpgradeCacheRebuilds());

        setSlot(panel, 0, new ItemStack(ModItems.mUpgradeEfficiency, 2));
        assertTrue(panel.getUpgradeCacheRebuilds() > rebuilds);
        assertEquals(2, panel.getUpgradeCount(ModItems.mUpgradeEfficiency));
    }

    @Test
    public void anInPlaceStackSizeChangeStillRefreshesTheCache() {
        SolarPanelTileEntity panel = newPanel(0);
        ItemStack upgrade = new ItemStack(ModItems.mUpgradeCapacity, 1);
        panel.setInventorySlotContents(0, upgrade);
        int oneUpgrade = panel.getMaxEnergyStored();

        // mergeItemStack grows the existing stack in place instead of replacing it.
        upgrade.stackSize = 2;
        panel.markDirty();

        assertTrue(panel.getMaxEnergyStored() > oneUpgrade);
    }

    @Test
    public void aConfigurationVersionChangeRefreshesTheCacheEvenWithUnchangedSlots() {
        SolarPanelTileEntity panel = newPanel(0);
        setSlot(panel, 0, new ItemStack(ModItems.mUpgradeCapacity, 1));
        int rebuilds = panel.getUpgradeCacheRebuilds();

        TestFixture
            .setStatic(ModConfiguration.class, "sConfigurationVersion", ModConfiguration.getConfigurationVersion() + 1);
        panel.markDirty();

        assertEquals(rebuilds + 1, panel.getUpgradeCacheRebuilds());
    }

    @Test
    public void theDescriptionPacketCarriesOnlyTheDisplayState() {
        SolarPanelTileEntity panel = newPanel(2);
        panel.setEnergyStored(1234);
        setSlot(panel, 0, new ItemStack(ModItems.mUpgradeCapacity, 2));
        int capacity = panel.getMaxEnergyStored();

        NBTTagCompound tag = panel.getSyncTag();

        assertEquals(1234, tag.getInteger(NBTConstants.ENERGY));
        assertEquals(2, tag.getInteger(NBTConstants.TIER_INDEX));
        assertEquals(capacity, tag.getInteger(NBTConstants.SYNC_CAPACITY));
        assertFalse("the upgrade slots must not be on the wire", tag.hasKey(NBTConstants.ITEMS));
    }

    @Test
    public void applyingTheDescriptionPacketLeavesTheClientInventoryAlone() {
        SolarPanelTileEntity server = newPanel(2);
        server.setEnergyStored(500);
        server.setCurrentEnergyGeneration(7);
        server.setSunIntensity(0.5F);
        setSlot(server, 0, new ItemStack(ModItems.mUpgradeCapacity, 2));
        int capacity = server.getMaxEnergyStored();

        SolarPanelTileEntity client = newPanel(0);
        client.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeEfficiency, 3));

        client.applySyncTag(server.getSyncTag());

        assertEquals(500, client.getEnergyStored());
        assertEquals(capacity, client.getMaxEnergyStored());
        assertSame(ModConfiguration.getTierConfiguration(2), client.getTierConfiguration());
        assertEquals(7, client.getCurrentEnergyGeneration());
        assertEquals(0.5F, client.getSunIntensity(), 0.0F);
        assertEquals(3, client.getUpgradeCount(ModItems.mUpgradeEfficiency));
    }

    @Test
    public void extractEnergyIsLimitedByTheRequestAndByTheTransferRate() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(1000);

        assertEquals(1, panel.extractEnergy(ForgeDirection.DOWN, 1, false));
        assertEquals(999, panel.getEnergyStored());

        assertEquals(TIER_TRANSFER, panel.extractEnergy(ForgeDirection.DOWN, 1000, false));
        assertEquals(999 - TIER_TRANSFER, panel.getEnergyStored());
    }

    @Test
    public void theTopFaceCannotExtractEnergy() {
        SolarPanelTileEntity panel = newPanel(0);
        panel.setEnergyStored(1000);

        assertEquals(0, panel.extractEnergy(ForgeDirection.UP, 100, false));
        assertEquals(0, panel.extractEnergy(ForgeDirection.UP, 100, true));
        assertEquals(1000, panel.getEnergyStored());
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
