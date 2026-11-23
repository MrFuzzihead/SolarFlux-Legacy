package com.nauktis.solarflux.support;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyReceiver;

/**
 * A real {@link TileEntity} that also accepts energy.
 *
 * <p>
 * The energy dispenser module only ever hands energy to a {@code TileEntity} that happens to implement
 * {@link IEnergyReceiver}, so a plain Mockito mock of the interface is not enough: the cast in the module would fail
 * before the interface check is reached.
 * </p>
 */
public class TestEnergyReceiverTileEntity extends TileEntity implements IEnergyReceiver {

    private final int mMaxAcceptedPerTick;
    private int mReceived;
    private int mReceiveCalls;

    public TestEnergyReceiverTileEntity() {
        this(Integer.MAX_VALUE);
    }

    public TestEnergyReceiverTileEntity(int pMaxAcceptedPerTick) {
        mMaxAcceptedPerTick = pMaxAcceptedPerTick;
    }

    public int getReceived() {
        return mReceived;
    }

    public int getReceiveCalls() {
        return mReceiveCalls;
    }

    @Override
    public int receiveEnergy(ForgeDirection pFrom, int pMaxReceive, boolean pSimulate) {
        int accepted = Math.min(mMaxAcceptedPerTick - mReceived, Math.max(pMaxReceive, 0));
        ++mReceiveCalls;
        if (!pSimulate) {
            mReceived += accepted;
        }
        return accepted;
    }

    @Override
    public int getEnergyStored(ForgeDirection pFrom) {
        return mReceived;
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection pFrom) {
        return mMaxAcceptedPerTick;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection pFrom) {
        return true;
    }
}
