package com.mrfuzzihead.solarflux.blocks.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
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
 * {@code mTickShift} of the panel cannot make the tests flaky. Each call advances the search by one block; the target
 * list is published at the start of the call that finds the queue empty.
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

    @SuppressWarnings("unchecked")
    private Collection<BlockPosition> queuedBlocks() {
        return (Collection<BlockPosition>) TestFixture.getField(mModule, "mBlocksToVisit");
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
     * Regression: the north-east diagonal was queued twice because {@code discoverNeighbors} only checked the visited
     * set, which is filled when a block is popped.
     */
    @Test
    public void theSearchNeverQueuesTheSamePositionTwice() {
        installTraversalUpgrades(1);
        placeDiamond();

        // Round one visits the north neighbour, round two the east one; both discover the diagonal.
        runSearch(2);

        Collection<BlockPosition> queue = queuedBlocks();
        assertEquals("only the diagonal is still pending", 1, queue.size());
        assertEquals("the queue holds no duplicates", 1, new HashSet<>(queue).size());
        assertEquals(
            position(X + 1, Y, Z - 1),
            queue.iterator()
                .next());
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
    public void anIsolatedPanelFinishesTheSearchWithoutThrowing() {
        runSearch(3);

        assertTrue(
            mModule.getTargets()
                .isEmpty());
    }
}
