package com.mrfuzzihead.solarflux.blocks;

/**
 * The panel's energy state: the RF storage plus the last sun/generation values computed for display.
 *
 * <p>
 * Extracted from the tile entity so the storage and the display numbers have a single owner; the tile entity keeps
 * the world-dependent calculation and the {@code IEnergyProvider} façade.
 * </p>
 */
public final class PanelEnergy {

    private final StatefulEnergyStorage mStorage;
    private float mSunIntensity;
    private int mCurrentGeneration;

    public PanelEnergy(int pCapacity, int pTransfer) {
        mStorage = new StatefulEnergyStorage(pCapacity, pTransfer);
    }

    public StatefulEnergyStorage storage() {
        return mStorage;
    }

    public float sunIntensity() {
        return mSunIntensity;
    }

    public void setSunIntensity(float pSunIntensity) {
        mSunIntensity = pSunIntensity;
    }

    public int currentGeneration() {
        return mCurrentGeneration;
    }

    public void setCurrentGeneration(int pCurrentGeneration) {
        mCurrentGeneration = pCurrentGeneration;
    }
}
