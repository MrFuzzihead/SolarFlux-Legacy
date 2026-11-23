package com.mrfuzzihead.solarflux.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.config.ModConfiguration;
import com.mrfuzzihead.solarflux.core.block.icon.IBlockIconHandler;
import com.mrfuzzihead.solarflux.core.block.icon.SingleIconHandler;
import com.mrfuzzihead.solarflux.core.block.icon.TopIconHandler;
import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.reference.NBTConstants;
import com.mrfuzzihead.solarflux.support.TestFixture;

public class SolarPanelBlockTest {

    private IBlockIconHandler iconHandler;
    private SolarPanelBlock block;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();
        iconHandler = new TopIconHandler(
            new SingleIconHandler("SolarFlux", "solar0_0"),
            new SingleIconHandler("SolarFlux", "solar0_side"));
        block = new SolarPanelBlock("solar0", iconHandler, 2);
    }

    @Test
    public void theTierIndexIsKept() {
        assertEquals(2, block.getTierIndex());
    }

    @Test
    public void theUnlocalizedNameFollowsTheModConvention() {
        assertEquals("tile.solarflux:solar0", block.getUnlocalizedName());
    }

    @Test
    public void aPartialPanelIsNotACube() {
        assertFalse(block.renderAsNormalBlock());
        assertFalse(block.isOpaqueCube());
    }

    @Test
    public void aFullHeightPanelBehavesLikeANormalBlock() {
        TestFixture.setStatic(ModConfiguration.class, "mSolarPanelHeight", 1.0F);

        SolarPanelBlock full = new SolarPanelBlock("solar0", iconHandler, 0);

        assertTrue(full.renderAsNormalBlock());
        assertTrue(full.isOpaqueCube());
    }

    @Test
    public void theBlockIsHarderThanDirtAndBlastResistant() {
        assertEquals(3.0F, block.getBlockHardness(null, 0, 0, 0), 0.0F);
        // setResistance(5) stores 5 * 3 and getExplosionResistance divides by 5 again.
        assertEquals(3.0F, block.getExplosionResistance(null), 0.0F);
    }

    @Test
    public void theBlockCreatesItsOwnTileEntityAtItsTier() {
        TileEntity tileEntity = block.createNewTileEntity(null, 0);

        assertTrue(tileEntity instanceof SolarPanelTileEntity);
        assertEquals(
            ModConfiguration.DEFAULT_TIER_CONFIGURATIONS.get(2)
                .getMaximumEnergyGeneration(),
            ((SolarPanelTileEntity) tileEntity).getTierConfiguration()
                .getMaximumEnergyGeneration());
    }

    @Test
    public void theIconsAreDelegatedToTheHandler() {
        IIconRegister register = mock(IIconRegister.class);
        IIcon top = mock(IIcon.class);
        IIcon side = mock(IIcon.class);
        when(register.registerIcon("solarflux:solar0_0")).thenReturn(top);
        when(register.registerIcon("solarflux:solar0_side")).thenReturn(side);

        block.registerBlockIcons(register);

        assertEquals(top, block.getIcon(1, 0));
        assertEquals(side, block.getIcon(0, 0));

        IBlockAccess blockAccess = mock(IBlockAccess.class);
        assertEquals(side, block.getIcon(blockAccess, 0, 0, 0, 4));
    }

    @Test
    public void breakingTheBlockDropsThePanelInventory() {
        World world = mock(World.class);
        TestFixture.setInstanceField(world, World.class, "provider", mock(net.minecraft.world.WorldProvider.class));
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        when(world.getTileEntity(1, 2, 3)).thenReturn(panel);
        panel.setInventorySlotContents(0, TestFixture.stack(ModItems.mUpgradeEfficiency, 2, 0));

        block.breakBlock(world, 1, 2, 3, block, 0);

        verify(world, org.mockito.Mockito.atLeastOnce())
            .spawnEntityInWorld(any(net.minecraft.entity.item.EntityItem.class));
        assertEquals(0, panel.getStackInSlot(0).stackSize);
    }

    @Test
    public void placingAPanelWithoutATileEntityDoesNotThrow() {
        World world = mock(World.class);
        when(world.getTileEntity(1, 2, 3)).thenReturn(null);
        ItemStack item = TestFixture.stack(1, 0);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setInteger(NBTConstants.ENERGY, 123);
        item.setTagCompound(tag);

        block.onBlockPlacedBy(world, 1, 2, 3, mock(net.minecraft.entity.EntityLivingBase.class), item);
    }

    @Test
    public void dismantlingAPanelWithoutATileEntityDropsTheItem() {
        World world = mock(World.class);
        TestFixture.setInstanceField(world, World.class, "provider", mock(net.minecraft.world.WorldProvider.class));
        when(world.getTileEntity(1, 2, 3)).thenReturn(null);
        org.mockito.ArgumentCaptor<net.minecraft.entity.item.EntityItem> captor = org.mockito.ArgumentCaptor
            .forClass(net.minecraft.entity.item.EntityItem.class);

        dismantle(world, 1, 2, 3);

        verify(world).spawnEntityInWorld(captor.capture());
        assertNull(
            captor.getValue()
                .getEntityItem()
                .getTagCompound());
        verify(world).setBlockToAir(1, 2, 3);
    }

    @Test
    public void theClientDoesNotDropTheInventory() {
        World world = mock(World.class);
        world.isRemote = true;
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        when(world.getTileEntity(1, 2, 3)).thenReturn(panel);
        panel.setInventorySlotContents(0, TestFixture.stack(ModItems.mUpgradeEfficiency, 2, 0));

        block.breakBlock(world, 1, 2, 3, block, 0);

        verify(world, never()).spawnEntityInWorld(any(net.minecraft.entity.item.EntityItem.class));
        assertEquals(2, panel.getStackInSlot(0).stackSize);
    }

    @Test
    public void breakingAPanelWithoutAnInventoryDropsNothing() {
        World world = mock(World.class);
        when(world.getTileEntity(1, 2, 3)).thenReturn(null);

        block.breakBlock(world, 1, 2, 3, block, 0);

        verify(world, never()).spawnEntityInWorld(any(net.minecraft.entity.item.EntityItem.class));
    }

    @Test
    public void aClientEventIsForwardedToTheTileEntity() {
        World world = mock(World.class);
        SolarPanelTileEntity panel = mock(SolarPanelTileEntity.class);
        when(world.getTileEntity(4, 5, 6)).thenReturn(panel);
        when(panel.receiveClientEvent(1, 2)).thenReturn(true);

        assertTrue(block.onBlockEventReceived(world, 4, 5, 6, 1, 2));
    }

    @Test
    public void aClientEventWithoutATileEntityIsNotHandled() {
        World world = mock(World.class);
        when(world.getTileEntity(4, 5, 6)).thenReturn(null);

        assertFalse(block.onBlockEventReceived(world, 4, 5, 6, 1, 2));
    }

    @Test
    public void usingAPanelWithoutSneakingOpensTheGui() {
        World world = mock(World.class);
        EntityPlayer player = mock(EntityPlayer.class);
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        when(world.getTileEntity(7, 8, 9)).thenReturn(panel);
        when(player.isSneaking()).thenReturn(false);

        assertTrue(block.onBlockActivated(world, 7, 8, 9, player, 1, 0.5F, 0.5F, 0.5F));

        verify(player).openGui(null, 0, world, 7, 8, 9);
    }

    @Test
    public void usingAPanelWhereAnotherBlockWasReplacedDoesNothing() {
        World world = mock(World.class);
        EntityPlayer player = mock(EntityPlayer.class);
        when(world.getTileEntity(7, 8, 9)).thenReturn(null);
        when(player.isSneaking()).thenReturn(false);

        // Still reports the click as handled, which is what the client sees too.
        assertTrue(block.onBlockActivated(world, 7, 8, 9, player, 1, 0.5F, 0.5F, 0.5F));

        verify(player, never()).openGui(any(), anyInt(), any(World.class), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void aWrenchedPanelKeepsItsChargeInTheDroppedItem() {
        World world = mock(World.class);
        TestFixture.setInstanceField(world, World.class, "provider", mock(net.minecraft.world.WorldProvider.class));
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        panel.setEnergyStored(1234);
        when(world.getTileEntity(1, 2, 3)).thenReturn(panel);
        org.mockito.ArgumentCaptor<net.minecraft.entity.item.EntityItem> captor = org.mockito.ArgumentCaptor
            .forClass(net.minecraft.entity.item.EntityItem.class);

        dismantle(world, 1, 2, 3);

        verify(world).spawnEntityInWorld(captor.capture());
        ItemStack dropped = captor.getValue()
            .getEntityItem();
        assertNotNull(dropped);
        assertEquals(
            1234,
            dropped.getTagCompound()
                .getInteger(NBTConstants.ENERGY));
        verify(world).setBlockToAir(1, 2, 3);
    }

    @Test
    public void aPanelWithoutChargeIsDroppedWithoutANbtTag() {
        World world = mock(World.class);
        TestFixture.setInstanceField(world, World.class, "provider", mock(net.minecraft.world.WorldProvider.class));
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        when(world.getTileEntity(1, 2, 3)).thenReturn(panel);
        org.mockito.ArgumentCaptor<net.minecraft.entity.item.EntityItem> captor = org.mockito.ArgumentCaptor
            .forClass(net.minecraft.entity.item.EntityItem.class);

        dismantle(world, 1, 2, 3);

        verify(world).spawnEntityInWorld(captor.capture());
        assertNull(
            captor.getValue()
                .getEntityItem()
                .getTagCompound());
    }

    @Test
    public void dismantlingKeepsTheBufferedFurnaceEnergy() {
        World world = mock(World.class);
        TestFixture.setInstanceField(world, World.class, "provider", mock(net.minecraft.world.WorldProvider.class));
        SolarPanelTileEntity panel = new SolarPanelTileEntity(0);
        panel.setEnergyStored(100);
        panel.setFurnaceEnergyBuffer(16);
        when(world.getTileEntity(1, 2, 3)).thenReturn(panel);
        org.mockito.ArgumentCaptor<net.minecraft.entity.item.EntityItem> captor = org.mockito.ArgumentCaptor
            .forClass(net.minecraft.entity.item.EntityItem.class);

        dismantle(world, 1, 2, 3);

        verify(world).spawnEntityInWorld(captor.capture());
        assertEquals(
            116,
            captor.getValue()
                .getEntityItem()
                .getTagCompound()
                .getInteger(NBTConstants.ENERGY));
        assertEquals(0, panel.getFurnaceEnergyBuffer());
    }

    /**
     * {@code dismantleBlock} is private; the wrench path that calls it also goes through
     * {@code Utils.hasUsableWrench}, whose {@code ModAPIManager.hasAPI} needs a live Forge loader. Invoking the private
     * method keeps the drop logic itself under test without changing production visibility for a test.
     */
    private void dismantle(World pWorld, int pX, int pY, int pZ) {
        try {
            java.lang.reflect.Method method = SolarPanelBlock.class
                .getDeclaredMethod("dismantleBlock", World.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(block, pWorld, pX, pY, pZ);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot invoke dismantleBlock", e);
        }
    }
}
