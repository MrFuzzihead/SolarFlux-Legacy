package com.mrfuzzihead.solarflux.creativetab;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.mrfuzzihead.solarflux.init.ModItems;
import com.mrfuzzihead.solarflux.reference.Reference;

public class ModCreativeTab {

    public static final CreativeTabs MOD_TAB = new CreativeTabs(Reference.MOD_ID.toLowerCase()) {

        @Override
        public Item getTabIconItem() {
            return ModItems.mSolarCell3;
        }
    };
}
