package com.dooji.dno.registry;

import com.dooji.dno.TrainMod;
import com.dooji.dno.block.TrackNodeBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class TrainModBlocks {
    public static Block TRACK_NODE_BLOCK;

    public static void initialize() {
        Identifier nodeId = Identifier.of(TrainMod.MOD_ID, "track_node");
        RegistryKey<Block> nodeKey = RegistryKey.of(RegistryKeys.BLOCK, nodeId);

        TRACK_NODE_BLOCK = Blocks.register(
            nodeKey,
            TrackNodeBlock::new,
            AbstractBlock.Settings
                .copy(Blocks.STONE)
                .nonOpaque()
                .strength(2.0f)
        );
    }
}
