package com.mrfuzzihead.solarflux.blocks.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.core.utility.BlockPosition;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.support.TestEnergyReceiverTileEntity;
import com.mrfuzzihead.solarflux.support.TestFixture;

/**
 * Headless tests for the breadth-first target search.
 *
 * <p>
 * The module is driven through {@code searchTargets()} directly instead of {@code tick()}, so the random
 * {@code mTickShift} of the panel cannot make the tests flaky. One call completes a whole sweep (up to the node
 * budget); the target list is published at the start of the call that finds the queue empty.
 * </p>
 */
public class TraversalEnergyDispenserModuleTest {

    private static final int X = 4;
    private static final int Y = 64;
    private static final int Z = -3;

    private World mWorld;
    private SolarPanelTileEntity mPanel;
    private TraversalEnergyDispenserModule mModule;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();

        mWorld = mock(World.class);
        mPanel = new SolarPanelTileEntity(0);
        TestFixture.setWorld(mPanel, mWorld);
        mPanel.xCoord = X;
        mPanel.yCoord = Y;
        mPanel.zCoord = Z;
        mModule = new TraversalEnergyDispenserModule(mPanel);
    }

    private void putReceiver(int pX, int pY, int pZ) {
        when(mWorld.getTileEntity(pX, pY, pZ)).thenReturn(new TestEnergyReceiverTileEntity());
    }

    private void installTraversalUpgrades(int pCount) {
        mPanel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeTraversal, pCount));
        mPanel.markDirty();
    }

    private void runSearch(int pRounds) {
        for (int round = 0; round < pRounds; ++round) {
            mModule.searchTargets();
        }
    }

    private static BlockPosition position(int pX, int pY, int pZ) {
        return new BlockPosition(pX, pY, pZ);
    }

    /**
     * The panel at the west corner of a 2x2 square of receivers: north (direct), east (direct) and the north-east
     * diagonal, which is reachable from both direct neighbours.
     */
    private void placeDiamond() {
        putReceiver(X, Y, Z - 1);
        putReceiver(X + 1, Y, Z);
        putReceiver(X + 1, Y, Z - 1);
    }

    /**
     * Regression: the north-east diagonal was validated (a world lookup) twice because {@code discoverNeighbors} only
     * checked the visited set, which is filled when a block is popped.
     */
    @Test
    public void aBlockReachableFromTwoParentsIsValidatedOnce() {
        installTraversalUpgrades(1);
        placeDiamond();

        runSearch(1);

        verify(mWorld, times(1)).getTileEntity(X + 1, Y, Z - 1);
    }

    @Test
    public void oneRefreshCompletesTheWholeSweep() {
        installTraversalUpgrades(1);
        putReceiver(X + 1, Y, Z);
        putReceiver(X + 2, Y, Z);

        runSearch(1);
        assertTrue(
            "the first call publishes the previous (empty) sweep",
            mModule.getTargets()
                .isEmpty());

        // One more call publishes the sweep that was completed entirely in the previous call.
        runSearch(1);
        assertEquals(
            2,
            mModule.getTargets()
                .size());
    }

    @Test
    public void aBlockReachableFromTwoParentsIsPublishedOnce() {
        installTraversalUpgrades(1);
        placeDiamond();

        // Two direct neighbours + one extra target, then one round to publish the list.
        runSearch(4);

        List<BlockPosition> targets = mModule.getTargets();
        assertEquals(3, targets.size());
        assertTrue(targets.contains(position(X, Y, Z - 1)));
        assertTrue(targets.contains(position(X + 1, Y, Z)));
        assertTrue(targets.contains(position(X + 1, Y, Z - 1)));
    }

    @Test
    public void theSearchStopsAtTheMaximumExtraTargets() {
        installTraversalUpgrades(1);
        putReceiver(X + 1, Y, Z);
        putReceiver(X + 2, Y, Z);
        putReceiver(X + 3, Y, Z);

        runSearch(4);

        List<BlockPosition> targets = mModule.getTargets();
        assertEquals("one direct neighbour plus one extra", 2, targets.size());
        assertTrue(targets.contains(position(X + 1, Y, Z)));
        assertTrue(targets.contains(position(X + 2, Y, Z)));
        assertFalse(targets.contains(position(X + 3, Y, Z)));
    }

    @Test
    public void withoutTraversalUpgradesOnlyTheDirectNeighboursAreVisited() {
        putReceiver(X, Y, Z - 1);
        putReceiver(X + 1, Y, Z);

        assertEquals(0, mModule.getMaximumExtraTargets());
        runSearch(4);

        assertEquals(
            2,
            mModule.getTargets()
                .size());
    }

    @Test
    public void aRepublishedTargetListDropsTheResolvedTileEntities() {
        installTraversalUpgrades(1);
        TestEnergyReceiverTileEntity first = new TestEnergyReceiverTileEntity();
        when(mWorld.getTileEntity(X + 1, Y, Z)).thenReturn(first);
        mPanel.setEnergyStored(1000);

        runSearch(2);
        mModule.sendEnergyToTargets();
        assertEquals(16, first.getReceived());

        TestEnergyReceiverTileEntity second = new TestEnergyReceiverTileEntity();
        when(mWorld.getTileEntity(X + 1, Y, Z)).thenReturn(second);

        // The search publishes the same position again; the cache must be dropped with it.
        runSearch(2);
        mModule.sendEnergyToTargets();

        assertEquals(16, first.getReceived());
        assertEquals(16, second.getReceived());
    }

    @Test
    public void anIsolatedPanelFinishesTheSearchWithoutThrowing() {
        runSearch(3);

        assertTrue(
            mModule.getTargets()
                .isEmpty());
    }
}
