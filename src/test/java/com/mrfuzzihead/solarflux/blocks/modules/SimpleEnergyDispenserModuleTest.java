package com.mrfuzzihead.solarflux.blocks.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.core.utility.BlockPosition;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.support.TestEnergyReceiverTileEntity;
import com.mrfuzzihead.solarflux.support.TestFixture;

public class SimpleEnergyDispenserModuleTest {

    private static final int X = 4;
    private static final int Y = 64;
    private static final int Z = -3;

    private World world;
    private SolarPanelTileEntity panel;
    private SimpleEnergyDispenserModule module;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();

        world = mock(World.class);
        panel = new SolarPanelTileEntity(0);
        TestFixture.setWorld(panel, world);
        panel.xCoord = X;
        panel.yCoord = Y;
        panel.zCoord = Z;
        module = new SimpleEnergyDispenserModule(panel);
    }

    private static void setSlot(SolarPanelTileEntity pPanel, net.minecraft.item.ItemStack pStack) {
        pPanel.setInventorySlotContents(0, pStack);
        pPanel.markDirty();
    }

    private BlockPosition neighbour(ForgeDirection pDirection) {
        return new BlockPosition(X, Y, Z).move(pDirection);
    }

    private void putTileEntity(ForgeDirection pDirection, net.minecraft.tileentity.TileEntity pTileEntity) {
        BlockPosition position = neighbour(pDirection);
        when(world.getTileEntity(position.x(), position.y(), position.z())).thenReturn(pTileEntity);
    }

    @Test
    public void theTargetListIsRefreshedEveryTwoSeconds() {
        assertEquals(40, module.getTargetRefreshRate());
    }

    @Test
    public void theTargetRefreshRateComesFromTheConfiguration() {
        TestFixture.setStatic(ModConfiguration.class, "mDispenserUpdateRate", 7);

        assertEquals(7, module.getTargetRefreshRate());
    }

    @Test
    public void anIsolatedPanelHasNoTargets() {
        module.searchTargets();

        assertTrue(
            module.getTargets()
                .isEmpty());
    }

    @Test
    public void onlyEnergyReceiversBecomeTargets() {
        TestEnergyReceiverTileEntity receiver = new TestEnergyReceiverTileEntity();
        putTileEntity(ForgeDirection.NORTH, receiver);
        putTileEntity(ForgeDirection.SOUTH, new net.minecraft.tileentity.TileEntity());

        module.searchTargets();

        assertEquals(
            1,
            module.getTargets()
                .size());
        assertEquals(
            neighbour(ForgeDirection.NORTH).x(),
            module.getTargets()
                .get(0)
                .x());
    }

    @Test
    public void everyFaceIsScanned() {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            putTileEntity(direction, new TestEnergyReceiverTileEntity());
        }

        module.searchTargets();

        assertEquals(
            ForgeDirection.VALID_DIRECTIONS.length,
            module.getTargets()
                .size());
    }

    @Test
    public void aFurnaceIsOnlyATargetWithTheFurnaceUpgrade() {
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        putTileEntity(ForgeDirection.WEST, furnace);
        assertFalse(module.isValidTarget(neighbour(ForgeDirection.WEST)));

        setSlot(panel, TestFixture.stack(ModItems.mUpgradeFurnace, 1, 0));

        assertTrue(module.isValidTarget(neighbour(ForgeDirection.WEST)));
    }

    @Test
    public void sendingEnergyDrainsThePanelIntoTheReceiver() {
        TestEnergyReceiverTileEntity receiver = new TestEnergyReceiverTileEntity(10);
        putTileEntity(ForgeDirection.NORTH, receiver);
        module.searchTargets();
        panel.setEnergyStored(1000);

        module.sendEnergyToTargets();

        assertEquals(990, panel.getEnergyStored());
        assertEquals(10, receiver.getReceived());
        assertEquals(1, receiver.getReceiveCalls());
    }

    @Test
    public void anEmptyPanelDoesNotContactItsTargets() {
        TestEnergyReceiverTileEntity receiver = new TestEnergyReceiverTileEntity();
        putTileEntity(ForgeDirection.NORTH, receiver);
        module.searchTargets();

        module.sendEnergyToTargets();

        assertEquals(0, receiver.getReceiveCalls());
    }

    @Test
    public void theRoundRobinIndexAdvancesOverTheTargets() {
        TestEnergyReceiverTileEntity first = new TestEnergyReceiverTileEntity(1);
        TestEnergyReceiverTileEntity second = new TestEnergyReceiverTileEntity(1);
        putTileEntity(ForgeDirection.NORTH, first);
        putTileEntity(ForgeDirection.SOUTH, second);
        module.searchTargets();
        panel.setEnergyStored(1000);

        module.sendEnergyToTargets();

        // Both are contacted in a single pass; the index only decides which one is asked first.
        assertEquals(1, first.getReceiveCalls());
        assertEquals(1, second.getReceiveCalls());
        assertEquals(998, panel.getEnergyStored());
    }

    @Test
    public void theFurnaceBufferSurvivesTheModuleSwapCausedByATraversalUpgrade() {
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        when(furnace.getStackInSlot(0)).thenReturn(TestFixture.stack(1, 0));
        panel.setEnergyStored(100);

        module.sendEnergyToFurnace(furnace);

        assertEquals(16, panel.getFurnaceEnergyBuffer());
        assertEquals(84, panel.getEnergyStored());

        // Installing a traversal upgrade replaces the dispenser module with the traversal variant.
        setSlot(panel, TestFixture.stack(ModItems.mUpgradeTraversal, 1, 0));
        assertTrue(TestFixture.getField(panel, "mEnergyDispenserModule") instanceof TraversalEnergyDispenserModule);
        assertEquals(16, panel.getFurnaceEnergyBuffer());

        // Removing it swaps the module back.
        setSlot(panel, null);
        assertFalse(TestFixture.getField(panel, "mEnergyDispenserModule") instanceof TraversalEnergyDispenserModule);
        assertEquals(16, panel.getFurnaceEnergyBuffer());
    }

    @Test
    public void theFurnaceBufferFillsUpBeforeAnyBurnTimeIsGranted() {
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        putTileEntity(ForgeDirection.DOWN, furnace);
        setSlot(panel, TestFixture.stack(ModItems.mUpgradeFurnace, 1, 0));
        panel.setEnergyStored(100);

        module.sendEnergyToFurnace(furnace);

        assertEquals(0, furnace.furnaceBurnTime);
        assertEquals(100 - 16, panel.getEnergyStored());
    }

    @Test
    public void oneCookOfEnergyKeepsTheFurnaceBurning() {
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        furnace.furnaceBurnTime = 10;
        when(furnace.getStackInSlot(0)).thenReturn(TestFixture.stack(1, 0));
        panel.getEnergyStorage()
            .setMaxTransfer(2000);
        panel.setEnergyStored(2000);

        module.sendEnergyToFurnace(furnace);

        // 200 cooking ticks at 8 RF/tick.
        assertEquals(210, furnace.furnaceBurnTime);
        assertEquals(400, panel.getEnergyStored());
    }

    @Test
    public void aFurnaceWithoutSmeltablesIsNotFed() {
        TileEntityFurnace furnace = mock(TileEntityFurnace.class);
        when(furnace.getStackInSlot(0)).thenReturn(null);
        panel.getEnergyStorage()
            .setMaxTransfer(2000);
        panel.setEnergyStored(2000);

        module.sendEnergyToFurnace(furnace);

        assertEquals(0, furnace.furnaceBurnTime);
        // The buffer is filled anyway; only starting a burn needs something to smelt.
        assertEquals(2000 - 1600, panel.getEnergyStored());
    }

    @Test
    public void tickRefreshesTheTargetsOnTheConfiguredRate() {
        putTileEntity(ForgeDirection.UP, new TestEnergyReceiverTileEntity());
        int aligned = 0;
        for (int time = 0; time < 40; ++time) {
            when(world.getTotalWorldTime()).thenReturn((long) time);
            if (panel.atTickRate(module.getTargetRefreshRate())) {
                aligned = time;
                break;
            }
        }
        when(world.getTotalWorldTime()).thenReturn((long) aligned);

        module.tick();

        assertEquals(
            1,
            module.getTargets()
                .size());
    }

    @Test
    public void theMaximumExtraTargetsComeFromTheTraversalUpgrade() {
        TraversalEnergyDispenserModule traversal = new TraversalEnergyDispenserModule(panel);
        assertEquals(0, traversal.getMaximumExtraTargets());

        setSlot(panel, TestFixture.stack(ModItems.mUpgradeTraversal, 3, 0));

        assertEquals(
            3 * ModConfiguration.getTraversalUpgradeIncrease(),
            new TraversalEnergyDispenserModule(panel).getMaximumExtraTargets());
    }
}
