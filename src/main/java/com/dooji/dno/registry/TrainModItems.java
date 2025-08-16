package com.dooji.dno.registry;

import com.dooji.dno.TrainMod;
import com.dooji.dno.item.TrackItem;
import com.dooji.dno.item.TrackNodeItem;
import com.dooji.dno.item.CrowbarItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class TrainModItems {
    public static TrackItem TRACK_ITEM;
    public static TrackNodeItem TRACK_NODE;
    public static CrowbarItem CROWBAR;

    public static Item register(String name, Function<Item.Settings, Item> itemFactory, Item.Settings settings) {
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TrainMod.MOD_ID, name));
        Item item = itemFactory.apply(settings.registryKey(itemKey));
        Registry.register(Registries.ITEM, itemKey, item);

        return item;
    }

    public static void initialize() {
        TRACK_ITEM = (TrackItem) register("track_item", TrackItem::new, new Item.Settings());
        TRACK_NODE = (TrackNodeItem) register("track_node", TrackNodeItem::new, new Item.Settings());
        CROWBAR = (CrowbarItem) register("crowbar", CrowbarItem::new, new Item.Settings());
    }
}
