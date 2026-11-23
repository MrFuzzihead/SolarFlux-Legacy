package com.mrfuzzihead.solarflux.blocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrfuzzihead.solarflux.blocks.modules.EnergySharingModule;
import com.mrfuzzihead.solarflux.blocks.modules.ITileEntityModule;
import com.mrfuzzihead.solarflux.blocks.modules.SimpleEnergyDispenserModule;
import com.mrfuzzihead.solarflux.blocks.modules.TraversalEnergyDispenserModule;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.config.TierConfiguration;
import com.mrfuzzihead.solarflux.core.inventory.BaseInventory;
import com.mrfuzzihead.solarflux.core.tileentity.BaseModTileEntitySynced;
import com.mrfuzzihead.solarflux.core.utility.Utils;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.items.UpgradeItem;
import com.mrfuzzihead.solarflux.reference.NBTConstants;

import cofh.api.energy.IEnergyProvider;

public class SolarPanelTileEntity extends BaseModTileEntitySynced implements IInventory, IEnergyProvider {

    public static final int INVENTORY_SIZE = 5;
    /**
     * Random number to avoid having all panels performing costly operations during the same tick.
     */
    private final short mTickShift;
    private final PanelEnergy mEnergy;
    /**
     * Index of this tier of SolarPanel.
     */
    private int mTierIndex;
    private ITileEntityModule mEnergySharingModule;
    private ITileEntityModule mEnergyDispenserModule = new SimpleEnergyDispenserModule(this);

    /**
     * Energy already extracted from the storage as a prepayment for the furnace upgrade. Kept on the panel rather than
     * in the dispenser module so that swapping the module (which happens when a traversal upgrade is added or removed)
     * cannot delete it, and so that dismantling can hand it back with the rest of the stored energy.
     */
    private int mFurnaceEnergyBuffer;

    private BaseInventory mInventory = new BaseInventory("container.solarflux.panel", INVENTORY_SIZE, this);
    private final PanelUpgrades mUpgrades = new PanelUpgrades(mInventory);
    private int mUpgradeCacheRebuilds;
    private int mAppliedConfigurationVersion = Integer.MIN_VALUE;

    public SolarPanelTileEntity() {
        // An empty constructor is always required as it is used when loading a TileEntity from NBT.
        this(0);
    }

    public SolarPanelTileEntity(int pTierIndex) {
        mTierIndex = pTierIndex;
        mTickShift = (short) Utils.RANDOM.nextInt(5 * 20);
        mEnergy = new PanelEnergy(
            ModConfiguration.getTierConfiguration(mTierIndex)
                .getCapacity(),
            ModConfiguration.getTierConfiguration(mTierIndex)
                .getMaximumEnergyTransfer());

        // Add an energy sharing module if the config says so.
        if (ModConfiguration.doesAutoBalanceEnergy()) {
            mEnergySharingModule = new EnergySharingModule(this);
        }
    }

    public TierConfiguration getTierConfiguration() {
        return ModConfiguration.getTierConfiguration(mTierIndex);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        if (isServer()) {
            // The client gets the generation rate from the container sync; generating there too would fabricate charge
            // that the server never had and make the GUI drift until the next sync.
            generateEnergy();

            if (atTickRate(20)) {
                updateCurrentEnergyGeneration();
            }

            mEnergyDispenserModule.tick();

            if (mEnergySharingModule != null) {
                mEnergySharingModule.tick();
            }
        }
    }

    /**
     * Returns the internal RF storage of the SolarPanel.
     */
    public StatefulEnergyStorage getEnergyStorage() {
        return mEnergy.storage();
    }

    public int getCurrentEnergyGeneration() {
        return mEnergy.currentGeneration();
    }

    public int getFurnaceEnergyBuffer() {
        return mFurnaceEnergyBuffer;
    }

    public void setFurnaceEnergyBuffer(int pFurnaceEnergyBuffer) {
        mFurnaceEnergyBuffer = Math.max(pFurnaceEnergyBuffer, 0);
    }

    /**
     * Updates the amount of RF currently generated per tick.
     * This must only be used on the client to set the value received by the server.
     */
    public void setCurrentEnergyGeneration(int pCurrentEnergyGeneration) {
        mEnergy.setCurrentGeneration(pCurrentEnergyGeneration);
    }

    public int getMaximumEnergyGeneration() {
        return getTierConfiguration().getMaximumEnergyGeneration();
    }

    /**
     * Updates the current amount of RF generated per tick.
     */
    private void updateCurrentEnergyGeneration() {
        computeSunIntensity();

        double energyGeneration = getMaximumEnergyGeneration() * mEnergy.sunIntensity();
        energyGeneration *= UpgradeMath.multiplier(
            getUpgradeCount(ModItems.mUpgradeEfficiency),
            ModConfiguration.getEfficiencyUpgradeIncrease(),
            ModConfiguration.getEfficiencyUpgradeReturnsToScale());

        mEnergy.setCurrentGeneration((int) Math.round(energyGeneration));
    }

    public float getSunIntensity() {
        return mEnergy.sunIntensity();
    }

