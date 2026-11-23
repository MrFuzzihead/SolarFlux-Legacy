package com.nauktis.solarflux.core.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.nauktis.solarflux.reference.NBTConstants;
import com.nauktis.solarflux.support.TestFixture;

public class BaseInventoryTest {

    @Test
    public void aNewInventoryIsEmptyAndHasTheRequestedSize() {
        BaseInventory inventory = new BaseInventory("Test", 5);

        assertEquals(5, inventory.getSizeInventory());
        assertEquals(0, inventory.getOccupiedSlotsNumber());
        assertTrue(inventory.isEmpty());
        assertEquals("Test", inventory.getInventoryName());
        assertFalse(inventory.hasCustomInventoryName());
        assertEquals(64, inventory.getInventoryStackLimit());
    }

    @Test
    public void storingAStackOccupiesOneSlot() {
        BaseInventory inventory = new BaseInventory("Test", 3);

        inventory.setInventorySlotContents(1, TestFixture.stack(2, 0));

        assertEquals(1, inventory.getOccupiedSlotsNumber());
        assertFalse(inventory.isEmpty());
        assertEquals(2, inventory.getStackInSlot(1).stackSize);
        assertNull(inventory.getStackInSlot(0));
    }

    @Test
    public void storingNullClearsTheSlot() {
        BaseInventory inventory = new BaseInventory("Test", 2);
        inventory.setInventorySlotContents(0, TestFixture.stack(2, 0));

        inventory.setInventorySlotContents(0, null);

        assertNull(inventory.getStackInSlot(0));
        assertTrue(inventory.isEmpty());
    }

    @Test
    public void oversizedStacksAreClampedToTheInventoryLimit() {
        BaseInventory inventory = new BaseInventory("Test", 1);

        inventory.setInventorySlotContents(0, TestFixture.stack(100, 0));

        assertEquals(64, inventory.getStackInSlot(0).stackSize);
    }

    @Test
    public void decrStackSizeBelowTheStackSizeSplitsTheStack() {
        BaseInventory inventory = new BaseInventory("Test", 1);
        inventory.setInventorySlotContents(0, TestFixture.stack(10, 0));

        ItemStack removed = inventory.decrStackSize(0, 4);

        assertEquals(4, removed.stackSize);
        assertEquals(6, inventory.getStackInSlot(0).stackSize);
    }

    @Test
    public void decrStackSizeAtOrAboveTheStackSizeEmptiesTheSlot() {
        BaseInventory inventory = new BaseInventory("Test", 2);
        inventory.setInventorySlotContents(0, TestFixture.stack(3, 0));
        inventory.setInventorySlotContents(1, TestFixture.stack(3, 0));

        assertEquals(3, inventory.decrStackSize(0, 3).stackSize);
        assertNull(inventory.getStackInSlot(0));

        assertEquals(3, inventory.decrStackSize(1, 99).stackSize);
        assertNull(inventory.getStackInSlot(1));
    }

    @Test
    public void decrStackSizeOnAnEmptySlotReturnsNull() {
        BaseInventory inventory = new BaseInventory("Test", 1);

        assertNull(inventory.decrStackSize(0, 1));
    }

    @Test
    public void getStackInSlotOnClosingRemovesAndReturnsTheStack() {
        BaseInventory inventory = new BaseInventory("Test", 1);
        ItemStack stored = TestFixture.stack(2, 0);
        inventory.setInventorySlotContents(0, stored);

        ItemStack closing = inventory.getStackInSlotOnClosing(0);

        assertSame(stored, closing);
        assertNull(inventory.getStackInSlot(0));
    }

    @Test
    public void getStackInSlotOnClosingOnAnEmptySlotReturnsNull() {
        BaseInventory inventory = new BaseInventory("Test", 1);

        assertNull(inventory.getStackInSlotOnClosing(0));
    }

    @Test
    public void writeToNbtStoresOnlyTheOccupiedSlots() {
        BaseInventory inventory = new BaseInventory("Test", 3);
        inventory.setInventorySlotContents(2, TestFixture.stack(5, 1));
        NBTTagCompound tag = new NBTTagCompound();

        inventory.writeToNBT(tag);

        assertEquals(
            1,
            tag.getTagList(NBTConstants.ITEMS, 10)
                .tagCount());
    }

    @Test
    public void writeToNbtRemembersWhichSlotEachStackCameFrom() {
        BaseInventory inventory = new BaseInventory("Test", 4);
        inventory.setInventorySlotContents(0, TestFixture.stack(3, 2));
        inventory.setInventorySlotContents(3, TestFixture.stack(7, 0));
        NBTTagCompound tag = new NBTTagCompound();

        inventory.writeToNBT(tag);

        net.minecraft.nbt.NBTTagList items = tag.getTagList(NBTConstants.ITEMS, 10);
        assertEquals(2, items.tagCount());
        assertEquals(
            0,
            items.getCompoundTagAt(0)
                .getByte(NBTConstants.SLOT));
        assertEquals(
            3,
            items.getCompoundTagAt(1)
                .getByte(NBTConstants.SLOT));
    }

    @Test
    public void readingNbtDropsStacksWhoseItemCannotBeResolved() {
        // A unit test cannot register an item (FMLControlledNamespacedRegistry needs a LaunchClassLoader), so the
        // written id is -1 and Item.getItemById(-1) is null. In game the item is registered and the stack comes back;
        // the in-game test mod covers that path.
        BaseInventory inventory = new BaseInventory("Test", 4);
        inventory.setInventorySlotContents(0, TestFixture.stack(3, 2));
        NBTTagCompound tag = new NBTTagCompound();
        inventory.writeToNBT(tag);

        BaseInventory restored = new BaseInventory("Test", 4);
        restored.readFromNBT(tag);

        assertTrue(restored.isEmpty());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void readFromNbtDoesNotGuardAgainstSlotsBeyondTheInventorySize() {
        // Documented limitation, not desired behaviour: readFromNBT trusts the slot index stored in the NBT. It is
        // unreachable in game because the panel always rebuilds the same five slots, and the game test covers the
        // real path. Asserting it here keeps the behaviour visible instead of silently changing it.
        BaseInventory written = new BaseInventory("Test", 4);
        written.setInventorySlotContents(3, TestFixture.stack(1, 0));
        NBTTagCompound tag = new NBTTagCompound();
        written.writeToNBT(tag);

        new BaseInventory("Test", 2).readFromNBT(tag);
    }

    @Test
    public void anEmptyNbtTagRestoresAnEmptyInventory() {
        BaseInventory inventory = new BaseInventory("Test", 2);
        inventory.setInventorySlotContents(0, TestFixture.stack(3, 0));

        inventory.readFromNBT(new NBTTagCompound());

        assertTrue(inventory.isEmpty());
        assertEquals(2, inventory.getSizeInventory());
    }

    @Test
    public void everySlotValidatesAndIsUsable() {
        BaseInventory inventory = new BaseInventory("Test", 1);

        assertTrue(inventory.isItemValidForSlot(0, TestFixture.stack(1, 0)));
        assertTrue(inventory.isUseableByPlayer(null));

        // No-ops that must simply not throw.
        inventory.markDirty();
        inventory.openInventory();
        inventory.closeInventory();
    }
}
