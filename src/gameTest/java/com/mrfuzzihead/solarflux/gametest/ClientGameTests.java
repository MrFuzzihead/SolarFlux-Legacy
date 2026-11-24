package com.mrfuzzihead.solarflux.gametest;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.common.config.Configuration;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.config.ModConfigurationGui;
import com.mrfuzzihead.solarflux.gui.GuiSolarPanel;
import com.mrfuzzihead.solarflux.utility.Lang;

import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The client half of the in-game test mod.
 *
 * <p>
 * Runs once, from the main menu, so the client-only classes are guaranteed to be loaded and the language file is
 * active. The checks cover what a dedicated server cannot reach: the panel GUI title (G1) and the contents of the
 * configuration GUI (G3). The client is exited as soon as they finish.
 * </p>
 */
@SideOnly(Side.CLIENT)
final class ClientGameTests {

    static final String OK_MARKER = "SOLARFLUX_CLIENTTEST_OK";
    static final String FAIL_MARKER = "SOLARFLUX_CLIENTTEST_FAILED";

    /** Roughly a minute: long enough for mod loading and resource packs, short enough to fail fast in CI. */
    private static final int MAIN_MENU_TIMEOUT_TICKS = 20 * 60;

    private static int sTicks;
    private static boolean sRan;
    private static int sChecks;
    private static int sFailures;

    private ClientGameTests() {}

    static void onClientTick() {
        if (sRan) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiMainMenu)) {
            if (++sTicks > MAIN_MENU_TIMEOUT_TICKS) {
                sRan = true;
                fail("the client never reached the main menu");
            }
            return;
        }

        sRan = true;
        try {
            checkPanelTitle();
            checkConfigurationGui(mc);
        } catch (Throwable t) {
            ++sFailures;
            System.out.println("[SOLARFLUX-CLIENTTEST] FAILED with an exception: " + t);
            t.printStackTrace();
        }

        System.out.println("[SOLARFLUX-CLIENTTEST] " + (sChecks - sFailures) + "/" + sChecks + " checks passed");
        if (sFailures > 0) {
            fail(sFailures + " client assertion(s) failed");
            return;
        }
        System.out.println(OK_MARKER);
        exit(0);
    }

    /** G1: the title is the panel's localised name, not the player's always-"Inventory" name. */
    private static void checkPanelTitle() {
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        GuiSolarPanel gui = new GuiSolarPanel(new InventoryPlayer(null), panel);

        String expected = Lang.localise(panel.getInventoryName(), false);
        String title = gui.getPanelTitle();

        check(
            expected.equals(title),
            "the GUI title must be the panel's localised name (expected \"" + expected + "\", got \"" + title + "\")");
        check(!"Inventory".equalsIgnoreCase(title), "the GUI title must not be the player's inventory name");
        check(
            !expected.startsWith("container.") && !expected.startsWith("info."),
            "the panel title must actually be translated on the client (got \"" + expected + "\")");
    }

    /** G3: the configuration GUI carries every category, not just the general one. */
    private static void checkConfigurationGui(Minecraft mc) {
        ModConfigurationGui gui = new ModConfigurationGui(mc.currentScreen);
        List<IConfigElement> elements = gui.configElements;

        boolean general = false;
        boolean upgrades = false;
        boolean tier = false;
        for (IConfigElement element : elements) {
            String name = element.getName();
            general |= Configuration.CATEGORY_GENERAL.equals(name);
            upgrades |= "upgrades".equals(name);
            tier |= name != null && name.startsWith("solar_panel_tier");
        }

        check(general, "the config GUI must expose the general category");
        check(upgrades, "the config GUI must expose the upgrades category");
        check(tier, "the config GUI must expose the solar panel tier categories");
    }

    private static void check(boolean pCondition, String pDescription) {
        ++sChecks;
        if (!pCondition) {
            ++sFailures;
            System.out.println("[SOLARFLUX-CLIENTTEST] FAILED: " + pDescription);
        }
    }

    private static void fail(String pMessage) {
        System.out.println(FAIL_MARKER + ": " + pMessage);
        exit(1);
    }

    /** Hand the exit to a worker so the client can return from its tick callback before the JVM stops. */
    private static void exit(int pStatus) {
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                FMLCommonHandler.instance()
                    .exitJava(pStatus, false);
            }
        }, "SolarFlux-client-test-exit");
        worker.setDaemon(true);
        worker.start();
    }
}
