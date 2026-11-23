package com.mrfuzzihead.solarflux.core.block.icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.support.TestFixture;

/**
 * The connected-texture handler packs the four neighbours of a face into a 4 bit texture index. Getting one bit wrong
 * only shows up as the wrong texture in game, so the packing is pinned down here.
 */
public class ConnectedIconHandlerTest {

    private ConnectedIconHandler handler;
    private IIcon[] icons;
    private Block panel;
    private Block other;

    @Before
    public void setUp() {
        handler = new ConnectedIconHandler("SolarFlux", "solar0_");
        icons = new IIcon[16];
        IIconRegister register = mock(IIconRegister.class);
        for (int i = 0; i < icons.length; ++i) {
            icons[i] = mock(IIcon.class);
            when(register.registerIcon("solarflux:solar0_" + i)).thenReturn(icons[i]);
        }
        handler.registerBlockIcons(register);

        panel = mock(Block.class);
        other = mock(Block.class);
    }

    /** A world in which only the panel at the origin and the given neighbours are solar panels. */
    private IBlockAccess worldWithNeighbours(int[]... pNeighbours) {
        IBlockAccess access = mock(IBlockAccess.class);
        when(access.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(other);
        when(access.getBlock(0, 0, 0)).thenReturn(panel);
        for (int[] neighbour : pNeighbours) {
            when(access.getBlock(neighbour[0], neighbour[1], neighbour[2])).thenReturn(panel);
        }
        return access;
    }

    @Test
    public void allSixteenTextureVariantsAreRegistered() {
        IIconRegister register = mock(IIconRegister.class);
        for (int i = 0; i < 16; ++i) {
            when(register.registerIcon("solarflux:solar0_" + i)).thenReturn(icons[i]);
        }

        handler.registerBlockIcons(register);

        for (int i = 0; i < 16; ++i) {
            verify(register).registerIcon("solarflux:solar0_" + i);
        }
    }

    @Test
    public void theBaseIconIsTheUnconnectedTexture() {
        assertEquals(icons[0], handler.getIcon(0, 0));
        assertEquals(icons[0], handler.getIcon(1, 7));
    }

    @Test
    public void anIsolatedPanelUsesTextureZero() {
        assertEquals(icons[0], handler.getIcon(worldWithNeighbours(), 0, 0, 0, 0));
    }

    @Test
    public void theNorthEdgeOfTheBottomFaceOwnsTheFirstBit() {
        // Face 0 is the bottom; its "up" edge points east.
        assertEquals(icons[1], handler.getIcon(worldWithNeighbours(new int[] { 1, 0, 0 }), 0, 0, 0, 0));
    }

    @Test
    public void theEastEdgeOfTheBottomFaceOwnsTheSecondBit() {
        assertEquals(icons[2], handler.getIcon(worldWithNeighbours(new int[] { 0, 0, 1 }), 0, 0, 0, 0));
    }

    @Test
    public void theSouthEdgeOfTheBottomFaceOwnsTheThirdBit() {
        assertEquals(icons[4], handler.getIcon(worldWithNeighbours(new int[] { -1, 0, 0 }), 0, 0, 0, 0));
    }

    @Test
    public void theWestEdgeOfTheBottomFaceOwnsTheFourthBit() {
        assertEquals(icons[8], handler.getIcon(worldWithNeighbours(new int[] { 0, 0, -1 }), 0, 0, 0, 0));
    }

    @Test
    public void aFullySurroundedFaceUsesTheLastTexture() {
        IBlockAccess access = worldWithNeighbours(
            new int[] { 1, 0, 0 },
            new int[] { 0, 0, 1 },
            new int[] { -1, 0, 0 },
            new int[] { 0, 0, -1 });

        assertEquals(icons[15], handler.getIcon(access, 0, 0, 0, 0));
    }

    @Test
    public void theBottomFaceLooksAtHorizontalNeighbours() {
        // A panel directly below must not change the texture of the bottom face.
        IBlockAccess access = worldWithNeighbours(new int[] { 0, -1, 0 });

        assertEquals(icons[0], handler.getIcon(access, 0, 0, 0, 0));
    }

    @Test
    public void aVerticalNeighbourDoesNotAffectTheTopFace() {
        assertEquals(icons[0], handler.getIcon(worldWithNeighbours(new int[] { 0, 1, 0 }), 0, 0, 0, 1));
    }

    @Test
    public void theTopFaceOnlyLooksAtItsHorizontalNeighbours() {
        // Face 1 is the top; its "up" edge points north.
        assertEquals(icons[1], handler.getIcon(worldWithNeighbours(new int[] { 0, 0, -1 }), 0, 0, 0, 1));
        assertEquals(icons[2], handler.getIcon(worldWithNeighbours(new int[] { 1, 0, 0 }), 0, 0, 0, 1));
        assertEquals(icons[4], handler.getIcon(worldWithNeighbours(new int[] { 0, 0, 1 }), 0, 0, 0, 1));
        assertEquals(icons[8], handler.getIcon(worldWithNeighbours(new int[] { -1, 0, 0 }), 0, 0, 0, 1));
    }

    @Test
    public void theTextureIsNullBeforeTheIconsAreRegistered() {
        assertNull(new ConnectedIconHandler("SolarFlux", "solar0_").getIcon(0, 0));
    }

    @Test
    public void connectedTexturesAreTheShippedDefault() {
        TestFixture.applyDefaultConfiguration();

        assertTrue(ModConfiguration.useConnectedTextures());
    }
}
