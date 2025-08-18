package com.dooji.dno.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.dooji.dno.track.TrackManager;
import com.dooji.dno.track.TrackSegment;
import com.dooji.dno.train.Train;
import com.dooji.dno.train.TrainManager;
import com.dooji.dno.network.payloads.BreakTrackSegmentPayload;
import com.dooji.dno.network.payloads.PlaceTrackSegmentPayload;
import com.dooji.dno.network.payloads.RefreshTrainPathPayload;
import com.dooji.dno.network.payloads.SyncTracksPayload;
import com.dooji.dno.network.payloads.UpdateTrackSegmentPayload;
import com.dooji.dno.network.payloads.UpdateTrainConfigPayload;
import com.dooji.dno.network.payloads.TrainSyncPayload;
import com.dooji.dno.network.payloads.RequestBoardingPayload;
import com.dooji.dno.network.payloads.RequestDisembarkPayload;
import com.dooji.dno.network.payloads.PlayerPositionUpdatePayload;
import com.dooji.dno.network.payloads.BoardingResponsePayload;
import com.dooji.dno.network.payloads.BoardingSyncPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
 
public class TrainModNetworking {
    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(SyncTracksPayload.ID, SyncTracksPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlaceTrackSegmentPayload.ID, PlaceTrackSegmentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BreakTrackSegmentPayload.ID, BreakTrackSegmentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateTrackSegmentPayload.ID, UpdateTrackSegmentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TrainSyncPayload.ID, TrainSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardingResponsePayload.ID, BoardingResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardingSyncPayload.ID, BoardingSyncPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(PlaceTrackSegmentPayload.ID, PlaceTrackSegmentPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BreakTrackSegmentPayload.ID, BreakTrackSegmentPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateTrackSegmentPayload.ID, UpdateTrackSegmentPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateTrainConfigPayload.ID, UpdateTrainConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RefreshTrainPathPayload.ID, RefreshTrainPathPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestBoardingPayload.ID, RequestBoardingPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestDisembarkPayload.ID, RequestDisembarkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlayerPositionUpdatePayload.ID, PlayerPositionUpdatePayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncTracksToPlayer(handler.getPlayer());
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> {
            syncTracksToPlayer(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            syncTracksToPlayer(newPlayer);
        });

        ServerPlayNetworking.registerGlobalReceiver(PlaceTrackSegmentPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos start = payload.start();
            BlockPos end = payload.end();

            if (!world.canEntityModifyAt(player, start) || !world.canEntityModifyAt(player, end)) {
                return;
            }

            TrackSegment segment = new TrackSegment(start, end, payload.startDirection(), payload.endDirection());
            TrackManager.addTrackSegment(player, world, segment);
        });

        ServerPlayNetworking.registerGlobalReceiver(BreakTrackSegmentPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos position = payload.position();

            if (!world.canEntityModifyAt(player, position)) {
                return;
            }

            TrackManager.breakTrackSegment(player, world, position);
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateTrackSegmentPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos start = payload.start();
            BlockPos end = payload.end();

            if (!world.canEntityModifyAt(player, start) || !world.canEntityModifyAt(player, end)) {
                return;
            }

            TrackManager.updateTrackSegment(world, start, end, payload.modelId(), payload.type(), payload.dwellTimeSeconds(), payload.slopeCurvature(), payload.maxSpeedKmh(), payload.stationName(), payload.stationId());
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateTrainConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();

            TrainManager.updateTrainConfiguration(world, payload.trainId(), payload.carriageIds(), payload.carriageLengths(), payload.bogieInsets(), payload.trackSegmentKey(), payload.boundingBoxWidths(), payload.boundingBoxLengths(), payload.boundingBoxHeights());
        });

