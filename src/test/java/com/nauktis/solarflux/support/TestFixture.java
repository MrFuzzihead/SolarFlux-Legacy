package com.nauktis.solarflux.support;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.nauktis.solarflux.config.ModConfiguration;
import com.nauktis.solarflux.config.TierConfiguration;
import com.nauktis.solarflux.init.ModItems;
import com.nauktis.solarflux.items.UpgradeItem;

/**
 * Fixture shared by the headless unit tests.
 *
 * <p>
 * {@link ModConfiguration#initialize(java.io.File)} cannot run outside a Forge launch: it registers a bus handler
 * through {@code FMLCommonHandler.instance()} and builds a Forge {@code Configuration}, whose constructor dereferences
 * {@code FMLInjectionData.data()} (null until FML injects the Minecraft home). The state it would populate is therefore
 * installed here through reflection, using exactly the defaults {@code loadConfiguration()} uses.
 * </p>
 *
 * <p>
 * The same applies to {@link ModItems}: {@code initialize()} calls {@code GameRegistry.registerItem}, which needs the
 * FML registries. The upgrade items are constructed directly instead; everything a solar panel needs from them is
 * {@code instanceof UpgradeItem} and {@code getMaximumPerSolarPanel()}.
 * </p>
 */
public final class TestFixture {

    private TestFixture() {}

    /** Installs the same values {@code ModConfiguration.loadConfiguration()} would produce on a fresh config file. */
    public static void applyDefaultConfiguration() {
        setStatic(ModConfiguration.class, "mSolarPanelHeight", 0.375F);
        setStatic(ModConfiguration.class, "mAutoBalanceEnergy", true);
        setStatic(ModConfiguration.class, "mUseThermalExpansionRecipes", true);
        setStatic(ModConfiguration.class, "mKeepEnergyWhenDismantled", true);
        setStatic(ModConfiguration.class, "mKeepInventoryWhenDismantled", true);
        setStatic(ModConfiguration.class, "mRainGenerationFactor", 0.4F);
        setStatic(ModConfiguration.class, "mThunderGenerationFactor", 0.4F);
        setStatic(ModConfiguration.class, "mInfoOnSneakClick", false);
        setStatic(ModConfiguration.class, "mConnectedTextures", true);
        setStatic(ModConfiguration.class, "mTierConfigurations", defaultTiers());

        setStatic(ModConfiguration.class, "mEfficiencyUpgradeActive", true);
        setStatic(ModConfiguration.class, "mEfficiencyUpgradeIncrease", 0.05F);
        setStatic(ModConfiguration.class, "mEfficiencyUpgradeReturnsToScale", 0.9F);
        setStatic(ModConfiguration.class, "mEfficiencyUpgradeMax", 8);

        setStatic(ModConfiguration.class, "mLowLightUpgradeActive", true);
        setStatic(ModConfiguration.class, "mLowLightUpgradeMax", 8);

        setStatic(ModConfiguration.class, "mTraversalUpgradeActive", true);
        setStatic(ModConfiguration.class, "mTraversalUpgradeIncrease", 1);
        setStatic(ModConfiguration.class, "mTraversalUpgradeUpdateRate", 25);
        setStatic(ModConfiguration.class, "mTraversalUpgradeMax", 64);

        setStatic(ModConfiguration.class, "mTransferRateUpgradeActive", true);
        setStatic(ModConfiguration.class, "mTransferRateUpgradeIncrease", 0.1F);
        setStatic(ModConfiguration.class, "mTransferRateUpgradeReturnsToScale", 0.9F);
        setStatic(ModConfiguration.class, "mTransferRateUpgradeMax", 8);

        setStatic(ModConfiguration.class, "mCapacityUpgradeActive", true);
        setStatic(ModConfiguration.class, "mCapacityUpgradeIncrease", 0.1F);
        setStatic(ModConfiguration.class, "mCapacityUpgradeReturnsToScale", 1F);
        setStatic(ModConfiguration.class, "mCapacityUpgradeMax", 16);

        setStatic(ModConfiguration.class, "mFurnaceUpgradeActive", true);
        setStatic(ModConfiguration.class, "mFurnaceUpgradeHeatingConsumption", 8);
    }

    /** Turns the automatic energy balancing module off, so a panel does not try to reach its neighbours. */
    public static void disableAutoBalance() {
        setStatic(ModConfiguration.class, "mAutoBalanceEnergy", false);
    }

