package com.mrfuzzihead.solarflux.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.junit.Before;
import org.junit.Test;

import com.mrfuzzihead.solarflux.blocks.SolarPanelTileEntity;
import com.mrfuzzihead.solarflux.items.UpgradeItem;
import com.mrfuzzihead.solarflux.support.TestFixture;

/**
 * Headless tests for the shift-click paths of {@link ContainerSolarPanel}.
 *
 * <p>
 * {@code transferStackInSlot} does not touch the player, so the fixture can use a bare
 * {@link InventoryPlayer} instead of a full {@code EntityPlayer}. The first panel slot is container slot 0 and the
 * first player inventory row starts at container slot {@code SolarPanelTileEntity.INVENTORY_SIZE}, backed by
 * {@code InventoryPlayer.mainInventory[9]}.
 * </p>
 */
public class ContainerSolarPanelTest {

    private static final int FIRST_PLAYER_SLOT = SolarPanelTileEntity.INVENTORY_SIZE;

    private SolarPanelTileEntity mPanel;
    private InventoryPlayer mPlayerInventory;
    private ContainerSolarPanel mContainer;

    @Before
    public void setUp() {
        TestFixture.applyDefaultConfiguration();
        TestFixture.disableAutoBalance();
        TestFixture.installUpgradeItems();
        mPanel = new SolarPanelTileEntity(0);
        mPlayerInventory = new InventoryPlayer(null);
        mContainer = new ContainerSolarPanel(mPlayerInventory, mPanel);
    }

    private void fillAllUpgradeSlotsWithDifferentUpgrades() {
        for (int slot = 0; slot < SolarPanelTileEntity.INVENTORY_SIZE; ++slot) {
            mPanel.setInventorySlotContents(slot, new ItemStack(TestFixture.newUpgrade("upgrade" + slot, 8), 1));
        }
        mPanel.markDirty();
    }

    @Test
    public void theContainerExposesThePanelSlotsAndThePlayerInventory() {
        assertEquals(SolarPanelTileEntity.INVENTORY_SIZE + 27 + 9, mContainer.inventorySlots.size());
        for (int slot = 0; slot < SolarPanelTileEntity.INVENTORY_SIZE; ++slot) {
            assertTrue(mContainer.getSlot(slot) instanceof SlotUpgrade);
        }
    }

    /**
     * Regression: the upgrade-specific branch split the stack before trying to merge it and only put the remainder
     * back when the merge succeeded. A full panel (one of each upgrade type) made every merge fail, so the split part
     * was deleted.
     */
    @Test
    public void shiftClickingAnUpgradeIntoAFullPanelKeepsItInThePlayerInventory() {
        fillAllUpgradeSlotsWithDifferentUpgrades();
        UpgradeItem sixthUpgradeType = TestFixture.newUpgrade("upgradeSixth", 8);
        ItemStack stack = new ItemStack(sixthUpgradeType, 8);
        mPlayerInventory.mainInventory[9] = stack;

        assertNull(mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT));

