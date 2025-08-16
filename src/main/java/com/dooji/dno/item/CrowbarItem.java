package com.dooji.dno.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class CrowbarItem extends Item {
    public CrowbarItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.dno.crowbar");
    }
}
