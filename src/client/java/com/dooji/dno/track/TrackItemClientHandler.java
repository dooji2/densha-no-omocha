package com.dooji.dno.track;

import com.dooji.dno.block.entity.TrackNodeBlockEntity;
import com.dooji.dno.network.payloads.PlaceTrackSegmentPayload;
import com.dooji.dno.registry.TrainModBlocks;
import com.dooji.dno.registry.TrainModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class TrackItemClientHandler {
    private static BlockPos selectedStartNodePosition = null;
    private static long lastInteractionMillis = 0;
    private static final long interactionCooldownMillis = 500;

    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.getStackInHand(hand).getItem() != TrainModItems.TRACK_ITEM) return ActionResult.PASS;
            long now = System.currentTimeMillis();
            if (now - lastInteractionMillis < interactionCooldownMillis) return ActionResult.PASS;
            lastInteractionMillis = now;

            BlockPos targetPosition = hitResult.getBlockPos();
            if (!isTrackNode(world, targetPosition)) {
                player.sendMessage(Text.translatable("message.dno.track_nodes_only"), true);
                return ActionResult.PASS;
            }

            if (selectedStartNodePosition == null) {
                selectedStartNodePosition = targetPosition;
                player.sendMessage(Text.translatable("message.dno.track_start_selected", targetPosition.getX(), targetPosition.getY(), targetPosition.getZ()), true);
                return ActionResult.SUCCESS;
            }

            if (selectedStartNodePosition.equals(targetPosition)) {
                selectedStartNodePosition = null;
                player.sendMessage(Text.translatable("message.dno.track_cancelled"), true);
                return ActionResult.SUCCESS;
            }

            Direction startNodeDirection = getNodeDirection(world, selectedStartNodePosition);
            Direction endNodeDirection = getNodeDirection(world, targetPosition);
            if (startNodeDirection == null || endNodeDirection == null) {
                player.sendMessage(Text.translatable("message.dno.track_direction_error"), true);
                selectedStartNodePosition = null;
                return ActionResult.SUCCESS;
            }

            BlockPos startPosition = selectedStartNodePosition;
            BlockPos endPosition = targetPosition;
            Direction startDirection = startNodeDirection;
            Direction endDirection = endNodeDirection;

            PlaceTrackSegmentPayload payload = new PlaceTrackSegmentPayload(startPosition, endPosition, startDirection, endDirection);
            ClientPlayNetworking.send(payload);

            player.sendMessage(Text.translatable(
                "message.dno.track_connected",
                selectedStartNodePosition.getX(), selectedStartNodePosition.getY(), selectedStartNodePosition.getZ(),
                targetPosition.getX(), targetPosition.getY(), targetPosition.getZ()
            ), true);

            selectedStartNodePosition = null;
            return ActionResult.SUCCESS;
        });
    }

    private static boolean isTrackNode(World world, BlockPos position) {
        return world.getBlockState(position).getBlock() == TrainModBlocks.TRACK_NODE_BLOCK;
    }

    private static Direction getNodeDirection(World world, BlockPos position) {
        if (world.getBlockEntity(position) instanceof TrackNodeBlockEntity entity) return entity.getDirection();
        return null;
    }
}