        assertNotNull("the stack must not be deleted", mPlayerInventory.mainInventory[9]);
        assertEquals(8, mPlayerInventory.mainInventory[9].stackSize);
        assertEquals(sixthUpgradeType, mPlayerInventory.mainInventory[9].getItem());
        for (int slot = 0; slot < SolarPanelTileEntity.INVENTORY_SIZE; ++slot) {
            assertNotNull("slot " + slot + " must keep its upgrade", mPanel.getStackInSlot(slot));
        }
    }

    @Test
    public void shiftClickingAnUpgradeIntoAnEmptyPanelMovesIt() {
        mPlayerInventory.mainInventory[9] = new ItemStack(TestFixture.newUpgrade("upgradeEfficiency", 8), 8);

        mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT);

        assertNull(mPlayerInventory.mainInventory[9]);
        assertNotNull(mPanel.getStackInSlot(0));
        assertEquals(8, mPanel.getStackInSlot(0).stackSize);
    }

    @Test
    public void shiftClickingAnUpgradeThatOnlyPartlyFitsKeepsTheRemainder() {
        UpgradeItem upgrade = TestFixture.newUpgrade("upgradeEfficiency", 8);
        mPanel.setInventorySlotContents(0, new ItemStack(upgrade, 5));
        mPanel.markDirty();
        mPlayerInventory.mainInventory[9] = new ItemStack(upgrade, 8);

        mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT);

        assertEquals(8, mPanel.getStackInSlot(0).stackSize);
        assertNotNull(mPlayerInventory.mainInventory[9]);
        assertEquals(5, mPlayerInventory.mainInventory[9].stackSize);
    }

    @Test
    public void shiftClickingANonUpgradeLeavesItInThePlayerInventory() {
        ItemStack stack = TestFixture.stack(16, 0);
        mPlayerInventory.mainInventory[9] = stack;

        assertNull(mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT));

        assertNotNull(mPlayerInventory.mainInventory[9]);
        assertEquals(16, mPlayerInventory.mainInventory[9].stackSize);
    }

    @Test
    public void transferStackInSlotReportsAPartialUpgradeMove() {
        UpgradeItem upgrade = TestFixture.newUpgrade("upgradeEfficiency", 8);
        mPanel.setInventorySlotContents(0, new ItemStack(upgrade, 5));
        mPlayerInventory.mainInventory[9] = new ItemStack(upgrade, 8);

        assertNotNull(
            "a partial move still counts as a transfer",
            mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT));

        assertEquals(8, mPanel.getStackInSlot(0).stackSize);
        assertEquals(5, mPlayerInventory.mainInventory[9].stackSize);
    }

    @Test
    public void transferStackInSlotReportsAFullMove() {
        ItemStack stack = new ItemStack(TestFixture.newUpgrade("upgradeEfficiency", 8), 8);
        mPlayerInventory.mainInventory[9] = stack;

        assertNotNull(mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT));

        assertNull(mPlayerInventory.mainInventory[9]);
        assertEquals(8, mPanel.getStackInSlot(0).stackSize);
    }

    @Test
    public void mergeItemStackHonoursTheItemMaxStackSize() {
        // A tool-like upgrade: legal in the panel, but only one per slot.
        UpgradeItem singleStack = new UpgradeItem("upgradeSingle", 8, new java.util.ArrayList<String>()) {

            @Override
            public int getItemStackLimit() {
                return 1;
            }
        };
        mPlayerInventory.mainInventory[9] = new ItemStack(singleStack, 5);

        assertNotNull(mContainer.transferStackInSlot(null, FIRST_PLAYER_SLOT));

        assertEquals(1, mPanel.getStackInSlot(0).stackSize);
        assertEquals(1, mPanel.getStackInSlot(1).stackSize);
        assertNotNull(mPlayerInventory.mainInventory[9]);
        assertEquals(3, mPlayerInventory.mainInventory[9].stackSize);
    }

    @Test
    public void upgradeSlotsCannotBeDragFilled() {
        for (int slot = 0; slot < SolarPanelTileEntity.INVENTORY_SIZE; ++slot) {
            assertFalse("slot " + slot, mContainer.canDragIntoSlot(mContainer.getSlot(slot)));
        }
    }

    @Test
    public void everyPlayerSlotCanBeDragFilledIncludingTheHotbar() {
        for (int slot = FIRST_PLAYER_SLOT; slot < mContainer.inventorySlots.size(); ++slot) {
            assertTrue("slot " + slot, mContainer.canDragIntoSlot(mContainer.getSlot(slot)));
        }
        // The hotbar slots report inventory indexes 0..8; the first five used to be rejected because the check compared
        // that index with the upgrade-slot range.
        for (int hotbar = 0; hotbar < 9; ++hotbar) {
            assertTrue(
                "hotbar " + hotbar,
                mContainer.canDragIntoSlot(mContainer.getSlot(FIRST_PLAYER_SLOT + 27 + hotbar)));
        }
    }

    private static EntityPlayer playerWithCursor(ItemStack pCursor) {
        EntityPlayer player = mock(EntityPlayer.class);
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setItemStack(pCursor);
        TestFixture.setInstanceField(player, EntityPlayer.class, "inventory", inventory);
        return player;
    }

    /**
     * Regression: {@code slotClick} split the cursor stack, let vanilla place what the slot could take, and then
     * overwrote the cursor with the withheld part. Whatever vanilla could not place was deleted.
     */
    @Test
    public void slotClickKeepsThePartThatDoesNotFitInTheSlot() {
        UpgradeItem upgrade = TestFixture.newUpgrade("upgradeLarge", 100);
        mPanel.setInventorySlotContents(0, new ItemStack(upgrade, 50));
        EntityPlayer player = playerWithCursor(new ItemStack(upgrade, 64));

        mContainer.slotClick(0, 0, 0, player);

        assertEquals(64, mPanel.getStackInSlot(0).stackSize);
        ItemStack cursor = player.inventory.getItemStack();
        assertNotNull("the remainder must stay on the cursor", cursor);
        assertEquals(50, cursor.stackSize);
        assertEquals(114, mPanel.getStackInSlot(0).stackSize + cursor.stackSize);
    }

    @Test
    public void slotClickOnAFullSlotKeepsEveryItemOnTheCursor() {
        UpgradeItem upgrade = TestFixture.newUpgrade("upgradeLarge", 100);
        mPanel.setInventorySlotContents(0, new ItemStack(upgrade, 64));
        EntityPlayer player = playerWithCursor(new ItemStack(upgrade, 64));

        mContainer.slotClick(0, 0, 0, player);

        assertEquals(64, mPanel.getStackInSlot(0).stackSize);
        assertNotNull(player.inventory.getItemStack());
        assertEquals(64, player.inventory.getItemStack().stackSize);
    }
}
