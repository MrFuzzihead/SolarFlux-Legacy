package com.mrfuzzihead.solarflux.blocks;

import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.common.collect.Maps;
import com.mrfuzzihead.solarflux.items.UpgradeItem;

/**
 * Counts the upgrades installed in a panel, skipping the scan when the slots did not change.
 *
 * <p>
 * {@code markDirty()} fires for reasons unrelated to the inventory (saves, block updates) and a shift-click fires
 * it once per merged slot, so a plain scan on every call was wasted work. The slot snapshot is compared by stack
 * identity and size, which also catches the in-place growth done by {@code mergeItemStack}.
 * </p>
 */
public final class PanelUpgrades {

    private final IInventory mInventory;
    private final Map<Item, Integer> mCounts = Maps.newHashMap();
    private final ItemStack[] mLastStacks;
    private final int[] mLastSizes;
    private boolean mValid;

    public PanelUpgrades(IInventory pInventory) {
        mInventory = pInventory;
        int size = pInventory.getSizeInventory();
        mLastStacks = new ItemStack[size];
        mLastSizes = new int[size];
    }

    /**
     * @return true when the slots changed and the counts were rebuilt
     */
    public boolean refreshIfNeeded() {
        if (matches()) {
            return false;
        }
        remember();
        mCounts.clear();
        for (int i = 0; i < mInventory.getSizeInventory(); ++i) {
            ItemStack stack = mInventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof UpgradeItem) {
                Integer previous = mCounts.get(stack.getItem());
                mCounts.put(stack.getItem(), (previous == null ? 0 : previous) + stack.stackSize);
            }
        }
        return true;
    }

    public int getCount(Item pItem) {
        if (pItem == null) {
            return 0;
        }
        Integer count = mCounts.get(pItem);
        return count == null ? 0 : count;
    }

    public int getTotal() {
        int total = 0;
        for (int count : mCounts.values()) {
            total += count;
        }
        return total;
    }

    private boolean matches() {
        if (!mValid) {
            return false;
        }
        for (int i = 0; i < mLastStacks.length; ++i) {
            ItemStack stack = mInventory.getStackInSlot(i);
            if (stack != mLastStacks[i]) {
                return false;
            }
            if (stack != null && stack.stackSize != mLastSizes[i]) {
                return false;
            }
        }
        return true;
    }

    private void remember() {
        for (int i = 0; i < mLastStacks.length; ++i) {
            ItemStack stack = mInventory.getStackInSlot(i);
            mLastStacks[i] = stack;
            mLastSizes[i] = stack == null ? 0 : stack.stackSize;
        }
        mValid = true;
    }
}
