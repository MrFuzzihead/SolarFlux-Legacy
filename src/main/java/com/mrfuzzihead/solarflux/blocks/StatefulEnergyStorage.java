package com.mrfuzzihead.solarflux.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.base.Objects;
import com.mrfuzzihead.solarflux.reference.NBTConstants;

import cofh.api.energy.IEnergyReceiver;
import cofh.api.energy.IEnergyStorage;

public class StatefulEnergyStorage implements IEnergyStorage {

    protected int mEnergy;
    protected int mCapacity;
    protected int mMaxTransferReceive;
    protected int mMaxTransferExtract;

    /** Convenience for tests and harnesses: the capacity is also used as both transfer rates. */
    public StatefulEnergyStorage(int pCapacity) {
        this(pCapacity, pCapacity, pCapacity);
    }

    public StatefulEnergyStorage(int pCapacity, int pMaxTransfer) {
        this(pCapacity, pMaxTransfer, pMaxTransfer);
    }

    /** Convenience for tests and harnesses that need different receive and extract rates. */
    public StatefulEnergyStorage(int pCapacity, int pMaxTransferReceive, int pMaxTransferExtract) {
        mCapacity = pCapacity;
        mMaxTransferReceive = pMaxTransferReceive;
        mMaxTransferExtract = pMaxTransferExtract;
    }

    public void readFromNBT(NBTTagCompound pNbt) {
        setEnergyStored(pNbt.getInteger(NBTConstants.ENERGY));
    }

    public void writeToNBT(NBTTagCompound pNbt) {
        pNbt.setInteger(NBTConstants.ENERGY, getEnergyStored());
    }

    /**
     * Returns the maximum amount of energy the storage can receive.
     */
    public int getMaxReceive() {
        // Energy can exceed the capacity after a capacity upgrade is removed; the panel drains the excess instead of
        // deleting it, so the headroom has to be clamped instead of turning negative.
        return Math.max(0, Math.min(mCapacity - mEnergy, mMaxTransferReceive));
    }

    /**
     * Returns the maximum amount of energy that can be extracted from the storage.
     */
    public int getMaxExtract() {
        return Math.min(mEnergy, mMaxTransferExtract);
    }

    @Override
    public int receiveEnergy(int pMaxReceive, boolean pSimulate) {
        int energyReceived = Math.min(getMaxReceive(), Math.max(pMaxReceive, 0));
        return pSimulate ? energyReceived : addEnergy(energyReceived);
    }

    @Override
    public int extractEnergy(int pMaxExtract, boolean pSimulate) {
        int energyExtracted = Math.min(getMaxExtract(), Math.max(pMaxExtract, 0));
        return pSimulate ? energyExtracted : removeEnergy(energyExtracted);
    }

    /**
     * Adds up to {@code pAmount}, clamped to the remaining capacity. Unlike {@link #receiveEnergy(int, boolean)} this
     * ignores the transfer rate: it is the path used by energy generation, which is limited by the tier's generation
     * rate, not by the rate at which energy may be handed to neighbours.
     */
    public int addEnergy(int pAmount) {
        int accepted = Math.min(Math.max(pAmount, 0), Math.max(mCapacity - mEnergy, 0));
        mEnergy += accepted;
        return accepted;
    }

    /**
     * Removes up to {@code pAmount}, clamped to the stored energy. Unlike {@link #extractEnergy(int, boolean)} this
     * ignores the transfer rate, so callers are responsible for applying it.
     */
    private int removeEnergy(int pAmount) {
        int removed = Math.min(Math.max(pAmount, 0), mEnergy);
        mEnergy -= removed;
        return removed;
    }

    /**
     * Sends at most {@code pMax} energy to the receiver, further capped by the storage's transfer rate.
     *
     * @return the amount the receiver accepted, which is exactly the amount extracted
     */
    public int sendTo(IEnergyReceiver pEnergyReceiver, ForgeDirection pFrom, int pMax) {
        int offered = Math.min(getMaxExtract(), Math.max(pMax, 0));
        return extractEnergy(pEnergyReceiver.receiveEnergy(pFrom, offered, false), false);
    }

