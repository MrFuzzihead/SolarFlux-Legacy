package com.mrfuzzihead.solarflux.config;

public final class TierConfiguration {

    private final int mMaximumEnergyGeneration;
    private final int mMaximumEnergyTransfer;
    private final int mCapacity;
    private final boolean mActive;

    /** The historical rule for the compact default table: a panel moves eight times what it generates. */
    private static final int TRANSFER_PER_GENERATION = 8;

    TierConfiguration(int pMaximumEnergyGeneration, int pMaximumEnergyTransfer, int pCapacity) {
        this(pMaximumEnergyGeneration, pMaximumEnergyTransfer, pCapacity, true);
    }

    TierConfiguration(int pMaximumEnergyGeneration, int pCapacity) {
        this(pMaximumEnergyGeneration, pMaximumEnergyGeneration * TRANSFER_PER_GENERATION, pCapacity, true);
    }

    TierConfiguration(int pMaximumEnergyGeneration, int pMaximumEnergyTransfer, int pCapacity, boolean pActive) {
        mMaximumEnergyGeneration = pMaximumEnergyGeneration;
        mMaximumEnergyTransfer = pMaximumEnergyTransfer;
        mCapacity = pCapacity;
        mActive = pActive;
    }

    /**
     * Whether this tier can be crafted. Inactive tiers keep their block registered (so an existing world never loses
     * it and the tier indexes stay aligned) but get no recipe and are hidden from the creative tab.
     */
    public boolean isActive() {
        return mActive;
    }

    /**
     * The maximum amount of RF generated per tick.
     */
    public int getMaximumEnergyGeneration() {
        return mMaximumEnergyGeneration;
    }

    /**
     * The maximum amount of RF transferred to adjacent machines per tick.
     */
    public int getMaximumEnergyTransfer() {
        return mMaximumEnergyTransfer;
    }

    /**
     * The maximum amount of RF that can be stored internally.
     */
    public int getCapacity() {
        return mCapacity;
    }
}
