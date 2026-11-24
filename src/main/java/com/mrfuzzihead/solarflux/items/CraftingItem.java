package com.mrfuzzihead.solarflux.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.mrfuzzihead.solarflux.utility.Lang;

public class CraftingItem extends SFItem {

    public CraftingItem(String pName) {
        super(pName);
    }

    @Override
    public void addInformation(ItemStack pItemStack, EntityPlayer pEntityPlayer, List pList, boolean pBoolean) {
        super.addInformation(pItemStack, pEntityPlayer, pList, pBoolean);
        pList.add(Lang.localise("crafting.component"));
    }
}
