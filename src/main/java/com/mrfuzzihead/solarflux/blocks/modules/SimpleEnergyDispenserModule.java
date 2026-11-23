package com.mrfuzzihead.solarflux.blocks.modules;

import java.util.List;

import net.minecraft.block.BlockFurnace;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.Lists;
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
            for (int i = 0; i < mTargets.size(); ++i) {
                sendEnergyTo(mTargets.get((mTargetStartingIndex + i) % mTargets.size()));
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
        TileEntity tile = getTileEntity().getWorldObj()
            .getTileEntity(pBlockPosition.x(), pBlockPosition.y(), pBlockPosition.z());
        if (tile instanceof IEnergyReceiver) {
            sendEnergyToReceiver(
                (IEnergyReceiver) tile,
                pBlockPosition.getDirection()
                    .getOpposite());
        } else if (getTileEntity().getUpgradeCount(ModItems.mUpgradeFurnace) > 0 && tile instanceof TileEntityFurnace) {
            sendEnergyToFurnace((TileEntityFurnace) tile);
        }
    }

    protected void sendEnergyToReceiver(IEnergyReceiver pEnergyReceiver, ForgeDirection pFrom) {
        getTileEntity().getEnergyStorage()
            .sendMaxTo(pEnergyReceiver, pFrom);
    }

    protected void sendEnergyToFurnace(TileEntityFurnace pFurnace) {
        final int FURNACE_COOKING_TICKS = 200;
        final int FURNACE_COOKING_ENERGY = FURNACE_COOKING_TICKS
            * ModConfiguration.getFurnaceUpgradeHeatingConsumption();
        SolarPanelTileEntity tileEntity = getTileEntity();

        if (tileEntity.getFurnaceEnergyBuffer() < FURNACE_COOKING_ENERGY) {
            tileEntity.setFurnaceEnergyBuffer(
                tileEntity.getFurnaceEnergyBuffer() + tileEntity.getEnergyStorage()
                    .extractEnergy(FURNACE_COOKING_ENERGY - tileEntity.getFurnaceEnergyBuffer(), false));
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
