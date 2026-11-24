package com.mrfuzzihead.solarflux.blocks.modules;

import java.util.Iterator;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.Sets;
import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.core.utility.BlockPosition;
import com.mrfuzzihead.solarflux.init.ModItems;

public class TraversalEnergyDispenserModule extends SimpleEnergyDispenserModule {

    /** A sweep is bounded, so a pathological target count cannot stall a tick. */
    private static final int MAX_NODES_PER_REFRESH = 512;

    private final Set<BlockPosition> mVisitedBlocks = Sets.newHashSet();
    private final Set<BlockPosition> mBlocksToVisit = Sets.newLinkedHashSet();
    private int mDirectNeighborDiscovered;

    public TraversalEnergyDispenserModule(SolarPanelTileEntity pTileEntity) {
        super(pTileEntity);
    }

    protected void searchTargets() {
        if (searchFinished()) {
            // We have our new targets.
            getTargets().clear();
            getTargets().addAll(mVisitedBlocks);
            clearTargetCache();
            // Reset the search.
            mVisitedBlocks.clear();
            discoverNeighbors(new BlockPosition(getTileEntity().x(), getTileEntity().y(), getTileEntity().z()));
            mDirectNeighborDiscovered = mBlocksToVisit.size();
        }

        // Complete the sweep in one refresh instead of one node per UpdateRate (up to 80 s for a full upgrade),
        // bounded so a pathological configuration cannot stall the server tick.
        int processed = 0;
        while (!searchFinished() && processed < MAX_NODES_PER_REFRESH) {
            // If we reached the max number of targets we stop the search.
            if (mVisitedBlocks.size() >= mDirectNeighborDiscovered + getMaximumExtraTargets()) {
                mBlocksToVisit.clear();
                break;
            }
            BlockPosition position = pollNext();
            mVisitedBlocks.add(position);
            discoverNeighbors(position);
            ++processed;
        }
    }

    @Override
    protected int getTargetRefreshRate() {
        return ModConfiguration.getTraversalUpgradeUpdateRate();
    }

    private BlockPosition pollNext() {
        Iterator<BlockPosition> iterator = mBlocksToVisit.iterator();
        BlockPosition next = iterator.next();
        iterator.remove();
        return next;
    }

    private boolean searchFinished() {
        return mBlocksToVisit.isEmpty();
    }

    /**
     * Discover blocks that should be traveled around the location provided.
     */
    private void discoverNeighbors(BlockPosition pPosition) {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            BlockPosition neighbor = pPosition.move(direction);
            // Check the queue as well as the visited set: a block reachable from two parents (a diamond) would
            // otherwise be validated and queued twice.
            if (!mVisitedBlocks.contains(neighbor) && !mBlocksToVisit.contains(neighbor) && isValidTarget(neighbor)) {
                mBlocksToVisit.add(neighbor);
            }
        }
    }

    /**
     * Returns the maximum amount of target that can be found in addition to the 4 neighbors of the block.
     */
    public int getMaximumExtraTargets() {
        return getTileEntity().getUpgradeCount(ModItems.mUpgradeTraversal)
            * ModConfiguration.getTraversalUpgradeIncrease();
    }
}