    /**
     * Sends the maximum amount of energy possible to the {@link IEnergyReceiver}
     */
    public int sendMaxTo(IEnergyReceiver pEnergyReceiver, ForgeDirection pFrom) {
        return sendTo(pEnergyReceiver, pFrom, Integer.MAX_VALUE);
    }

    /**
     * Tries to balance the amount of energy between the two {@link StatefulEnergyStorage}.
     *
     * <p>
     * The amount to move is computed once, against both transfer rates, before anything changes. The receiver is
     * then credited and the sender is debited by exactly what the receiver accepted, so the pair can never create or
     * destroy energy. Computing the amount first matters: the two storages usually have different transfer rates (a
     * tier I panel next to a tier VI one), and clamping each side independently after the move would let the receiver
     * be credited more than the sender can give.
     * </p>
     */
    public int autoBalanceEnergy(StatefulEnergyStorage pOtherEnergyStorage) {
        return autoBalanceEnergy(pOtherEnergyStorage, 1);
    }

    /**
     * Same as {@link #autoBalanceEnergy(StatefulEnergyStorage)}, but allows each storage to move up to
     * {@code pTransferSpeed} times its configured transfer rate in a single call.
     *
     * <p>
     * Uses {@code long} arithmetic so a speed multiplier can never overflow the transfer rates.
     * </p>
     */
    public int autoBalanceEnergy(StatefulEnergyStorage pOtherEnergyStorage, int pTransferSpeed) {
        int transferSpeed = Math.max(pTransferSpeed, 1);
        int delta = getEnergyStored() - pOtherEnergyStorage.getEnergyStored();
        if (delta < 0) {
            return pOtherEnergyStorage.autoBalanceEnergy(this, transferSpeed);
        }
        if (delta == 0 || pOtherEnergyStorage.isFull()) {
            return 0;
        }
        long transferLimit = Math.min(
            (long) getMaxTransferExtract() * transferSpeed,
            (long) pOtherEnergyStorage.getMaxTransferReceive() * transferSpeed);
        int toMove = (int) Math.min(delta / 2, Math.min(transferLimit, Integer.MAX_VALUE));
        if (toMove <= 0) {
            return 0;
        }
        return removeEnergy(pOtherEnergyStorage.addEnergy(toMove));
    }

    @Override
    public int getEnergyStored() {
        return mEnergy;
    }

    public void setEnergyStored(int pEnergy) {
        mEnergy = Math.max(pEnergy, 0);
    }

    @Override
    public int getMaxEnergyStored() {
        return mCapacity;
    }

    /**
     * Sets the capacity. Stored energy is deliberately preserved when the capacity shrinks: removing a capacity upgrade
     * must not delete the energy that no longer fits, it becomes headroom-free and drains through the normal transfer
     * path instead.
     */
    public void setMaxEnergyStored(int pCapacity) {
        mCapacity = pCapacity;
    }

    public boolean isFull() {
        return getEnergyStored() >= getMaxEnergyStored();
    }

    public int getMaxTransferReceive() {
        return mMaxTransferReceive;
    }

    public void setMaxTransferReceive(int pMaxTransferReceive) {
        mMaxTransferReceive = pMaxTransferReceive;
    }

    public int getMaxTransferExtract() {
        return mMaxTransferExtract;
    }

    public void setMaxTransferExtract(int pMaxTransferExtract) {
        mMaxTransferExtract = pMaxTransferExtract;
    }

    public void setMaxTransfer(int pMaxTransfer) {
        setMaxTransferReceive(pMaxTransfer);
        setMaxTransferExtract(pMaxTransfer);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
            .add("energy", getEnergyStored())
            .add("capacity", getMaxEnergyStored())
            .add("maxTransferReceive", getMaxTransferReceive())
            .add("maxTransferExtract", getMaxTransferExtract())
            .toString();
    }
}
