package com.mrfuzzihead.solarflux.blocks.modules;

import java.util.List;
import java.util.Map;

import net.minecraft.block.BlockFurnace;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.core.utility.BlockPosition;
import com.mrfuzzihead.solarflux.init.ModItems;

import cofh.api.energy.IEnergyReceiver;

/**
 * Module used to distribute energy to neighbor blocks.
 */
public class SimpleEnergyDispenserModule extends AbstractSolarPanelModule {

    private final List<BlockPosition> mTargets = Lists.newArrayList();
    /** Resolved targets, so a field of panels does not re-query the world for every target every tick. */
    private final Map<BlockPosition, TileEntity> mTargetTileEntities = Maps.newHashMap();
    private int mTargetStartingIndex;

    public SimpleEnergyDispenserModule(SolarPanelTileEntity pSolarPanelTileEntity) {
        super(pSolarPanelTileEntity);
    }

    @Override
    public void tick() {
        sendEnergyToTargets();
        if (atRate(getTargetRefreshRate())) {
            searchTargets();
        }
    }

    protected int getTargetRefreshRate() {
        return ModConfiguration.getDispenserUpdateRate();
    }

    protected void searchTargets() {
        mTargets.clear();
        clearTargetCache();
        BlockPosition position = new BlockPosition(getTileEntity().x(), getTileEntity().y(), getTileEntity().z());
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            BlockPosition neighbor = position.move(direction);
            if (isValidTarget(neighbor)) {
                mTargets.add(neighbor);
            }
        }
    }

    protected List<BlockPosition> getTargets() {
        return mTargets;
    }

    protected void sendEnergyToTargets() {
        if (mTargets.size() > 0 && getTileEntity().getEnergyStored() > 0) {
            // Share the buffer evenly instead of letting the first target in the rotation drain it. With a full panel
            // every target still gets the full transfer rate (the per-target share is larger than the rate).
            int available = getTileEntity().getEnergyStored();
            int perTarget = Math.max(1, available / mTargets.size());
            for (int i = 0; i < mTargets.size(); ++i) {
                sendEnergyTo(mTargets.get((mTargetStartingIndex + i) % mTargets.size()), perTarget);
            }
            mTargetStartingIndex = (mTargetStartingIndex + 1) % mTargets.size();
        }
    }

    protected boolean isValidTarget(BlockPosition pPosition) {
        TileEntity tile = getTileEntity().getWorldObj()
            .getTileEntity(pPosition.x(), pPosition.y(), pPosition.z());
        return tile instanceof IEnergyReceiver
            || (getTileEntity().getUpgradeCount(ModItems.mUpgradeFurnace) > 0 && tile instanceof TileEntityFurnace);
    }

    protected void sendEnergyTo(BlockPosition pBlockPosition) {
        sendEnergyTo(pBlockPosition, Integer.MAX_VALUE);
    }

    protected void sendEnergyTo(BlockPosition pBlockPosition, int pMax) {
        TileEntity tile = getTargetTileEntity(pBlockPosition);
        if (tile instanceof IEnergyReceiver) {
            sendEnergyToReceiver(
                (IEnergyReceiver) tile,
                pBlockPosition.getDirection()
                    .getOpposite(),
                pMax);
        } else if (getTileEntity().getUpgradeCount(ModItems.mUpgradeFurnace) > 0 && tile instanceof TileEntityFurnace) {
            sendEnergyToFurnace((TileEntityFurnace) tile, pMax);
        }
    }

    protected void sendEnergyToReceiver(IEnergyReceiver pEnergyReceiver, ForgeDirection pFrom) {
        sendEnergyToReceiver(pEnergyReceiver, pFrom, Integer.MAX_VALUE);
    }

    protected void sendEnergyToReceiver(IEnergyReceiver pEnergyReceiver, ForgeDirection pFrom, int pMax) {
        getTileEntity().getEnergyStorage()
            .sendTo(pEnergyReceiver, pFrom, pMax);
    }

    /**
     * Drops the resolved tile entities. Called whenever the target list is rebuilt, so a replaced block is picked up at
     * the next refresh instead of being served forever.
     */
    protected void clearTargetCache() {
        mTargetTileEntities.clear();
    }

    private TileEntity getTargetTileEntity(BlockPosition pBlockPosition) {
        TileEntity tile = mTargetTileEntities.get(pBlockPosition);
        if (tile == null) {
            tile = getTileEntity().getWorldObj()
                .getTileEntity(pBlockPosition.x(), pBlockPosition.y(), pBlockPosition.z());
            if (tile != null) {
                mTargetTileEntities.put(pBlockPosition, tile);
            }
        }
        return tile;
    }

    protected void sendEnergyToFurnace(TileEntityFurnace pFurnace) {
        sendEnergyToFurnace(pFurnace, Integer.MAX_VALUE);
    }

    protected void sendEnergyToFurnace(TileEntityFurnace pFurnace, int pMax) {
        final int FURNACE_COOKING_TICKS = 200;
        final int FURNACE_COOKING_ENERGY = FURNACE_COOKING_TICKS
            * ModConfiguration.getFurnaceUpgradeHeatingConsumption();
        SolarPanelTileEntity tileEntity = getTileEntity();

        if (tileEntity.getFurnaceEnergyBuffer() < FURNACE_COOKING_ENERGY) {
            int needed = Math.min(FURNACE_COOKING_ENERGY - tileEntity.getFurnaceEnergyBuffer(), pMax);
            tileEntity.setFurnaceEnergyBuffer(
                tileEntity.getFurnaceEnergyBuffer() + tileEntity.getEnergyStorage()
                    .extractEnergy(needed, false));
        }

        // Is there anything to smell?
        if (pFurnace.getStackInSlot(0) != null && pFurnace.furnaceBurnTime < FURNACE_COOKING_TICKS) {
            int burnTicksAvailable = tileEntity.getFurnaceEnergyBuffer()
                / ModConfiguration.getFurnaceUpgradeHeatingConsumption();
            if (burnTicksAvailable >= FURNACE_COOKING_TICKS) {
                if (pFurnace.furnaceBurnTime == 0) {
                    // Add 1 as first tick is not counted in the burning process.
                    pFurnace.furnaceBurnTime += 1;
                    BlockFurnace.updateFurnaceBlockState(
                        pFurnace.furnaceBurnTime > 0,
                        pFurnace.getWorldObj(),
                        pFurnace.xCoord,
                        pFurnace.yCoord,
                        pFurnace.zCoord);
                }
                pFurnace.furnaceBurnTime += FURNACE_COOKING_TICKS;
                tileEntity.setFurnaceEnergyBuffer(
                    tileEntity.getFurnaceEnergyBuffer()
                        - FURNACE_COOKING_TICKS * ModConfiguration.getFurnaceUpgradeHeatingConsumption());
            }
        }
    }
}
