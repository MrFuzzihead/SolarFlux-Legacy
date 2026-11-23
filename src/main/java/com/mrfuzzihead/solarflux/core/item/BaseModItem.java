package com.mrfuzzihead.solarflux.core.item;

import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class BaseModItem extends Item {

    private final String mModId;

    public BaseModItem(String pModId, String pName) {
        mModId = Objects.requireNonNull(pModId)
            .toLowerCase();
        setUnlocalizedName(Objects.requireNonNull(pName));
    }

    public String getModId() {
        return mModId;
    }

    @Override
    public String getUnlocalizedName() {
        return String.format("item.%s:%s", getModId(), unwrapUnlocalizedName(super.getUnlocalizedName()));
    }

    @Override
    public String getUnlocalizedName(ItemStack pItemStack) {
        return getUnlocalizedName();
    }

    @Override
    protected String getIconString() {
        return unwrapUnlocalizedName(getUnlocalizedName());
    }

    protected String unwrapUnlocalizedName(String pUnlocalizedName) {
        return pUnlocalizedName.substring(pUnlocalizedName.indexOf(".") + 1);
    }
}
