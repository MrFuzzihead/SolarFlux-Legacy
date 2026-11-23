package com.mrfuzzihead.solarflux.blocks;

import net.minecraft.nbt.NBTTagCompound;

import com.mrfuzzihead.solarflux.reference.NBTConstants;

/**
 * The S35 description payload: only what the client displays, never the inventory.
 *
 * <p>
 * The container syncs an open GUI's slots itself, so a field of panels would otherwise ship every upgrade stack to
 * every client on chunk load. The capacity travels with the packet because the client can no longer derive it from
 * the items.
 * </p>
 *
 * <p>
 * The disk NBT stays in the tile entity: it is a composition of the inventory's and storage's own serialisers.
 * </p>
 */
public final class PanelSync {

    private PanelSync() {}

    public static void write(NBTTagCompound pNBT, int pTier, int pEnergy, int pCapacity, int pGeneration, float pSun) {
        pNBT.setInteger(NBTConstants.TIER_INDEX, pTier);
        pNBT.setInteger(NBTConstants.ENERGY, pEnergy);
        pNBT.setInteger(NBTConstants.SYNC_CAPACITY, pCapacity);
        pNBT.setInteger(NBTConstants.SYNC_GENERATION, pGeneration);
        pNBT.setFloat(NBTConstants.SYNC_SUN_INTENSITY, pSun);
    }

    public static Data read(NBTTagCompound pNBT) {
        return new Data(
            pNBT.getInteger(NBTConstants.TIER_INDEX),
            pNBT.getInteger(NBTConstants.ENERGY),
            pNBT.getInteger(NBTConstants.SYNC_CAPACITY),
            pNBT.getInteger(NBTConstants.SYNC_GENERATION),
            pNBT.getFloat(NBTConstants.SYNC_SUN_INTENSITY));
    }

    /** The decoded payload; the tile entity applies it because the tier needs the usual clamping. */
    public static final class Data {

        private final int mTier;
        private final int mEnergy;
        private final int mCapacity;
        private final int mGeneration;
        private final float mSunIntensity;

        Data(int pTier, int pEnergy, int pCapacity, int pGeneration, float pSunIntensity) {
            mTier = pTier;
            mEnergy = pEnergy;
            mCapacity = pCapacity;
            mGeneration = pGeneration;
            mSunIntensity = pSunIntensity;
        }

        public int tier() {
            return mTier;
        }

        public int energy() {
            return mEnergy;
        }

        public int capacity() {
            return mCapacity;
        }

        public int generation() {
            return mGeneration;
        }

        public float sunIntensity() {
            return mSunIntensity;
        }
    }
}
