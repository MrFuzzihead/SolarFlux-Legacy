package com.mrfuzzihead.solarflux.gametest;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrfuzzihead.solarflux.blocks.SolarPanelBlock;
import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.init.ModBlocks;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.reference.NBTConstants;
import com.mrfuzzihead.solarflux.reference.Reference;

import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The assertions the in-game test mod runs against a live dedicated server.
 *
 * <p>
 * Everything here needs state a headless unit test cannot create: the real Forge block and item registries, the real
 * recipe list, and a world that ticks. The unit tests in {@code src/test/java} cover the arithmetic; this covers the
 * wiring.
 * </p>
 *
 * <p>
 * Every check goes through {@link #check(boolean, String)} so a failure is counted and reported instead of thrown: one
 * broken assumption should not hide the rest.
 * </p>
 */
final class SolarFluxGameTests {

    /** Above the world, so the panel always sees the sky. */
    private static final int TEST_HEIGHT = 200;

    private static int sChecks;
    private static int sFailures;

    private SolarFluxGameTests() {}

    static int checkCount() {
        return sChecks;
    }

    static int failureCount() {
        return sFailures;
    }

    private static void check(boolean pCondition, String pDescription) {
        ++sChecks;
        if (!pCondition) {
            ++sFailures;
            System.out.println("[SOLARFLUX-GAMETEST] FAILED: " + pDescription);
        }
    }

    private static void checkEquals(Object pExpected, Object pActual, String pDescription) {
        check(
            pExpected == null ? pActual == null : pExpected.equals(pActual),
            pDescription + " (expected " + pExpected + ", got " + pActual + ")");
    }

    /**
     * Runs every assertion. Must be called on the server thread with a live overworld.
     */
    static void runAll(World pWorld) {
        checksConfiguration();
        checksRegistration();
        checksRecipes();
        checksPlacedPanel(pWorld);
        checksEnergyTransfer(pWorld);
        checksNbtRoundTrip(pWorld);
    }

    /**
     * Ticks a panel the way the server would, advancing the world's total time so the panel's randomised tick shift
     * always lines up with its 20-tick and 40-tick rates.
     *
     * @return the number of ticks the predicate needed, or -1 if it never became true.
     */
    private static int tickUntil(SolarPanelTileEntity pPanel, World pWorld, int pMaxTicks, Condition pCondition) {
        for (int tick = 0; tick < pMaxTicks; ++tick) {
            // WorldInfo.incrementTotalWorldTime() *sets* the counter (vanilla 1.7.10 does the same), so the new value
            // has to be computed here rather than passed as a delta.
            pWorld.getWorldInfo()
                .incrementTotalWorldTime(pWorld.getTotalWorldTime() + 1L);
            pPanel.updateEntity();
            if (pCondition.isTrue()) {
                return tick + 1;
            }
        }
        return -1;
    }

    private interface Condition {

        boolean isTrue();
    }

    // -------------------------------------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------------------------------------

    private static void checksConfiguration() {
        check(
            ModConfiguration.getTierConfigurations() != null && ModConfiguration.getTierConfigurations()
                .size() >= 6,
            "the config must expose at least the six default tiers, got "
                + (ModConfiguration.getTierConfigurations() == null ? "null"
                    : ModConfiguration.getTierConfigurations()
                        .size()));

        checkEquals(0.375F, ModConfiguration.getSolarPanelHeight(), "the default panel height");
        check(ModConfiguration.doesAutoBalanceEnergy(), "panels balance their energy by default");
        check(ModConfiguration.useConnectedTextures(), "connected textures are on by default");
        check(ModConfiguration.isEfficiencyUpgradeActive(), "the efficiency upgrade is enabled by default");
        check(ModConfiguration.getEfficiencyUpgradeMax() > 0, "the efficiency upgrade has a positive maximum");
        check(
            ModConfiguration.getTraversalUpgradeUpdateRate() > 0,
            "the traversal update rate must be positive or the search never runs");
        check(
            ModConfiguration.getFurnaceUpgradeHeatingConsumption() > 0,
            "the furnace upgrade must consume a positive amount per tick");

        for (int tier = 0; tier < ModConfiguration.getTierConfigurations()
            .size(); ++tier) {
            check(
                ModConfiguration.getTierConfiguration(tier)
                    .getCapacity() > 0,
                "tier " + tier + " must have a positive capacity");
            check(
                ModConfiguration.getTierConfiguration(tier)
                    .getMaximumEnergyGeneration() > 0,
                "tier " + tier + " must generate energy");
            check(
                ModConfiguration.getTierConfiguration(tier)
                    .getMaximumEnergyTransfer() > 0,
                "tier " + tier + " must be able to transfer energy");
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------------------------------------

    private static void checksRegistration() {
        int tiers = ModConfiguration.getTierConfigurations()
            .size();
        checkEquals(
            tiers,
            ModBlocks.getSolarPanels()
                .size(),
            "one block per configured tier");

        for (int tier = 0; tier < Math.min(
            tiers,
            ModBlocks.getSolarPanels()
                .size()); ++tier) {
            checkEquals(
                tier,
                ((SolarPanelBlock) ModBlocks.getSolarPanels()
                    .get(tier)).getTierIndex(),
                "the block list must be ordered by tier");

            // ModBlocks registers tier N under the name "solar(N+1)".
            String registryName = "solar" + (tier + 1);
            Block block = GameRegistry.findBlock(Reference.MOD_ID, registryName);
            check(block != null, "block " + registryName + " must be registered");
            check(block instanceof SolarPanelBlock, "block " + registryName + " must be a SolarPanelBlock");
            if (block instanceof SolarPanelBlock) {
                checkEquals(tier, ((SolarPanelBlock) block).getTierIndex(), "tier index of " + registryName);
                // The block is named after its tier index ("solar0") but registered under "solar1", which is what the
                // lang files are keyed on.
                checkEquals(
                    Reference.MOD_ID.toLowerCase() + ":solar" + tier,
                    block.getUnlocalizedName()
                        .substring("tile.".length()),
                    "unlocalized name of " + registryName);
            }
        }

        checkItem(ModItems.mMirror, "mirror");
        checkItem(ModItems.mSolarCell1, "solarCell1");
        checkItem(ModItems.mSolarCell2, "solarCell2");
        checkItem(ModItems.mSolarCell3, "solarCell3");
        checkItem(ModItems.mSolarCell4, "solarCell4");

        if (ModConfiguration.isEfficiencyUpgradeActive()) {
            checkItem(ModItems.mUpgradeEfficiency, "upgradeEfficiency");
        }
        if (ModConfiguration.isLowLightUpgradeActive()) {
            checkItem(ModItems.mUpgradeLowLight, "upgradeLowLight");
        }
        if (ModConfiguration.isTraversalUpgradeActive()) {
            checkItem(ModItems.mUpgradeTraversal, "upgradeTraversal");
        }
        if (ModConfiguration.isTransferRateUpgradeActive()) {
            checkItem(ModItems.mUpgradeTransferRate, "upgradeTransferRate");
        }
        if (ModConfiguration.isCapacityUpgradeActive()) {
            checkItem(ModItems.mUpgradeCapacity, "upgradeCapacity");
        }
        if (ModConfiguration.isFurnaceUpgradeActive()) {
            checkItem(ModItems.mUpgradeFurnace, "upgradeFurnace");
        }
        checkItem(ModItems.mUpgradeBlank, "upgradeBlank");
    }

    private static void checkItem(Item pItem, String pRegistryName) {
        check(pItem != null, "item " + pRegistryName + " must exist");
        checkEquals(
            pItem,
            GameRegistry.findItem(Reference.MOD_ID, pRegistryName),
            "the registered item " + pRegistryName);
    }

    // -------------------------------------------------------------------------------------------------------
    // Recipes
    // -------------------------------------------------------------------------------------------------------

    private static void checksRecipes() {
        // ModRecipes.initialize() runs during FMLInitializationEvent, so the recipe list is populated by now.
        check(
            !CraftingManager.getInstance()
                .getRecipeList()
                .isEmpty(),
            "the crafting recipe list must not be empty");

        for (int tier = 0; tier < ModBlocks.getSolarPanels()
            .size(); ++tier) {
            Block panel = ModBlocks.getSolarPanels()
                .get(tier);
            check(hasRecipeFor(panel), "tier " + (tier + 1) + " must have a crafting recipe");
        }
        check(hasRecipeForItem(ModItems.mSolarCell1), "the first photovoltaic cell must have a crafting recipe");
        check(hasRecipeForItem(ModItems.mMirror), "the mirror must have a crafting recipe");
    }

    private static boolean hasRecipeFor(Block pBlock) {
        for (Object entry : CraftingManager.getInstance()
            .getRecipeList()) {
            ItemStack output = safeRecipeOutput(entry);
            if (output != null && Block.getBlockFromItem(output.getItem()) == pBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRecipeForItem(Item pItem) {
        for (Object entry : CraftingManager.getInstance()
            .getRecipeList()) {
            ItemStack output = safeRecipeOutput(entry);
            if (output != null && output.getItem() == pItem) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack safeRecipeOutput(Object pRecipe) {
        try {
            return ((IRecipe) pRecipe).getRecipeOutput();
        } catch (RuntimeException e) {
            // Some mods add recipes whose output is decided lazily; they are not interesting here.
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // A panel in a real world
    // -------------------------------------------------------------------------------------------------------

    private static void checksPlacedPanel(World pWorld) {
        int x = 0;
        int z = 0;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        check(block != null, "the first tier block must exist to place it");
        if (block == null) {
            return;
        }

        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        TileEntity tileEntity = pWorld.getTileEntity(x, TEST_HEIGHT, z);
        check(tileEntity instanceof SolarPanelTileEntity, "placing the block must create a SolarPanelTileEntity");
        if (!(tileEntity instanceof SolarPanelTileEntity)) {
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
            return;
        }

        SolarPanelTileEntity panel = (SolarPanelTileEntity) tileEntity;
        checkEquals(
            ModConfiguration.getTierConfiguration(0)
                .getCapacity(),
            panel.getMaxEnergyStored(),
            "a freshly placed tier 1 panel has the tier 1 capacity");
        checkEquals(
            ModConfiguration.getTierConfiguration(0)
                .getMaximumEnergyTransfer(),
            panel.getEnergyStorage()
                .getMaxTransferExtract(),
            "a freshly placed tier 1 panel has the tier 1 transfer rate");
        checkEquals(0, panel.getEnergyStored(), "a freshly placed panel starts empty");
        checkEquals(0, panel.getTotalUpgradeInstalled(), "a freshly placed panel has no upgrades");
        checkEquals(
            SolarPanelTileEntity.INVENTORY_SIZE,
            panel.getSizeInventory(),
            "the panel exposes its upgrade slots as an inventory");
        check(!panel.canConnectEnergy(ForgeDirection.UP), "the top face must not accept an energy connection");
        check(panel.canConnectEnergy(ForgeDirection.DOWN), "the bottom face must accept an energy connection");
        check(pWorld.canBlockSeeTheSky(x, TEST_HEIGHT + 1, z), "the placed panel must be able to see the sky");

        // Noon, no rain: the panel must actually generate. updateEntity is what the tick loop calls.
        long previousTime = pWorld.getWorldTime();
        try {
            pWorld.setWorldTime(6000L);
            panel.setEnergyStored(0);
            int ticks = tickUntil(panel, pWorld, 100, new Condition() {

                @Override
                public boolean isTrue() {
                    return panel.getCurrentEnergyGeneration() > 0 && panel.getEnergyStored() > 0;
                }
            });
            check(
                ticks > 0,
                "a panel under an open noon sky must generate and store energy within 100 ticks (state: generation="
                    + panel.getCurrentEnergyGeneration()
                    + ", stored="
                    + panel.getEnergyStored()
                    + ", sun="
                    + panel.getSunIntensity()
                    + ")");
            check(
                panel.getCurrentEnergyGeneration() <= panel.getMaximumEnergyGeneration(),
                "the reported generation must not exceed the tier maximum");
        } finally {
            pWorld.setWorldTime(previousTime);
        }

        pWorld.setBlockToAir(x, TEST_HEIGHT, z);
        check(pWorld.getTileEntity(x, TEST_HEIGHT, z) == null, "removing the block must remove the tile entity");
    }

    private static void checksEnergyTransfer(World pWorld) {
        int x = 5;
        int z = 5;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        Block receiverBlock = GameTestMod.getTestReceiverBlock();
        if (block == null || receiverBlock == null) {
            check(false, "the energy transfer test needs the panel and receiver blocks");
            return;
        }

        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        pWorld.setBlock(x + 1, TEST_HEIGHT, z, receiverBlock);
        TileEntity panelTile = pWorld.getTileEntity(x, TEST_HEIGHT, z);
        TileEntity receiverTile = pWorld.getTileEntity(x + 1, TEST_HEIGHT, z);
        check(receiverTile instanceof IEnergyReceiver, "the test receiver block must create an energy receiver");

        if (panelTile instanceof SolarPanelTileEntity && receiverTile instanceof GameTestMod.TestEnergyReceiver) {
            final SolarPanelTileEntity panel = (SolarPanelTileEntity) panelTile;
            final GameTestMod.TestEnergyReceiver receiver = (GameTestMod.TestEnergyReceiver) receiverTile;
            panel.setEnergyStored(panel.getMaxEnergyStored());

            // The dispenser refreshes its target list on a slower rate than it pushes energy, so this needs several
            // ticks before the receiver sees anything.
            int ticks = tickUntil(panel, pWorld, 200, new Condition() {

                @Override
                public boolean isTrue() {
                    return receiver.getEnergyStored() > 0;
                }
            });
            check(ticks > 0, "the panel must push energy into the adjacent receiver within 200 ticks");
            check(
                receiver.getEnergyStored() <= panel.getMaxEnergyStored(),
                "the receiver cannot have been given more than the panel held");
            check(
                panel.getEnergyStored() > 0,
                "a single transfer tick must not empty the whole panel (stored=" + panel.getEnergyStored() + ")");
        } else {
            check(
                false,
                "the energy transfer arena could not be built: panel=" + panelTile + " receiver=" + receiverTile);
        }

        pWorld.setBlockToAir(x, TEST_HEIGHT, z);
        pWorld.setBlockToAir(x + 1, TEST_HEIGHT, z);
    }

    private static void checksNbtRoundTrip(World pWorld) {
        int x = -5;
        int z = -5;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar3");
        check(block != null, "the third tier block must exist to place it");
        if (block == null) {
            return;
        }

        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        TileEntity tileEntity = pWorld.getTileEntity(x, TEST_HEIGHT, z);
        if (!(tileEntity instanceof SolarPanelTileEntity)) {
            check(false, "the third tier block must create a SolarPanelTileEntity");
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
            return;
        }

        SolarPanelTileEntity original = (SolarPanelTileEntity) tileEntity;
        original.setEnergyStored(12345);
        if (ModItems.mUpgradeEfficiency != null) {
            original.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeEfficiency, 2));
            original.markDirty();
        }
        NBTTagCompound tag = new NBTTagCompound();
        original.writeToNBT(tag);

        check(tag.hasKey(NBTConstants.TIER_INDEX), "the tier index must be written to NBT");
        checkEquals(12345, tag.getInteger(NBTConstants.ENERGY), "the charge must be written to NBT");
        checkEquals(
            ModConfiguration.getTierConfiguration(2)
                .getMaximumEnergyGeneration(),
            original.getTierConfiguration()
                .getMaximumEnergyGeneration(),
            "the third tier block must use the third tier configuration");

        SolarPanelTileEntity restored = new SolarPanelTileEntity(0);
        restored.readFromNBT(tag);

        checkEquals(
            original.getTierConfiguration()
                .getCapacity(),
            restored.getTierConfiguration()
                .getCapacity(),
            "the tier must survive an NBT round trip");
        checkEquals(12345, restored.getEnergyStored(), "the charge must survive an NBT round trip");
        checkEquals(
            original.getMaxEnergyStored(),
            restored.getMaxEnergyStored(),
            "the capacity must survive an NBT round trip");
        checkEquals(
            original.getEnergyStorage()
                .getMaxTransferExtract(),
            restored.getEnergyStorage()
                .getMaxTransferExtract(),
            "the transfer rate must survive an NBT round trip");
        if (ModItems.mUpgradeEfficiency != null) {
            checkEquals(
                2,
                restored.getUpgradeCount(ModItems.mUpgradeEfficiency),
                "the installed upgrades must survive an NBT round trip");
            checkEquals(
                2,
                restored.getTotalUpgradeInstalled(),
                "the total upgrade count must survive an NBT round trip");
        }

        pWorld.setBlockToAir(x, TEST_HEIGHT, z);
    }
}
