package com.mrfuzzihead.solarflux.gametest;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.mrfuzzihead.solarflux.blocks.SolarPanelBlock;
import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.blocks.UpgradeMath;
import com.mrfuzzihead.solarflux.config.ForgeConfigSource;
import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.config.TierConfiguration;
import com.mrfuzzihead.solarflux.core.utility.Utils;
import com.mrfuzzihead.solarflux.init.ModBlocks;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.init.recipes.VanillaRecipeRegistrar;
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
        checksLocalisation();
        checksRecipes();
        checksRecipeContent();
        checksPlacedPanel(pWorld);
        checksEnergyTransfer(pWorld);
        checksNbtRoundTrip(pWorld);
        checksRuntimeReload(pWorld);
        checksWrench(pWorld);
        checksInactiveTierHandling();
        checksTileEntityId(pWorld);
        checksUpgradesInPlace(pWorld);
        checksTraversalChain(pWorld);
        checksEnergySharing(pWorld);
        checksDismantleRoundTrip(pWorld);
        checksFurnaceUpgrade(pWorld);
        checksSunInSitu(pWorld);
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
            boolean active = ModConfiguration.getTierConfiguration(tier)
                .isActive();
            if (active) {
                check(hasRecipeFor(panel), "tier " + (tier + 1) + " must have a crafting recipe");
            } else {
                check(!hasRecipeFor(panel), "inactive tier " + (tier + 1) + " must not have a crafting recipe");
            }
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

        NBTTagCompound sync = original.getSyncTag();
        check(
            original.getDescriptionPacket() instanceof S35PacketUpdateTileEntity,
            "the synced tile entity must send an S35 description packet");
        check(!sync.hasKey(NBTConstants.ITEMS), "the description packet must not ship the upgrade inventory");
        checkEquals(
            original.getMaxEnergyStored(),
            sync.getInteger(NBTConstants.SYNC_CAPACITY),
            "the description packet must carry the capacity");
        checkEquals(2, sync.getInteger(NBTConstants.TIER_INDEX), "the description packet must carry the tier");
        check(!sync.hasKey(NBTConstants.FURNACE_ENERGY_BUFFER), "the description packet must not carry disk-only data");
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

    // -------------------------------------------------------------------------------------------------------
    // Runtime configuration reload
    // -------------------------------------------------------------------------------------------------------

    /**
     * A config GUI change fires a reload on a live server. Restart-safe values must be re-applied, and the tier list
     * must not be rebuilt: the blocks are already registered, so reinterpreting every saved tier index would corrupt
     * existing panels.
     */
    private static void checksRuntimeReload(World pWorld) {
        Configuration config = ModConfiguration.getConfiguration();
        check(config != null, "a live Forge configuration must exist");
        if (config == null) {
            return;
        }

        float originalHeight = ModConfiguration.getSolarPanelHeight();
        java.util.List<TierConfiguration> tiersBefore = ModConfiguration.getTierConfigurations();
        try {
            config.get(Configuration.CATEGORY_GENERAL, "SolarPanelHeight", originalHeight)
                .set((double) 0.5F);
            ModConfiguration.reload();

            checkEquals(0.5F, ModConfiguration.getSolarPanelHeight(), "a reload applies the restart-safe height");
            check(ModConfiguration.getTierConfigurations() == tiersBefore, "a reload must not rebuild the tier list");

            Block panel = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
            check(panel != null, "the first tier block must exist for the bounds check");
            if (panel != null) {
                checkEquals(
                    0.5D,
                    panel.getCollisionBoundingBoxFromPool(pWorld, 0, 0, 0).maxY,
                    "a reload must re-apply the panel bounds");
            }
        } finally {
            config.get(Configuration.CATEGORY_GENERAL, "SolarPanelHeight", originalHeight)
                .set((double) originalHeight);
            ModConfiguration.reload();
        }

        checkEquals(
            originalHeight,
            ModConfiguration.getSolarPanelHeight(),
            "the reload test must restore the panel height");
    }

    // -------------------------------------------------------------------------------------------------------
    // Wrench support
    // -------------------------------------------------------------------------------------------------------

    /**
     * A CoFH hammer (the Thermal Expansion tool) must be able to dismantle a panel, and a plain item must not.
     */
    private static void checksWrench(World pWorld) {
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        Item hammer = GameTestMod.getTestHammerItem();
        check(block != null, "the first tier block must exist for the wrench check");
        check(hammer != null, "the test hammer item must be registered");
        if (block == null || hammer == null) {
            return;
        }

        int x = 10;
        int z = 10;
        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        check(
            pWorld.getTileEntity(x, TEST_HEIGHT, z) instanceof SolarPanelTileEntity,
            "the panel must be placed for the wrench check");

        FakePlayer player = FakePlayerFactory.getMinecraft((WorldServer) pWorld);
        try {
            player.inventory.mainInventory[0] = new ItemStack(hammer);
            player.inventory.currentItem = 0;
            player.setSneaking(true);

            check(Utils.hasUsableWrench(player, x, TEST_HEIGHT, z), "a CoFH hammer must count as a wrench");
            check(
                block.onBlockActivated(pWorld, x, TEST_HEIGHT, z, player, 1, 0.5F, 0.5F, 0.5F),
                "the wrench click must be handled");
            check(pWorld.isAirBlock(x, TEST_HEIGHT, z), "a CoFH hammer must dismantle the panel");

            pWorld.setBlock(x, TEST_HEIGHT, z, block);
            player.inventory.mainInventory[0] = new ItemStack(Items.apple);
            check(!Utils.hasUsableWrench(player, x, TEST_HEIGHT, z), "a plain item must not count as a wrench");
            block.onBlockActivated(pWorld, x, TEST_HEIGHT, z, player, 1, 0.5F, 0.5F, 0.5F);
            check(!pWorld.isAirBlock(x, TEST_HEIGHT, z), "a plain item must not dismantle the panel");
        } finally {
            player.inventory.mainInventory[0] = null;
            player.setSneaking(false);
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // Tier activation (C1) and the tile entity id (R5)
    // -------------------------------------------------------------------------------------------------------

    /**
     * C1: a gap in the Active flags must not truncate the list. The crafted Configuration has tier 1 inactive and
     * tiers 0, 2..5 active; parsing must keep all six (index stability) and the recipe registrar must skip tier 1.
     */
    private static void checksInactiveTierHandling() {
        java.io.File file = null;
        try {
            file = java.io.File.createTempFile("solarflux-tier-test", ".cfg");
            Configuration config = new Configuration(file);
            for (int tier = 0; tier < 6; ++tier) {
                String category = "solar_panel_tier" + tier;
                config.getBoolean("Active", category, tier != 1, "test");
                config.getInt("MaximumEnergyGeneration", category, 10 + tier, 1, 1_000_000, "test");
                config.getInt("MaximumEnergyTransfer", category, 20 + tier, 1, 1_000_000, "test");
                config.getInt("Capacity", category, 1_000 + tier, 1, 1_000_000, "test");
            }
            config.save();

            Configuration reloaded = new Configuration(file);
            java.util.List<TierConfiguration> parsed = ModConfiguration
                .parseTierConfigurations(new ForgeConfigSource(reloaded));

            checkEquals(6, parsed.size(), "a gap in the Active flags must not truncate the tier list");
            check(
                parsed.get(0)
                    .isActive(),
                "tier 1 must stay active");
            check(
                !parsed.get(1)
                    .isActive(),
                "tier 2 must be parsed as inactive");
            check(
                parsed.get(2)
                    .isActive(),
                "tier 3 must stay active");
            checkEquals(
                12,
                parsed.get(2)
                    .getMaximumEnergyGeneration(),
                "tier 3 keeps its generation");
            checkEquals(
                1_002,
                parsed.get(2)
                    .getCapacity(),
                "tier 3 keeps its capacity");

            // Register recipes against the parsed list: the inactive tier gets no recipe, the tiers around it do.
            java.lang.reflect.Field tiers = ModConfiguration.class.getDeclaredField("mTierConfigurations");
            tiers.setAccessible(true);
            Object live = tiers.get(null);
            try {
                tiers.set(null, parsed);
                CraftingManager.getInstance()
                    .getRecipeList()
                    .clear();
                new VanillaRecipeRegistrar().registerRecipes();

                check(
                    !hasRecipeFor(
                        ModBlocks.getSolarPanels()
                            .get(1)),
                    "an inactive tier must not get a recipe");
                check(
                    hasRecipeFor(
                        ModBlocks.getSolarPanels()
                            .get(0)),
                    "an active tier must still get a recipe");
                check(
                    hasRecipeFor(
                        ModBlocks.getSolarPanels()
                            .get(2)),
                    "a tier above the gap must still get a recipe");
            } finally {
                tiers.set(null, live);
            }
        } catch (Throwable t) {
            check(false, "the tier activation test threw: " + t);
            t.printStackTrace();
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }

    /** R5: new saves write the namespaced id, old saves with the legacy id still load. */
    private static void checksTileEntityId(World pWorld) {
        int x = -10;
        int z = 10;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        check(block != null, "the first tier block must exist for the tile-entity id test");
        if (block == null) {
            return;
        }

        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        TileEntity tileEntity = pWorld.getTileEntity(x, TEST_HEIGHT, z);
        if (!(tileEntity instanceof SolarPanelTileEntity)) {
            check(false, "the tile-entity id test needs a SolarPanelTileEntity");
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
            return;
        }

        SolarPanelTileEntity panel = (SolarPanelTileEntity) tileEntity;
        panel.setEnergyStored(4242);
        NBTTagCompound tag = new NBTTagCompound();
        panel.writeToNBT(tag);

        checkEquals("solarflux:solar", tag.getString("id"), "new saves must use the namespaced tile-entity id");

        tag.setString("id", "solar");
        TileEntity legacy = TileEntity.createAndLoadEntity(tag);
        check(legacy instanceof SolarPanelTileEntity, "the legacy \"solar\" id must still resolve");
        if (legacy instanceof SolarPanelTileEntity) {
            checkEquals(4242, ((SolarPanelTileEntity) legacy).getEnergyStored(), "a legacy panel keeps its charge");
        }

        pWorld.setBlockToAir(x, TEST_HEIGHT, z);
    }

    // -------------------------------------------------------------------------------------------------------
    // Upgrades, traversal, sharing, dismantle round trip and the furnace upgrade
    // -------------------------------------------------------------------------------------------------------

    /** In-place upgrades through the inventory must change the running panel and survive removal. */
    private static void checksUpgradesInPlace(World pWorld) {
        int x = 20;
        int z = 0;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        TileEntity tileEntity = pWorld.getTileEntity(x, TEST_HEIGHT, z);
        if (!(tileEntity instanceof SolarPanelTileEntity)) {
            check(false, "the upgrades test needs a panel");
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
            return;
        }
        SolarPanelTileEntity panel = (SolarPanelTileEntity) tileEntity;
        int baseCapacity = panel.getMaxEnergyStored();
        int baseTransfer = panel.getEnergyStorage()
            .getMaxTransferExtract();

        panel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeCapacity, 2));
        panel.setInventorySlotContents(1, new ItemStack(ModItems.mUpgradeTransferRate, 2));
        panel.markDirty();

        checkEquals(
            UpgradeMath.apply(
                baseCapacity,
                2,
                ModConfiguration.getCapacityUpgradeIncrease(),
                ModConfiguration.getCapacityUpgradeReturnsToScale()),
            panel.getMaxEnergyStored(),
            "two capacity upgrades must change the capacity");
        checkEquals(
            UpgradeMath.apply(
                baseTransfer,
                2,
                ModConfiguration.getTransferRateUpgradeIncrease(),
                ModConfiguration.getTransferRateUpgradeReturnsToScale()),
            panel.getEnergyStorage()
                .getMaxTransferExtract(),
            "two transfer upgrades must change the transfer rate");

        panel.setEnergyStored(panel.getMaxEnergyStored());
        int upgradedEnergy = panel.getEnergyStored();
        panel.setInventorySlotContents(0, null);
        panel.markDirty();

        checkEquals(baseCapacity, panel.getMaxEnergyStored(), "removing the capacity upgrades restores the base");
        checkEquals(upgradedEnergy, panel.getEnergyStored(), "removing a capacity upgrade must not delete energy");

        pWorld.setBlockToAir(x, TEST_HEIGHT, z);
    }

    /** A traversal upgrade must discover and feed the machine two blocks away. */
    private static void checksTraversalChain(World pWorld) {
        int x = 0;
        int z = 20;
        Block panelBlock = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        Block receiverBlock = GameTestMod.getTestReceiverBlock();
        pWorld.setBlock(x, TEST_HEIGHT, z, panelBlock);
        pWorld.setBlock(x + 1, TEST_HEIGHT, z, receiverBlock);
        pWorld.setBlock(x + 2, TEST_HEIGHT, z, receiverBlock);
        SolarPanelTileEntity panel = (SolarPanelTileEntity) pWorld.getTileEntity(x, TEST_HEIGHT, z);
        GameTestMod.TestEnergyReceiver first = (GameTestMod.TestEnergyReceiver) pWorld
            .getTileEntity(x + 1, TEST_HEIGHT, z);
        final GameTestMod.TestEnergyReceiver second = (GameTestMod.TestEnergyReceiver) pWorld
            .getTileEntity(x + 2, TEST_HEIGHT, z);
        if (panel == null || first == null || second == null) {
            check(false, "the traversal chain arena could not be built");
            clearBlocks(pWorld, x, z, 3);
            return;
        }

        panel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeTraversal, 1));
        panel.markDirty();
        panel.setEnergyStored(panel.getMaxEnergyStored());

        int ticks = tickUntil(panel, pWorld, 200, new Condition() {

            @Override
            public boolean isTrue() {
                return second.getEnergyStored() > 0;
            }
        });

        check(ticks > 0, "the traversal upgrade must reach the second machine within 200 ticks");
        check(first.getEnergyStored() > 0, "the direct neighbour must receive energy too");

        clearBlocks(pWorld, x, z, 3);
    }

    /** Two adjacent panels must share their energy without creating or destroying any. */
    private static void checksEnergySharing(World pWorld) {
        int x = 0;
        int z = 30;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        pWorld.setBlock(x + 1, TEST_HEIGHT, z, block);
        SolarPanelTileEntity west = (SolarPanelTileEntity) pWorld.getTileEntity(x, TEST_HEIGHT, z);
        SolarPanelTileEntity east = (SolarPanelTileEntity) pWorld.getTileEntity(x + 1, TEST_HEIGHT, z);
        if (west == null || east == null) {
            check(false, "the sharing arena could not be built");
            clearBlocks(pWorld, x, z, 2);
            return;
        }

        west.setEnergyStored(west.getMaxEnergyStored());
        east.setEnergyStored(0);
        int total = west.getEnergyStored() + east.getEnergyStorage()
            .getEnergyStored();

        // Midnight: otherwise the panels generate while we are only testing the sharing module.
        long previousTime = pWorld.getWorldTime();
        pWorld.setWorldTime(18000L);
        try {
            for (int tick = 0; tick < 300; ++tick) {
                pWorld.getWorldInfo()
                    .incrementTotalWorldTime(pWorld.getTotalWorldTime() + 1L);
                west.updateEntity();
                east.updateEntity();
                if (west.getEnergyStored() > 0 && east.getEnergyStored() > 0) {
                    break;
                }
            }
        } finally {
            pWorld.setWorldTime(previousTime);
        }

        check(west.getEnergyStored() > 0, "the full panel must keep some energy");
        check(east.getEnergyStored() > 0, "the empty panel must receive shared energy");
        checkEquals(total, west.getEnergyStored() + east.getEnergyStored(), "sharing must conserve energy");

        clearBlocks(pWorld, x, z, 2);
    }

    /** Wrench it with the CoFH hammer, then place the drop back and check its contents. */
    private static void checksDismantleRoundTrip(World pWorld) {
        int x = 0;
        int z = 40;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        SolarPanelTileEntity panel = (SolarPanelTileEntity) pWorld.getTileEntity(x, TEST_HEIGHT, z);
        if (panel == null) {
            check(false, "the dismantle round trip needs a panel");
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
            return;
        }
        panel.setEnergyStored(1234);
        panel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeEfficiency, 2));
        panel.markDirty();

        FakePlayer player = FakePlayerFactory.getMinecraft((WorldServer) pWorld);
        try {
            player.inventory.mainInventory[0] = new ItemStack(GameTestMod.getTestHammerItem());
            player.inventory.currentItem = 0;
            player.setSneaking(true);

            check(
                block.onBlockActivated(pWorld, x, TEST_HEIGHT, z, player, 1, 0.5F, 0.5F, 0.5F),
                "the wrench click must be handled");
            check(pWorld.isAirBlock(x, TEST_HEIGHT, z), "the panel must be dismantled");

            EntityItem drop = findDrop(pWorld, x, TEST_HEIGHT, z);
            check(drop != null, "dismantling must spawn the panel item");
            if (drop == null) {
                return;
            }
            ItemStack stack = drop.getEntityItem();
            check(stack.getTagCompound() != null, "the drop must carry the panel contents");
            if (stack.getTagCompound() != null) {
                checkEquals(
                    1234,
                    stack.getTagCompound()
                        .getInteger(NBTConstants.ENERGY),
                    "the drop must carry the charge");
            }

            pWorld.setBlock(x, TEST_HEIGHT, z, block);
            block.onBlockPlacedBy(pWorld, x, TEST_HEIGHT, z, player, stack);
            SolarPanelTileEntity placed = (SolarPanelTileEntity) pWorld.getTileEntity(x, TEST_HEIGHT, z);
            check(placed != null, "placing the drop must create a panel");
            if (placed != null) {
                checkEquals(1234, placed.getEnergyStored(), "the placed panel must keep its charge");
                checkEquals(
                    2,
                    placed.getUpgradeCount(ModItems.mUpgradeEfficiency),
                    "the placed panel must keep its upgrades");
            }
            drop.setDead();
        } finally {
            player.inventory.mainInventory[0] = null;
            player.setSneaking(false);
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
        }
    }

    /** The furnace upgrade must prepay a real vanilla furnace. */
    private static void checksFurnaceUpgrade(World pWorld) {
        int x = 0;
        int z = 50;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        pWorld.setBlock(x + 1, TEST_HEIGHT, z, Blocks.furnace);
        SolarPanelTileEntity panel = (SolarPanelTileEntity) pWorld.getTileEntity(x, TEST_HEIGHT, z);
        TileEntity tileEntity = pWorld.getTileEntity(x + 1, TEST_HEIGHT, z);
        if (panel == null || !(tileEntity instanceof TileEntityFurnace)) {
            check(false, "the furnace arena could not be built");
            clearBlocks(pWorld, x, z, 2);
            return;
        }
        TileEntityFurnace furnace = (TileEntityFurnace) tileEntity;
        furnace.setInventorySlotContents(0, new ItemStack(Items.coal));
        panel.setInventorySlotContents(0, new ItemStack(ModItems.mUpgradeFurnace, 1));
        panel.markDirty();
        panel.setEnergyStored(panel.getMaxEnergyStored());

        boolean burning = false;
        for (int tick = 0; tick < 400 && !burning; ++tick) {
            pWorld.getWorldInfo()
                .incrementTotalWorldTime(pWorld.getTotalWorldTime() + 1L);
            panel.updateEntity();
            burning = furnace.furnaceBurnTime > 0;
        }

        check(burning, "the furnace upgrade must light the furnace within 400 ticks");
        check(
            panel.getEnergyStored() < panel.getMaxEnergyStored(),
            "the furnace upgrade must consume the panel's energy");

        clearBlocks(pWorld, x, z, 2);
    }

    /** Every active tier above the first must use the previous tier in its recipe. */
    private static void checksRecipeContent() {
        for (int tier = 1; tier < ModBlocks.getSolarPanels()
            .size(); ++tier) {
            if (!ModConfiguration.getTierConfiguration(tier)
                .isActive()) {
                continue;
            }
            Block panel = ModBlocks.getSolarPanels()
                .get(tier);
            Block previous = ModBlocks.getSolarPanels()
                .get(tier - 1);
            ShapedOreRecipe recipe = findPanelRecipe(panel);
            check(recipe != null, "tier " + (tier + 1) + " must have a shaped recipe");
            if (recipe != null) {
                check(recipeUses(recipe, previous), "the tier " + (tier + 1) + " recipe must use the tier " + tier);
            }
        }
    }

    private static ShapedOreRecipe findPanelRecipe(Block pPanel) {
        for (Object entry : CraftingManager.getInstance()
            .getRecipeList()) {
            if (entry instanceof ShapedOreRecipe) {
                ItemStack output = ((ShapedOreRecipe) entry).getRecipeOutput();
                if (output != null && Block.getBlockFromItem(output.getItem()) == pPanel) {
                    return (ShapedOreRecipe) entry;
                }
            }
        }
        return null;
    }

    private static boolean recipeUses(ShapedOreRecipe pRecipe, Block pBlock) {
        for (Object input : pRecipe.getInput()) {
            if (input instanceof ItemStack) {
                if (Block.getBlockFromItem(((ItemStack) input).getItem()) == pBlock) {
                    return true;
                }
            } else if (input instanceof java.util.List) {
                for (Object alternative : (java.util.List<?>) input) {
                    if (alternative instanceof ItemStack
                        && Block.getBlockFromItem(((ItemStack) alternative).getItem()) == pBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static EntityItem findDrop(World pWorld, int pX, int pY, int pZ) {
        for (Object entry : pWorld.loadedEntityList) {
            if (entry instanceof EntityItem) {
                EntityItem item = (EntityItem) entry;
                if (Math.abs(item.posX - (pX + 0.5D)) < 1.5D && Math.abs(item.posY - (pY + 0.5D)) < 1.5D
                    && Math.abs(item.posZ - (pZ + 0.5D)) < 1.5D) {
                    return item;
                }
            }
        }
        return null;
    }

    private static void clearBlocks(World pWorld, int pX, int pZ, int pCount) {
        for (int i = 0; i < pCount; ++i) {
            pWorld.setBlockToAir(pX + i, TEST_HEIGHT, pZ);
        }
    }

    /** Every active tier must have a name in the shipped English lang file (custom tiers included). */
    private static void checksLocalisation() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        java.io.InputStream stream = SolarFluxGameTests.class.getResourceAsStream("/assets/solarflux/lang/en_US.lang");
        if (stream == null) {
            check(false, "the English lang file must be on the classpath");
            return;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (!line.isEmpty() && !line.startsWith("#") && separator > 0) {
                    keys.add(line.substring(0, separator));
                }
            }
        } catch (java.io.IOException e) {
            check(false, "reading the lang file threw: " + e);
            return;
        }

        for (int tier = 0; tier < ModBlocks.getSolarPanels()
            .size(); ++tier) {
            if (!ModConfiguration.getTierConfiguration(tier)
                .isActive()) {
                continue;
            }
            String key = "tile.solarflux:solar" + tier + ".name";
            check(keys.contains(key), "active tier " + (tier + 1) + " needs " + key);
        }
    }

    /** The real weather and time paths, not just the pure sun model: midnight is dark, rain scales noon output. */
    private static void checksSunInSitu(World pWorld) {
        int x = 0;
        int z = 60;
        Block block = GameRegistry.findBlock(Reference.MOD_ID, "solar1");
        pWorld.setBlock(x, TEST_HEIGHT, z, block);
        SolarPanelTileEntity panel = (SolarPanelTileEntity) pWorld.getTileEntity(x, TEST_HEIGHT, z);
        check(panel != null, "the sun-in-situ test needs a panel");
        if (panel == null) {
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
            return;
        }

        long previousTime = pWorld.getWorldTime();
        // World.isRaining() reads the smoothed strength, not WorldInfo.raining; set it directly so the check does not
        // depend on the server's weather ramp.
        float previousRaining = pWorld.rainingStrength;
        float previousRainingPrevious = pWorld.prevRainingStrength;
        try {
            pWorld.setWorldTime(18000L);
            panel.setEnergyStored(0);
            panel.setCurrentEnergyGeneration(0);
            for (int tick = 0; tick < 40; ++tick) {
                pWorld.getWorldInfo()
                    .incrementTotalWorldTime(pWorld.getTotalWorldTime() + 1L);
                panel.updateEntity();
            }
            checkEquals(0, panel.getCurrentEnergyGeneration(), "midnight generation");
            checkEquals(0, panel.getEnergyStored(), "midnight stored energy");

            pWorld.setWorldTime(6000L);
            pWorld.rainingStrength = 1.0F;
            pWorld.prevRainingStrength = 1.0F;
            int ticks = tickUntil(panel, pWorld, 100, new Condition() {

                @Override
                public boolean isTrue() {
                    return panel.getCurrentEnergyGeneration() > 0;
                }
            });
            check(ticks > 0, "rain at noon must still generate");
            checkEquals(
                Math.round(panel.getMaximumEnergyGeneration() * ModConfiguration.getRainGenerationFactor()),
                panel.getCurrentEnergyGeneration(),
                "rain must scale the generation");
        } finally {
            pWorld.rainingStrength = previousRaining;
            pWorld.prevRainingStrength = previousRainingPrevious;
            pWorld.setWorldTime(previousTime);
            pWorld.setBlockToAir(x, TEST_HEIGHT, z);
        }
    }
}