    public static List<TierConfiguration> defaultTiers() {
        return ModConfiguration.DEFAULT_TIER_CONFIGURATIONS;
    }

    /** Installs the upgrade items the tile entity looks up in {@link ModItems} while refreshing its upgrade cache. */
    public static void installUpgradeItems() {
        setStatic(ModItems.class, "mUpgradeBlank", new UpgradeItem("upgradeBlank", 0, new java.util.ArrayList<>()));
        setStatic(ModItems.class, "mUpgradeEfficiency", newUpgrade("upgradeEfficiency", 8));
        setStatic(ModItems.class, "mUpgradeLowLight", newUpgrade("upgradeLowLight", 8));
        setStatic(ModItems.class, "mUpgradeTraversal", newUpgrade("upgradeTraversal", 64));
        setStatic(ModItems.class, "mUpgradeTransferRate", newUpgrade("upgradeTransferRate", 8));
        setStatic(ModItems.class, "mUpgradeCapacity", newUpgrade("upgradeCapacity", 16));
        setStatic(ModItems.class, "mUpgradeFurnace", newUpgrade("upgradeFurnace", 1));
    }

    /**
     * Tile entities get their world from Forge during placement; {@code worldObj} is protected, so tests have to go
     * through the setter.
     */
    public static void setWorld(net.minecraft.tileentity.TileEntity pTileEntity, net.minecraft.world.World pWorld) {
        pTileEntity.setWorldObj(pWorld);
    }

    public static UpgradeItem newUpgrade(String pName, int pMaximumPerSolarPanel) {
        return new UpgradeItem(pName, pMaximumPerSolarPanel, new java.util.ArrayList<String>());
    }

    /**
     * The item behind {@link #stack(int, int)}. One shared instance, so two stacks built that way really are the same
     * item: {@code Utils.itemStacksEqualIgnoreStackSize} compares by identity.
     *
     * <p>
     * It is deliberately never registered: {@code FMLControlledNamespacedRegistry.addObject} needs a
     * {@code LaunchClassLoader}, so items cannot be registered outside a Forge launch. That also means a stack cannot
     * survive an {@code ItemStack.writeToNBT}/{@code loadItemStackFromNBT} round trip in a unit test, because
     * {@code Item.getIdFromItem} returns -1 and {@code Item.getItemById(-1)} returns null. The in-game test mod covers
     * the real round trip.
     * </p>
     */
    public static final Item TEST_ITEM = new Item();

    /**
     * A usable {@link ItemStack}. {@code new Item()} gets a real Forge {@code RegistryDelegate} from its instance
     * initialiser, so the stack behaves like a normal item even though it was never registered.
     */
    public static ItemStack stack(int pSize, int pMetadata) {
        return new ItemStack(TEST_ITEM, pSize, pMetadata);
    }

    public static ItemStack stack(Item pItem, int pSize, int pMetadata) {
        return new ItemStack(pItem, pSize, pMetadata);
    }

    public static void setStatic(Class<?> pOwner, String pFieldName, Object pValue) {
        try {
            Field field = pOwner.getDeclaredField(pFieldName);
            field.setAccessible(true);
            field.set(null, pValue);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + pOwner.getSimpleName() + "." + pFieldName, e);
        }
    }

    public static Object getStatic(Class<?> pOwner, String pFieldName) {
        try {
            Field field = pOwner.getDeclaredField(pFieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + pOwner.getSimpleName() + "." + pFieldName, e);
        }
    }

    /** Writes an instance field, including final ones: {@code World.provider} has no setter. */
    public static void setInstanceField(Object pTarget, Class<?> pOwner, String pFieldName, Object pValue) {
        try {
            Field field = pOwner.getDeclaredField(pFieldName);
            field.setAccessible(true);
            field.set(pTarget, pValue);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + pOwner.getSimpleName() + "." + pFieldName, e);
        }
    }

    public static Object getField(Object pTarget, String pFieldName) {
        try {
            Field field = pTarget.getClass()
                .getDeclaredField(pFieldName);
            field.setAccessible(true);
            return field.get(pTarget);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Cannot read " + pTarget.getClass()
                    .getSimpleName() + "." + pFieldName,
                e);
        }
    }
}
