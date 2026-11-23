package com.mrfuzzihead.solarflux.gametest;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyReceiver;
import cofh.api.item.IToolHammer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Test-only mod: a separate, non-shipped {@code @Mod} that asserts Solar Flux works against the real game.
 *
 * <p>
 * It lives in the {@code gameTest} source set and is only put on the classpath by {@code ./gradlew gameTestServer} (or
 * {@code -PgameTest}), so it never reaches the published jar. Run it with:
 * </p>
 *
 * <pre>
 * ./gradlew gameTestServer
 * </pre>
 *
 * <p>
 * The assertions themselves live in {@link SolarFluxGameTests}; this class owns the server lifecycle, the test-only
 * energy receiver block and the exit code. Every assertion runs on the server thread at
 * {@link FMLServerStartedEvent}, where the overworld exists and block mutations are legal.
 * </p>
 */
@Mod(
    modid = GameTestMod.MOD_ID,
    name = "Solar Flux Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:SolarFlux")
public class GameTestMod {

    public static final String MOD_ID = "solarfluxgametest";
    static final String OK_MARKER = "SOLARFLUX_GAMETEST_OK";
    static final String FAIL_MARKER = "SOLARFLUX_GAMETEST_FAILED";

    private static final String RECEIVER_BLOCK_NAME = "solarflux_test_receiver";
    private static final String HAMMER_ITEM_NAME = "solarflux_test_hammer";

    private static Block sTestReceiverBlock;
    private static Item sTestHammerItem;

    private boolean ran;

    static Block getTestReceiverBlock() {
        return sTestReceiverBlock;
    }

    static Item getTestHammerItem() {
        return sTestHammerItem;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        sTestReceiverBlock = new TestReceiverBlock();
        GameRegistry.registerBlock(sTestReceiverBlock, RECEIVER_BLOCK_NAME);
        GameRegistry.registerTileEntity(TestEnergyReceiver.class, RECEIVER_BLOCK_NAME + "_tile");

        sTestHammerItem = new TestHammerItem();
        GameRegistry.registerItem(sTestHammerItem, HAMMER_ITEM_NAME);

        // The client tick hook lives on this instance; without it the client half never runs.
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /**
     * Client half of the harness: runs once at the main menu and exits the client with the result.
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent pEvent) {
        if (pEvent.phase == TickEvent.Phase.END && Boolean.getBoolean("solarflux.gameTest")) {
            ClientGameTests.onClientTick();
        }
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (ran) {
            return;
        }
        ran = true;

        // The test mod only runs when the build actually asked for it, so a stray copy on a normal dev classpath is
        // inert instead of failing a launch.
        if (!Boolean.getBoolean("solarflux.gameTest")) {
            System.out.println("[SOLARFLUX-GAMETEST] solarflux.gameTest is not set, skipping");
            return;
        }

        WorldServer overworld = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (overworld == null) {
            fail("the overworld is not available");
            return;
        }

        try {
            SolarFluxGameTests.runAll(overworld);
        } catch (Throwable t) {
            t.printStackTrace();
            fail("the game tests threw: " + t);
            return;
        }

        int checks = SolarFluxGameTests.checkCount();
        int failures = SolarFluxGameTests.failureCount();
        System.out.println("[SOLARFLUX-GAMETEST] " + (checks - failures) + "/" + checks + " checks passed");
        if (failures > 0) {
            fail(failures + " assertion(s) failed, see above");
            return;
        }
        System.out.println(OK_MARKER);
        exit(0);
    }

    private void fail(String pMessage) {
        System.out.println(FAIL_MARKER + ": " + pMessage);
        exit(1);
    }

    /**
     * Called from the server thread: hand the exit to a worker so the server can return from its callback before
     * {@code Runtime.exit} starts the shutdown hooks.
     */
    private static void exit(int pStatus) {
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                FMLCommonHandler.instance()
                    .exitJava(pStatus, false);
            }
        }, "SolarFlux-game-test-exit");
        worker.setDaemon(true);
        worker.start();
    }

    /** A plain block whose only job is to carry {@link TestEnergyReceiver}. */
    public static class TestReceiverBlock extends Block implements ITileEntityProvider {

        public TestReceiverBlock() {
            super(Material.rock);
            setBlockName("solarflux_test_receiver");
        }

        @Override
        public TileEntity createNewTileEntity(World pWorld, int pMetadata) {
            return new TestEnergyReceiver();
        }
    }

    /** An energy sink the dispenser module can hand energy to, and that records what it received. */
    public static class TestEnergyReceiver extends TileEntity implements IEnergyReceiver {

        private int mReceived;

        public int getEnergyStored() {
            return mReceived;
        }

        @Override
        public int receiveEnergy(ForgeDirection pFrom, int pMaxReceive, boolean pSimulate) {
            int accepted = Math.max(Math.min(pMaxReceive, Integer.MAX_VALUE - mReceived), 0);
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
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canConnectEnergy(ForgeDirection pFrom) {
            return true;
        }
    }

    /** A CoFH-style hammer, so the wrench path can be exercised without a second mod installed. */
    public static class TestHammerItem extends Item implements IToolHammer {

        TestHammerItem() {
            setUnlocalizedName("solarflux_test_hammer");
        }

        @Override
        public boolean isUsable(ItemStack pStack, EntityLivingBase pUser, int pX, int pY, int pZ) {
            return true;
        }

        @Override
        public void toolUsed(ItemStack pStack, EntityLivingBase pUser, int pX, int pY, int pZ) {
            // Nothing to do: the game test only checks the usability decision.
        }
    }
}
