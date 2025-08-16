package com.dooji.dno.registry;

import com.dooji.dno.TrainMod;
import com.dooji.dno.block.entity.TrackNodeBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class TrainModBlockEntities {
    public static BlockEntityType<TrackNodeBlockEntity> TRACK_NODE_BLOCK_ENTITY;

    public static void initialize() {
        TRACK_NODE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(TrainMod.MOD_ID, "track_node"),
            FabricBlockEntityTypeBuilder.<TrackNodeBlockEntity>create((pos, state) -> new TrackNodeBlockEntity(pos, state), TrainModBlocks.TRACK_NODE_BLOCK).build()
        );
    }
}