        ServerPlayNetworking.registerGlobalReceiver(RefreshTrainPathPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();
            if (!world.canEntityModifyAt(player, payload.sidingStart()) || !world.canEntityModifyAt(player, payload.sidingEnd())) {
                return;
            }
            
            TrainManager.handleRefreshPathRequest(world, payload.trainId(), payload.sidingStart(), payload.sidingEnd());
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestBoardingPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();

            TrainManager.handleBoardingRequest(world, player, payload.trainId(), payload.carriageIndex(), payload.relativeX(), payload.relativeY(), payload.relativeZ());
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestDisembarkPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();

            TrainManager.handleDisembarkRequest(world, player, payload.trainId());
        });

        ServerPlayNetworking.registerGlobalReceiver(PlayerPositionUpdatePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getWorld();

            TrainManager.handlePlayerPositionUpdate(world, player, payload.trainId(), payload.carriageIndex(), payload.relativeX(), payload.relativeY(), payload.relativeZ());
        });
    }

    public static void syncTracksToPlayer(ServerPlayerEntity player) {
        var world = player.getWorld();

        Map<BlockPos, NbtCompound> tags = new HashMap<>();
        TrackManager.getTracksFor(world).forEach((key, segment) -> {
            tags.put(segment.start(), segment.toNbt());
        });

        if (!tags.isEmpty()) {
            ServerPlayNetworking.send(player, new SyncTracksPayload(tags));
        }

        syncTrainsToPlayer(player);
    }

    public static void syncTrainsToPlayer(ServerPlayerEntity player) {
        var world = player.getWorld();
        String dimensionKey = world.getRegistryKey().getValue().toString();

        Map<String, Train> trains = TrainManager.getTrainsFor(world);

        if (!trains.isEmpty()) {
            Map<String, TrainSyncPayload.TrainData> trainDataMap = new HashMap<>();
            for (Map.Entry<String, Train> entry : trains.entrySet()) {
                Train train = entry.getValue();
                TrainSyncPayload.TrainData trainData = new TrainSyncPayload.TrainData(
                    train.getTrainId(),
                    train.getCarriageIds(),
                    train.isReversed(),
                    train.getTrackSegmentKey(),
                    train.getSpeed(),
                    train.getMaxSpeed(),
                    train.getAccelerationConstant(),
                    train.getMovementState().name(),
                    train.isReturningToDepot(),
                    train.getDwellStartTime(),
                    train.getCurrentPlatformId(),
                    new ArrayList<>(train.getVisitedPlatforms()),
                    train.getCurrentPathDistance(),
                    train.getContinuousPathPoints(),
                    train.getPath(),
                    train.getCurrentPlatformIndex(),
                    train.getDoorValue(),
                    train.getDoorTarget(),
                    train.getTotalPathLength(),
                    train.isMovingForward(),
                    train.getCarriageLengths(),
                    train.getBogieInsets(),
                    train.getBoundingBoxWidths(),
                    train.getBoundingBoxLengths(),
                    train.getBoundingBoxHeights()
                );

                trainDataMap.put(entry.getKey(), trainData);
            }

            ServerPlayNetworking.send(player, new TrainSyncPayload(dimensionKey, trainDataMap));
        }
    }

    public static void broadcastTrainSync(ServerWorld world) {
        String dimensionKey = world.getRegistryKey().getValue().toString();
        Map<String, Train> trains = TrainManager.getTrainsFor(world);

        if (!trains.isEmpty()) {
            Map<String, TrainSyncPayload.TrainData> trainDataMap = new HashMap<>();
            for (Map.Entry<String, Train> entry : trains.entrySet()) {
                Train train = entry.getValue();
                TrainSyncPayload.TrainData trainData = new TrainSyncPayload.TrainData(
                    train.getTrainId(),
                    train.getCarriageIds(),
                    train.isReversed(),
                    train.getTrackSegmentKey(),
                    train.getSpeed(),
                    train.getMaxSpeed(),
                    train.getAccelerationConstant(),
                    train.getMovementState().name(),
                    train.isReturningToDepot(),
                    train.getDwellStartTime(),
                    train.getCurrentPlatformId(),
                    new ArrayList<>(train.getVisitedPlatforms()),
                    train.getCurrentPathDistance(),
                    train.getContinuousPathPoints(),
                    train.getPath(),
                    train.getCurrentPlatformIndex(),
                    train.getDoorValue(),
                    train.getDoorTarget(),
                    train.getTotalPathLength(),
                    train.isMovingForward(),
                    train.getCarriageLengths(),
                    train.getBogieInsets(),
                    train.getBoundingBoxWidths(),
                    train.getBoundingBoxLengths(),
                    train.getBoundingBoxHeights()
                );

                trainDataMap.put(entry.getKey(), trainData);
            }

            for (var player : world.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, new TrainSyncPayload(dimensionKey, trainDataMap));
            }
        }
    }

    public static void broadcastAdd(ServerWorld world, TrackSegment segment) {
        var payload = new PlaceTrackSegmentPayload(segment.start(), segment.end(), segment.startDirection(), segment.endDirection());

        for (ServerPlayerEntity p : world.getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void broadcastRemove(ServerWorld world, TrackSegment segment) {
        var payload1 = new BreakTrackSegmentPayload(segment.start());
        var payload2 = new BreakTrackSegmentPayload(segment.end());

        for (ServerPlayerEntity p : world.getPlayers()) {
            ServerPlayNetworking.send(p, payload1);
            ServerPlayNetworking.send(p, payload2);
        }
    }

    public static void broadcastUpdate(ServerWorld world, TrackSegment segment) {
        var payload = new UpdateTrackSegmentPayload(
            segment.start(),
            segment.end(),
            segment.getModelId(),
            segment.getType(),
            segment.getDwellTimeSeconds(),
            segment.getSlopeCurvature(),
            segment.getTrainId(),
            segment.getMaxSpeedKmh(),
            segment.getStationName(),
            segment.getStationId()
        );

        for (ServerPlayerEntity p : world.getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
