package com.mrfuzzihead.solarflux.gui;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.support.TestFixture;

public class GuiHandlerTest {

    private GuiHandler mHandler;
    private World mWorld;
    private EntityPlayer mPlayer;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();

        mHandler = new GuiHandler();
        mWorld = mock(World.class);
        mPlayer = mock(EntityPlayer.class);
        TestFixture.setInstanceField(mPlayer, EntityPlayer.class, "inventory", new InventoryPlayer(null));
    }

    @Test
    public void aPanelProvidesServerContainer() {
        when(mWorld.getTileEntity(1, 2, 3)).thenReturn(new SolarPanelTileEntity(0));

        Object element = mHandler.getServerGuiElement(0, mPlayer, mWorld, 1, 2, 3);

        assertTrue(element instanceof ContainerSolarPanel);
    }

    @Test
    public void aMissingOrWrongTileProvidesNoContainer() {
        when(mWorld.getTileEntity(1, 2, 3)).thenReturn(null);
        assertNull(mHandler.getServerGuiElement(0, mPlayer, mWorld, 1, 2, 3));

        when(mWorld.getTileEntity(1, 2, 3)).thenReturn(new TileEntity());
        assertNull(mHandler.getServerGuiElement(0, mPlayer, mWorld, 1, 2, 3));
    }
}
