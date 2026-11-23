package com.mrfuzzihead.solarflux.core.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

import com.mrfuzzihead.solarflux.reference.NBTConstants;
import com.mrfuzzihead.solarflux.support.TestFixture;

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
    public void decrementingByZeroLeavesTheStackAlone() {
        BaseInventory inventory = new BaseInventory("Test", 1);
        inventory.setInventorySlotContents(0, TestFixture.stack(10, 0));

        assertNull(inventory.decrStackSize(0, 0));
        assertNull(inventory.decrStackSize(0, -3));
        assertNotNull(inventory.getStackInSlot(0));
        assertEquals(10, inventory.getStackInSlot(0).stackSize);
    }

    @Test
    public void randomOperationsKeepEverySlotWithinItsLimits() {
        java.util.Random random = new java.util.Random(42);
        for (int size = 1; size <= 8; ++size) {
            BaseInventory inventory = new BaseInventory("Test", size);
            for (int operation = 0; operation < 200; ++operation) {
                int slot = random.nextInt(size);
                switch (random.nextInt(3)) {
                    case 0:
                        inventory.setInventorySlotContents(
                            slot,
                            random.nextBoolean() ? TestFixture.stack(random.nextInt(64) + 1, 0) : null);
                        break;
                    case 1:
                        inventory.decrStackSize(slot, random.nextInt(70));
                        break;
                    default:
                        if (inventory.getStackInSlot(slot) != null) {
                            inventory.getStackInSlotOnClosing(slot);
                        }
                        break;
                }
                for (int i = 0; i < size; ++i) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (stack != null) {
                        assertTrue(stack.stackSize > 0);
                        assertTrue(stack.stackSize <= inventory.getInventoryStackLimit());
                    }
                }
            }
        }
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

    @Test
    public void readFromNbtIgnoresSlotsBeyondTheInventorySize() {
        // A save from an inventory with more slots (or a corrupted tag) must not crash the load. The stack is dropped,
        // which is the only sane option once its slot no longer exists.
        BaseInventory written = new BaseInventory("Test", 4);
        written.setInventorySlotContents(3, TestFixture.stack(1, 0));
        NBTTagCompound tag = new NBTTagCompound();
        written.writeToNBT(tag);

        BaseInventory restored = new BaseInventory("Test", 2);

        restored.readFromNBT(tag);

        assertTrue(restored.isEmpty());
        assertEquals(2, restored.getSizeInventory());
    }

    @Test
    public void readFromNbtIgnoresNegativeSlotIndexes() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setByte(NBTConstants.SLOT, (byte) -1);
        items.appendTag(entry);
        tag.setTag(NBTConstants.ITEMS, items);

        BaseInventory inventory = new BaseInventory("Test", 2);

        inventory.readFromNBT(tag);

        assertTrue(inventory.isEmpty());
    }

    @Test
    public void changingASlotNotifiesTheOwner() {
        IInventory owner = mock(IInventory.class);
        BaseInventory inventory = new BaseInventory("Test", 2, owner);

        inventory.setInventorySlotContents(0, TestFixture.stack(1, 0));

        verify(owner).markDirty();
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