    /**
     * This must only be used on the client to set the value received by the server.
     */
    public void setSunIntensity(float pSunIntensity) {
        mEnergy.setSunIntensity(pSunIntensity);
    }

    /**
     * Compute the intensity of the sun that can be used by the Solar Panel.
     */
    private void computeSunIntensity() {
        mEnergy.setSunIntensity(
            SunIntensity.calculate(
                worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord),
                worldObj.getCelestialAngleRadians(1.0F),
                worldObj.isRaining(),
                worldObj.isThundering(),
                getUpgradeCount(ModItems.mUpgradeLowLight),
                ModConfiguration.getRainGenerationFactor(),
                ModConfiguration.getThunderGenerationFactor()));
    }

    protected void generateEnergy() {
        if (mEnergy.currentGeneration() > 0) {
            // Generation is limited by the tier's generation rate, not by the transfer rate used to hand energy to
            // neighbours (the two are only equal for the default tiers).
            mEnergy.storage()
                .addEnergy(mEnergy.currentGeneration());
        }
    }

    /**
     * Goes through the inventory and counts the number of each upgrade.
     */
    private void refreshUpgradeCache() {
        boolean countsChanged = mUpgrades.refreshIfNeeded();
        int configurationVersion = ModConfiguration.getConfigurationVersion();
        boolean configurationChanged = configurationVersion != mAppliedConfigurationVersion;
        if (!countsChanged && !configurationChanged) {
            return;
        }
        mAppliedConfigurationVersion = configurationVersion;
        ++mUpgradeCacheRebuilds;

        // Do we have any upgrade for traversal?
        if (getUpgradeCount(ModItems.mUpgradeTraversal) > 0
            && mEnergyDispenserModule instanceof SimpleEnergyDispenserModule) {
            mEnergyDispenserModule = new TraversalEnergyDispenserModule(this);
        } else if (getUpgradeCount(ModItems.mUpgradeTraversal) == 0
            && mEnergyDispenserModule instanceof TraversalEnergyDispenserModule) {
                mEnergyDispenserModule = new SimpleEnergyDispenserModule(this);
            }

        // Apply effect of transfer rate upgrade.
        getEnergyStorage().setMaxTransfer(
            UpgradeMath.apply(
                ModConfiguration.getTierConfiguration(mTierIndex)
                    .getMaximumEnergyTransfer(),
                getUpgradeCount(ModItems.mUpgradeTransferRate),
                ModConfiguration.getTransferRateUpgradeIncrease(),
                ModConfiguration.getTransferRateUpgradeReturnsToScale()));

        // Apply effect of capacity upgrade
        getEnergyStorage().setMaxEnergyStored(
            UpgradeMath.apply(
                ModConfiguration.getTierConfiguration(mTierIndex)
                    .getCapacity(),
                getUpgradeCount(ModItems.mUpgradeCapacity),
                ModConfiguration.getCapacityUpgradeIncrease(),
                ModConfiguration.getCapacityUpgradeReturnsToScale()));
    }

    /** Visible for tests: how many times the cache was actually rebuilt. */
    int getUpgradeCacheRebuilds() {
        return mUpgradeCacheRebuilds;
    }

    public int getTotalUpgradeInstalled() {
        return mUpgrades.getTotal();
    }

    /**
     * Returns the number of upgrade that can still be added.
     */
    public int additionalUpgradeAllowed(ItemStack pItemStack) {
        if (pItemStack != null) {
            Item item = pItemStack.getItem();
            if (item instanceof UpgradeItem) {
                UpgradeItem upgrade = (UpgradeItem) item;
                return upgrade.getMaximumPerSolarPanel() - getUpgradeCount(upgrade);
            }
        }
        return 0;
    }

    public int getUpgradeCount(Item pItem) {
        return mUpgrades.getCount(pItem);
    }

    @Override
    protected void loadDataFromNBT(NBTTagCompound pNBT) {
        super.loadDataFromNBT(pNBT);

        // Migrate a stale index (a tier removed from the config) once, so the getters do not log on every tick.
        mTierIndex = ModConfiguration.clampTierIndex(pNBT.getInteger(NBTConstants.TIER_INDEX));

        // Update Energy Storage with Tier Configuration
        mEnergy.storage()
            .setMaxEnergyStored(
                ModConfiguration.getTierConfiguration(mTierIndex)
                    .getCapacity());
        mEnergy.storage()
            .setMaxTransfer(
                ModConfiguration.getTierConfiguration(mTierIndex)
                    .getMaximumEnergyTransfer());

        // Restore inventory and force an update of the upgrade cache.
        mInventory.readFromNBT(pNBT);
        markDirty();

        mEnergy.storage()
            .readFromNBT(pNBT);
        mFurnaceEnergyBuffer = Math.max(pNBT.getInteger(NBTConstants.FURNACE_ENERGY_BUFFER), 0);
    }

    @Override
    protected void addDataToNBT(NBTTagCompound pNBT) {
        super.addDataToNBT(pNBT);
        pNBT.setInteger(NBTConstants.TIER_INDEX, mTierIndex);
        pNBT.setInteger(NBTConstants.FURNACE_ENERGY_BUFFER, mFurnaceEnergyBuffer);
        mInventory.writeToNBT(pNBT);
        mEnergy.storage()
            .writeToNBT(pNBT);
    }

    @Override
    protected void addSyncDataToNBT(NBTTagCompound pNBT) {
        PanelSync.write(
            pNBT,
            mTierIndex,
            getEnergyStored(),
            getMaxEnergyStored(),
            mEnergy.currentGeneration(),
            mEnergy.sunIntensity());
    }

    @Override
    protected void loadSyncDataFromNBT(NBTTagCompound pNBT) {
        PanelSync.Data data = PanelSync.read(pNBT);
        mTierIndex = ModConfiguration.clampTierIndex(data.tier());
        int capacity = data.capacity();
        if (capacity <= 0) {
            // A packet from a build that did not send the capacity yet: fall back to the tier's base value so the
            // gauge never divides by zero.
            capacity = ModConfiguration.getTierConfiguration(mTierIndex)
                .getCapacity();
        }
        mEnergy.storage()
            .setMaxEnergyStored(capacity);
        mEnergy.storage()
            .setEnergyStored(data.energy());
        mEnergy.setCurrentGeneration(data.generation());
        mEnergy.setSunIntensity(data.sunIntensity());
    }

    /**
     * Returns true if this tick is a good one to perform an operation that is desired to run ar the provided rate.
     */
    public boolean atTickRate(int pDesiredTickRate) {
        return (getWorldObj().getTotalWorldTime() + mTickShift) % pDesiredTickRate == 0;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection pFrom) {
        return pFrom != ForgeDirection.UP;
    }

    @Override
    public int extractEnergy(ForgeDirection pFrom, int pMaxExtract, boolean pSimulate) {
        if (!canConnectEnergy(pFrom)) {
            return 0;
        }
        return getEnergyStorage().extractEnergy(pMaxExtract, pSimulate);
    }

    public int getEnergyStored() {
        return getEnergyStored(ForgeDirection.DOWN);
    }

    public void setEnergyStored(int pEnergy) {
        getEnergyStorage().setEnergyStored(pEnergy);
    }

    public int getScaledEnergyStoredFraction(int pScale) {
        // Little trick to avoid integer overflow.
        long v = pScale;
        v *= getEnergyStored();
        v /= getMaxEnergyStored();
        if (v > Integer.MAX_VALUE) {
            throw new IllegalStateException("Integer overflow");
        }
        // Stored energy can exceed the capacity after a capacity upgrade is removed; the gauge stays full instead of
        // overflowing its frame.
        return (int) Math.max(0, Math.min(v, pScale));
    }

    public int getPercentageEnergyStored() {
        return getScaledEnergyStoredFraction(100);
    }

    @Override
    public int getEnergyStored(ForgeDirection pFrom) {
        return getEnergyStorage().getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return getMaxEnergyStored(ForgeDirection.DOWN);
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection pFrom) {
        return getEnergyStorage().getMaxEnergyStored();
    }

    @Override
    public String toString() {
        return "SolarPanelTileEntity{hash=" + hashCode()
            + ", MaxProduction="
            + getMaximumEnergyGeneration()
            + ", energyStorage="
            + getEnergyStorage()
            + "}";
    }

    public BaseInventory getInventory() {
        return mInventory;
    }

    @Override
    public int getSizeInventory() {
        return mInventory.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(int pSlotIndex) {
        return mInventory.getStackInSlot(pSlotIndex);
    }

    @Override
    public ItemStack decrStackSize(int pSlotIndex, int pDecrementAmount) {
        return mInventory.decrStackSize(pSlotIndex, pDecrementAmount);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int pSlotIndex) {
        return mInventory.getStackInSlotOnClosing(pSlotIndex);
    }

    @Override
    public void setInventorySlotContents(int pSlotIndex, ItemStack pItemStack) {
        mInventory.setInventorySlotContents(pSlotIndex, pItemStack);
    }

    @Override
    public String getInventoryName() {
        return mInventory.getInventoryName();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return mInventory.hasCustomInventoryName();
    }

    @Override
    public int getInventoryStackLimit() {
        return mInventory.getInventoryStackLimit();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer pEntityPlayer) {
        return mInventory.isUseableByPlayer(pEntityPlayer);
    }

    @Override
    public void openInventory() {
        mInventory.openInventory();
    }

    @Override
    public void closeInventory() {
        mInventory.closeInventory();
    }

    @Override
    public boolean isItemValidForSlot(int pSlotIndex, ItemStack pItemStack) {
        // isItemValidForSlot is allowed to be probed with an empty slot, which happens with a null stack.
        if (pItemStack != null && pItemStack.getItem() instanceof UpgradeItem) {
            if (additionalUpgradeAllowed(pItemStack) >= pItemStack.stackSize) {
                return mInventory.isItemValidForSlot(pSlotIndex, pItemStack);
            }
        }
        return false;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        refreshUpgradeCache();
    }
}
