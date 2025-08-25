package com.dooji.dno.track;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Consumer;

public class TrackManagerClient {
    private static final Map<String, Map<String, TrackSegment>> dimensionKeyToTracks = new ConcurrentHashMap<>();

    public static final Event<Consumer<TrackSegment>> ON_TRACK_ADD = EventFactory.createArrayBacked(Consumer.class, (listeners) -> (segment) -> {
        for (Consumer<TrackSegment> listener : listeners) {
            listener.accept(segment);
        }
    });

    public static final Event<Consumer<TrackSegment>> ON_TRACK_REMOVE = EventFactory.createArrayBacked(Consumer.class, (listeners) -> (segment) -> {
        for (Consumer<TrackSegment> listener : listeners) {
            listener.accept(segment);
        }
    });

    public static void addTrackSegment(World world, TrackSegment segment) {
        if (world == null || segment == null) return;
        String dimensionKey = getDimensionKey(world);

        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>());
        String trackKey = buildTrackKey(segment.start(), segment.end());

        worldTracks.put(trackKey, segment);
        ON_TRACK_ADD.invoker().accept(segment);
    }

    public static void removeTrackSegment(World world, BlockPos position) {
        if (world == null || position == null) return;
        String dimensionKey = getDimensionKey(world);

        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);
        if (worldTracks == null) return;
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, TrackSegment> entry : worldTracks.entrySet()) {
            TrackSegment segment = entry.getValue();
            if (segment.start().equals(position) || segment.end().equals(position)) keysToRemove.add(entry.getKey());
        }

        for (String key : keysToRemove) {
            TrackSegment removed = worldTracks.remove(key);
            if (removed != null) ON_TRACK_REMOVE.invoker().accept(removed);
        }
    }

    public static TrackSegment getTrackSegmentAt(BlockPos position) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) return null;
        for (TrackSegment segment : worldTracks.values()) if (segment.contains(position)) return segment;
        return null;
    }

    public static Map<String, TrackSegment> getTracksFor(World world) {
        String key = world.getRegistryKey().getValue().toString();
        return dimensionKeyToTracks.getOrDefault(key, Map.of());
    }

    public static void clearTracks(World world) {
        if (world == null) return;
        String dimensionKey = getDimensionKey(world);

        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.remove(dimensionKey);
        if (worldTracks != null) worldTracks.values().forEach(ON_TRACK_REMOVE.invoker()::accept);
    }

    public static void sync(Map<String, TrackSegment> tracks) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> currentTracks = dimensionKeyToTracks.computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>());

        currentTracks.values().forEach(ON_TRACK_REMOVE.invoker());
        currentTracks.clear();
        currentTracks.putAll(tracks);
        currentTracks.values().forEach(ON_TRACK_ADD.invoker());
    }

    public static void syncAdd(BlockPos pos, TrackSegment segment) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>());

        String trackKey = buildTrackKey(segment.start(), segment.end());
        worldTracks.put(trackKey, segment);
        ON_TRACK_ADD.invoker().accept(segment);
    }

    public static void syncRemove(BlockPos pos) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) return;
        String segmentKey = null;

        for (Map.Entry<String, TrackSegment> entry : worldTracks.entrySet()) {
            if (entry.getValue().contains(pos)) {
                segmentKey = entry.getKey();
                break;
            }
        }

        if (segmentKey != null) {
            TrackSegment removed = worldTracks.remove(segmentKey);
            if (removed != null) ON_TRACK_REMOVE.invoker().accept(removed);
        }
    }

    public static void syncUpdate(BlockPos start, BlockPos end, String modelId, String type, int dwellTimeSeconds, double slopeCurvature, String trainId, String routeId, int maxSpeedKmh, String stationName, String stationId, boolean openDoorsLeft, boolean openDoorsRight, double scaling) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks != null) {
            String trackKey = buildTrackKey(start, end);
            TrackSegment existingSegment = worldTracks.get(trackKey);
            if (existingSegment == null) {
                trackKey = buildTrackKey(end, start);
                existingSegment = worldTracks.get(trackKey);
            }

            if (existingSegment != null) {
                TrackSegment updatedSegment = new TrackSegment(
                    existingSegment.start(),
                    existingSegment.end(),
                    existingSegment.startDirection(),
                    existingSegment.endDirection(),
                    modelId,
                    type
                );

                updatedSegment.setDwellTimeSeconds(dwellTimeSeconds);
                updatedSegment.setSlopeCurvature(slopeCurvature);
                updatedSegment.setRouteId(routeId);
                updatedSegment.setMaxSpeedKmh(maxSpeedKmh);
                updatedSegment.setStationName(stationName);
                updatedSegment.setStationId(stationId);
                updatedSegment.setOpenDoorsLeft(openDoorsLeft);
                updatedSegment.setOpenDoorsRight(openDoorsRight);
                updatedSegment.setScaling(scaling);
                if (trainId != null) {
                    updatedSegment.setTrainId(trainId);
                }

                worldTracks.put(trackKey, updatedSegment);
                ON_TRACK_REMOVE.invoker().accept(existingSegment);
                ON_TRACK_ADD.invoker().accept(updatedSegment);
            }
        }
    }

    public static void removeAll() {
        dimensionKeyToTracks.values().forEach(map -> map.values().forEach(ON_TRACK_REMOVE.invoker()::accept));
        dimensionKeyToTracks.clear();
    }

    private static String getDimensionKey(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static String buildTrackKey(BlockPos start, BlockPos end) {
        return start.getX() + "," + start.getY() + "," + start.getZ() + "->" + end.getX() + "," + end.getY() + "," + end.getZ();
    }
}
