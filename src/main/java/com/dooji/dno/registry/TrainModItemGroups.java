package com.dooji.dno.registry;

import com.dooji.dno.TrainMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TrainModItemGroups {
    public static final RegistryKey<ItemGroup> DNO = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(TrainMod.MOD_ID, "densha_no_omocha"));
    private static ItemGroup DNO_GROUP;

    public static ItemGroup register(String name, ItemGroup.Builder builder) {
        RegistryKey<ItemGroup> groupKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(TrainMod.MOD_ID, name));
        ItemGroup group = builder.build();
        Registry.register(Registries.ITEM_GROUP, groupKey, group);
        return group;
    }

    public static void initialize() {
        DNO_GROUP = register(TrainMod.MOD_ID, FabricItemGroup.builder()
            .icon(() -> new ItemStack(TrainModItems.TRACK_ITEM))
            .displayName(Text.translatable("itemGroup.dno.trainMod"))
            .entries((displayContext, entries) -> {
                entries.add(TrainModItems.TRACK_ITEM);
                entries.add(TrainModItems.TRACK_NODE);
                entries.add(TrainModItems.CROWBAR);
            }));
    }

    public static ItemGroup getToyTrainsGroup() {
        return DNO_GROUP;
    }
}
