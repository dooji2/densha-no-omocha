package com.dooji.dno.block;

import com.dooji.dno.block.entity.TrackNodeBlockEntity;
import com.dooji.dno.track.TrackManager;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class TrackNodeBlock extends BlockWithEntity {
    public TrackNodeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrackNodeBlockEntity(pos, state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(TrackNodeBlock::new);
    }

    @Override
    public void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        BlockState currentState = world.getBlockState(pos);
        if (!currentState.isOf(this)) {
            TrackManager.breakTrackSegmentAtNode(world, pos);
        }

        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        boolean hasTracks = world instanceof World && TrackManager.hasNodeAt((World) world, pos);

        if (hasTracks) {
            return VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.3, 1.0);
        } else {
            return super.getOutlineShape(state, world, pos, context);
        }
    }
}
