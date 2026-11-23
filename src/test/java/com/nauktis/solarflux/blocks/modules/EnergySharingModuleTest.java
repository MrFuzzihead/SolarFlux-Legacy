package com.nauktis.solarflux.blocks.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.world.World;

import org.junit.Before;
import org.junit.Test;

import com.nauktis.solarflux.blocks.SolarPanelTileEntity;
import com.nauktis.solarflux.support.TestFixture;

public class EnergySharingModuleTest {

    private World world;
    private SolarPanelTileEntity panel;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.installUpgradeItems();

        world = mock(World.class);
        panel = new SolarPanelTileEntity(0);
        TestFixture.setWorld(panel, world);
        panel.xCoord = 0;
        panel.yCoord = 70;
        panel.zCoord = 0;
    }

    private long alignedWorldTime(int pRate) {
        for (int time = 0; time < pRate; ++time) {
            when(world.getTotalWorldTime()).thenReturn((long) time);
            if (panel.atTickRate(pRate)) {
                return time;
            }
        }
        throw new IllegalStateException("no tick aligns with the rate " + pRate);
    }

    @Test
    public void theConfigurationAddsTheSharingModuleToANewPanel() {
        // applyDefaultConfiguration leaves BalanceEnergy on, which is the shipped default.
        assertNotNull(TestFixture.getField(panel, "mEnergySharingModule"));
    }

    @Test
    public void balancingOnlyHappensEveryFiveSeconds() {
        SolarPanelTileEntity neighbour = new SolarPanelTileEntity(0);
        TestFixture.setWorld(neighbour, world);
        neighbour.xCoord = 1;
        neighbour.yCoord = 70;
        neighbour.zCoord = 0;
        when(world.getTileEntity(1, 70, 0)).thenReturn(neighbour);
        panel.setEnergyStored(800);
        EnergySharingModule module = new EnergySharingModule(panel);

        long aligned = alignedWorldTime(100);
        when(world.getTotalWorldTime()).thenReturn(aligned + 1);
        module.tick();
        assertEquals(800, panel.getEnergyStored());
        assertEquals(0, neighbour.getEnergyStored());

        when(world.getTotalWorldTime()).thenReturn(aligned);
        module.tick();
        assertEquals(400, panel.getEnergyStored());
        assertEquals(400, neighbour.getEnergyStored());
    }

    @Test
    public void theEastAndSouthNeighboursAreBothConsidered() {
        SolarPanelTileEntity east = new SolarPanelTileEntity(0);
        TestFixture.setWorld(east, world);
        east.xCoord = 1;
        east.yCoord = 70;
        east.zCoord = 0;
        SolarPanelTileEntity south = new SolarPanelTileEntity(0);
        TestFixture.setWorld(south, world);
        south.xCoord = 0;
        south.yCoord = 70;
        south.zCoord = 1;
        when(world.getTileEntity(1, 70, 0)).thenReturn(east);
        when(world.getTileEntity(0, 70, 1)).thenReturn(south);
        panel.setEnergyStored(900);
        EnergySharingModule module = new EnergySharingModule(panel);

        long aligned = alignedWorldTime(100);
        when(world.getTotalWorldTime()).thenReturn(aligned);
        module.tick();

        // East is balanced first (900 -> 450/450), then south (450 -> 225/225).
        assertEquals(225, panel.getEnergyStored());
        assertEquals(450, east.getEnergyStored());
        assertEquals(225, south.getEnergyStored());
        assertEquals(900, panel.getEnergyStored() + east.getEnergyStored() + south.getEnergyStored());
    }

    @Test
    public void somethingThatIsNotAPanelIsIgnored() {
        when(world.getTileEntity(1, 70, 0)).thenReturn(new net.minecraft.tileentity.TileEntity());
        panel.setEnergyStored(500);
        EnergySharingModule module = new EnergySharingModule(panel);

        long aligned = alignedWorldTime(100);
        when(world.getTotalWorldTime()).thenReturn(aligned);
        module.tick();

        assertEquals(500, panel.getEnergyStored());
    }
}
