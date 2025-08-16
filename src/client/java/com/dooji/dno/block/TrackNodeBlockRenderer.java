package com.dooji.dno.block;

import com.dooji.dno.block.entity.TrackNodeBlockEntity;
import com.dooji.dno.registry.TrainModBlockEntities;
import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.registry.TrainModItems;

import com.dooji.dno.track.TrackSegment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;

public class TrackNodeBlockRenderer implements BlockEntityRenderer<TrackNodeBlockEntity> {
    public TrackNodeBlockRenderer(BlockEntityRendererFactory.Context context) {}

    @Override
    public void render(TrackNodeBlockEntity blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d offset) {
        World world = blockEntity.getWorld();
        if (world == null) return;

        BlockPos position = blockEntity.getPos();
        boolean hasTracks = hasAnyTrackAt(world, position);
        boolean isHoldingWrench = isHoldingWrench();

        if (!hasTracks || isHoldingWrench) {
            matrices.push();
            matrices.translate(offset.x, offset.y + 1.5, offset.z);
            
            BlockState state = blockEntity.getCachedState();
            BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
            blockRenderManager.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
            
            matrices.pop();
        }
    }

    public static void init() {
        BlockEntityRendererFactories.register(TrainModBlockEntities.TRACK_NODE_BLOCK_ENTITY, TrackNodeBlockRenderer::new);
    }

    private static boolean hasAnyTrackAt(World world, BlockPos position) {
        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(world);
        if (tracks.isEmpty()) return false;

        for (TrackSegment segment : tracks.values()) {
            if (segment.contains(position)) return true;
        }

        return false;
    }

    private static boolean isHoldingWrench() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        
        return player.getMainHandStack().getItem() == TrainModItems.CROWBAR || player.getOffHandStack().getItem() == TrainModItems.CROWBAR;
    }
}
