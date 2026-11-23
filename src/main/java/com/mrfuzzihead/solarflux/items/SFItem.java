package com.mrfuzzihead.solarflux.items;

import com.mrfuzzihead.solarflux.core.item.BaseModItem;
import com.mrfuzzihead.solarflux.creativetab.ModCreativeTab;
import com.mrfuzzihead.solarflux.reference.Reference;

public class SFItem extends BaseModItem {

    public SFItem(String pName) {
        super(Reference.MOD_ID, pName);
        setCreativeTab(ModCreativeTab.MOD_TAB);
    }
}
