package com.dooji.dno.block.entity;

import com.dooji.dno.registry.TrainModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class TrackNodeBlockEntity extends BlockEntity {
    public TrackNodeBlockEntity(BlockPos pos, BlockState state) {
        super(TrainModBlockEntities.TRACK_NODE_BLOCK_ENTITY, pos, state);
    }

    public Direction getDirection() {
        return getCachedState().get(Properties.HORIZONTAL_FACING);
    }

    public void setDirection(Direction direction) {
        BlockState newState = getCachedState().with(Properties.HORIZONTAL_FACING, direction);
        getWorld().setBlockState(getPos(), newState);
    }
}
