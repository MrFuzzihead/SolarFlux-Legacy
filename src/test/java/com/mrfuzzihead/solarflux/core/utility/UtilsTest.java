package com.mrfuzzihead.solarflux.core.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mrfuzzihead.solarflux.support.TestFixture;

import cofh.api.item.IToolHammer;
import cpw.mods.fml.common.ModAPIManager;

public class UtilsTest {

    @Before
    public void provideAnEmptyApiRegistry() {
        // ModAPIManager.apiContainers is only populated by the FML launch; without it hasAPI() throws. An empty map
        // is the truthful state of a headless test: no BuildCraft API is installed.
        if (TestFixture.getField(ModAPIManager.INSTANCE, "apiContainers") == null) {
            TestFixture.setInstanceField(
                ModAPIManager.INSTANCE,
                ModAPIManager.class,
                "apiContainers",
                new java.util.HashMap<>());
        }
    }

    /** A world that can be handed to {@code new EntityItem(...)}: the Entity constructor reads the dimension id. */
    private static World newItemDropWorld() {
        World world = mock(World.class);
        TestFixture.setInstanceField(world, World.class, "provider", mock(net.minecraft.world.WorldProvider.class));
        return world;
    }

    @Test
    public void isServerAndIsClientAreMutuallyExclusive() {
        World serverWorld = mock(World.class);
        serverWorld.isRemote = false;
        World clientWorld = mock(World.class);
        clientWorld.isRemote = true;

        assertTrue(Utils.isServer(serverWorld));
        assertFalse(Utils.isClient(serverWorld));
        assertTrue(Utils.isClient(clientWorld));
        assertFalse(Utils.isServer(clientWorld));
    }

    @Test
    public void twoNullStacksAreNotEqual() {
        assertFalse(Utils.itemStacksEqualIgnoreStackSize(null, null));
    }

    @Test
    public void aNullStackIsNeverEqualToAStack() {
        assertFalse(Utils.itemStacksEqualIgnoreStackSize(null, TestFixture.stack(1, 0)));
        assertFalse(Utils.itemStacksEqualIgnoreStackSize(TestFixture.stack(1, 0), null));
    }

    @Test
    public void theSameItemAndMetadataMatchRegardlessOfTheStackSize() {
        ItemStack one = TestFixture.stack(1, 0);
        ItemStack many = TestFixture.stack(64, 0);

        assertTrue(Utils.itemStacksEqualIgnoreStackSize(one, many));
        assertTrue(Utils.itemStacksEqualIgnoreStackSize(many, one));
    }

    @Test
    public void differentMetadataIsADifferentItem() {
        assertFalse(Utils.itemStacksEqualIgnoreStackSize(TestFixture.stack(1, 0), TestFixture.stack(1, 1)));
    }

    @Test
    public void differentItemsAreDifferent() {
        assertFalse(
            Utils.itemStacksEqualIgnoreStackSize(
                TestFixture.stack(1, 0),
                TestFixture.stack(new net.minecraft.item.Item(), 1, 0)));
    }

    @Test
    public void matchingTagsMatch() {
        ItemStack first = TestFixture.stack(1, 0);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound()
            .setInteger("probe", 7);
        ItemStack second = TestFixture.stack(4, 0);
        second.setTagCompound(new NBTTagCompound());
        second.getTagCompound()
            .setInteger("probe", 7);

        assertTrue(Utils.itemStacksEqualIgnoreStackSize(first, second));
    }

    @Test
    public void differingTagsDoNotMatch() {
        ItemStack first = TestFixture.stack(1, 0);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound()
            .setInteger("probe", 7);
        ItemStack second = TestFixture.stack(1, 0);
        second.setTagCompound(new NBTTagCompound());
        second.getTagCompound()
            .setInteger("probe", 8);

        assertFalse(Utils.itemStacksEqualIgnoreStackSize(first, second));
    }

    @Test
    public void aTagOnOnlyOneStackDoesNotMatch() {
        ItemStack plain = TestFixture.stack(1, 0);
        ItemStack tagged = TestFixture.stack(1, 0);
        tagged.setTagCompound(new NBTTagCompound());

        assertFalse(Utils.itemStacksEqualIgnoreStackSize(plain, tagged));
        assertFalse(Utils.itemStacksEqualIgnoreStackSize(tagged, plain));
    }

    @Test
    public void spawnItemStackNeverLaunchesTheItemSidewaysOrForward() {
        World world = newItemDropWorld();
        ItemStack stack = TestFixture.stack(1, 0);

        Utils.spawnItemStack(world, 3, 64, -7, stack);

        ArgumentCaptor<EntityItem> captor = ArgumentCaptor.forClass(EntityItem.class);
        verify(world).spawnEntityInWorld(captor.capture());
        EntityItem item = captor.getValue();
        assertEquals(0.0D, item.motionX, 0.0D);
        assertEquals(0.0D, item.motionZ, 0.0D);
        assertEquals(3.5D, item.posX, 0.0D);
        assertEquals(64.5D, item.posY, 0.0D);
        assertEquals(-6.5D, item.posZ, 0.0D);
    }

    @Test
    public void aPlayerWithoutAnEquippedItemCannotWrench() {
        EntityPlayer player = mock(EntityPlayer.class);
        when(player.getCurrentEquippedItem()).thenReturn(null);

        assertFalse(Utils.hasUsableWrench(player, 0, 0, 0));
    }

    @Test
    public void aCoFHHammerCountsAsAWrench() {
        EntityPlayer player = mock(EntityPlayer.class);
        when(player.getCurrentEquippedItem()).thenReturn(new ItemStack(new TestHammer(), 1));

        assertTrue(Utils.hasUsableWrench(player, 3, 64, -7));
    }

    @Test
    public void aPlainItemIsNotAWrench() {
        EntityPlayer player = mock(EntityPlayer.class);
        when(player.getCurrentEquippedItem()).thenReturn(TestFixture.stack(1, 0));

        assertFalse(Utils.hasUsableWrench(player, 0, 0, 0));
    }

    /** The CoFH API interface, so the code path is the same one Thermal Expansion's hammer takes. */
    private static final class TestHammer extends Item implements IToolHammer {

        @Override
        public boolean isUsable(ItemStack pStack, EntityLivingBase pUser, int pX, int pY, int pZ) {
            return true;
        }

        @Override
        public void toolUsed(ItemStack pStack, EntityLivingBase pUser, int pX, int pY, int pZ) {}
    }

    @Test
    public void theSharedRandomSourceIsNeverNull() {
        assertNotNull(Utils.RANDOM);
    }
}
