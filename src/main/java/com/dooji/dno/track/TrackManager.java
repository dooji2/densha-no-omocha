package com.dooji.dno.track;

import com.dooji.dno.block.entity.TrackNodeBlockEntity;
import com.dooji.dno.TrainMod;
import com.dooji.dno.network.TrainModNetworking;
import com.dooji.dno.train.Train;
import com.dooji.dno.train.TrainManager;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TrackManager {
    private static final Map<String, Map<String, TrackSegment>> dimensionKeyToTracks = new ConcurrentHashMap<>();
    private static final Map<String, Map<BlockPos, TrackNodeInfo>> dimensionKeyToNodes = new ConcurrentHashMap<>();

    public static void addTrackSegment(ServerPlayerEntity player, ServerWorld world, TrackSegment segment) {
        if (world == null || segment == null) {
            return;
        }

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>());

        String trackKey = buildTrackKey(segment.start(), segment.end());
        worldTracks.put(trackKey, segment);

        if (world.getBlockEntity(segment.start()) instanceof TrackNodeBlockEntity startEntity) {
            startEntity.setDirection(segment.startDirection());
        }
        
        if (world.getBlockEntity(segment.end()) instanceof TrackNodeBlockEntity endEntity) {
            endEntity.setDirection(segment.endDirection());
        }

        registerNode(world, segment.start(), segment.startDirection());
        registerNode(world, segment.end(), segment.endDirection());

        TrainModNetworking.broadcastAdd(world, segment);
        TrackPersistenceHandler.saveTracks(world, worldTracks);
    }

    public static boolean removeTrackSegment(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        try {
            List<String> keysToRemove = new ArrayList<>();
            List<TrackSegment> segmentsToRemove = new ArrayList<>();

            for (Map.Entry<String, TrackSegment> entry : worldTracks.entrySet()) {
                TrackSegment segment = entry.getValue();
                if (segment.start().equals(pos) || segment.end().equals(pos)) {
                    keysToRemove.add(entry.getKey());
                    segmentsToRemove.add(segment);
                }
            }

            for (TrackSegment segment : segmentsToRemove) {
                if (segment.isSiding()) {
                    String sidingKey = buildTrackKey(segment.start(), segment.end());
                    TrainManager.handleSidingRemoved(world, sidingKey);
                }
            }

            for (String key : keysToRemove) {
                worldTracks.remove(key);
            }

            if (!world.isClient() && world instanceof ServerWorld && !keysToRemove.isEmpty()) {
                TrackPersistenceHandler.saveTracks(world, worldTracks);
            }

            for (TrackSegment removed : segmentsToRemove) {
                removeNodeIfIsolated(world, removed.start());
                removeNodeIfIsolated(world, removed.end());
            }

            return !keysToRemove.isEmpty();
        } catch (Exception e) {
            TrainMod.LOGGER.error("Failed to remove track segment", e);
        }

        return false;
    }

    public static void breakTrackSegment(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) {
            return;
        }

        TrackSegment segmentToBreak = null;
        String segmentKey = null;

        for (Map.Entry<String, TrackSegment> entry : worldTracks.entrySet()) {
            TrackSegment segment = entry.getValue();
            if (segment.contains(pos)) {
                segmentToBreak = segment;
                segmentKey = entry.getKey();
                break;
            }
        }

        if (segmentToBreak != null) {
            if (segmentToBreak.isSiding()) {
                String sidingKey = buildTrackKey(segmentToBreak.start(), segmentToBreak.end());
                TrainManager.handleSidingRemoved(world, sidingKey);
            }

            worldTracks.remove(segmentKey);
            TrainModNetworking.broadcastRemove(world, segmentToBreak);
            TrackPersistenceHandler.saveTracks(world, worldTracks);
        }
    }

    public static boolean hasTrackSegment(World world, BlockPos pos) {
        return getTrackSegment(world, pos) != null;
    }

    public static TrackSegment getTrackSegment(World world, BlockPos pos) {
        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks != null) {
            for (TrackSegment segment : worldTracks.values()) {
                if (segment.start().equals(pos) || segment.end().equals(pos)) {
                    return segment;
                }
            }
        }

        return null;
    }

    public static Map<String, TrackSegment> getTracksFor(World world) {
        String key = world.getRegistryKey().getValue().toString();
        return dimensionKeyToTracks.getOrDefault(key, Map.of());
    }

    public static Map<BlockPos, TrackNodeInfo> getNodesFor(World world) {
        String key = world.getRegistryKey().getValue().toString();
        return dimensionKeyToNodes.getOrDefault(key, new ConcurrentHashMap<>());
    }

    public static void loadTracks(World world) {
        if (world.isClient() || !(world instanceof ServerWorld)) {
            return;
        }

        String dimensionKey = getDimensionKey(world);
        if (dimensionKey == null || dimensionKey.isEmpty()) {
            return;
        }

        Map<String, TrackSegment> worldTracks = TrackPersistenceHandler.loadTracks(world);
        if (worldTracks != null && !worldTracks.isEmpty()) {
            dimensionKeyToTracks.put(dimensionKey, worldTracks);
        }
    }

    public static boolean hasNodeAt(World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof TrackNodeBlockEntity;
    }

    public static Set<TrackSegment> getConnectedSegments(World world, BlockPos pos) {
        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);
        Set<TrackSegment> connected = new java.util.HashSet<>();

        if (worldTracks != null) {
            for (TrackSegment segment : worldTracks.values()) {
                if (segment.start().equals(pos) || segment.end().equals(pos)) {
                    connected.add(segment);
                }
            }
        }

        return connected;
    }

    public static boolean canConnectTo(World world, BlockPos pos, Direction direction) {
        Set<TrackSegment> segments = getConnectedSegments(world, pos);
        
        for (TrackSegment segment : segments) {
            if (segment.end().equals(pos) && segment.endDirection() == direction.getOpposite()) {
                return true;
            }
            if (segment.start().equals(pos) && segment.startDirection() == direction.getOpposite()) {
                return true;
            }
        }

        return false;
    }

    public static void registerNode(World world, BlockPos pos, Direction facing) {
        String dimensionKey = getDimensionKey(world);
        Map<BlockPos, TrackNodeInfo> nodes = dimensionKeyToNodes.computeIfAbsent(dimensionKey, k -> new ConcurrentHashMap<>());
        TrackNodeInfo existing = nodes.get(pos);
        if (existing == null) {
            nodes.put(pos, new TrackNodeInfo(pos, facing));
        } else {
            existing.setFacing(facing);
        }
    }

    private static void removeNodeIfIsolated(World world, BlockPos pos) {
        String dimensionKey = getDimensionKey(world);
        Map<BlockPos, TrackNodeInfo> nodes = dimensionKeyToNodes.get(dimensionKey);
        if (nodes == null) return;

        Set<TrackSegment> connected = getConnectedSegments(world, pos);
        if (connected == null || connected.isEmpty()) {
            nodes.remove(pos);
        }
    }

    public static void setNodesForDimension(World world, Map<BlockPos, TrackNodeInfo> nodes) {
        if (world == null) return;
        String dimensionKey = getDimensionKey(world);

        if (dimensionKey == null || dimensionKey.isEmpty()) return;
        dimensionKeyToNodes.put(dimensionKey, new ConcurrentHashMap<>(nodes));
    }

    public static void updateTrackSegment(World world, BlockPos start, BlockPos end, String modelId, String type, int dwellTimeSeconds, double slopeCurvature, String routeId, int maxSpeedKmh, String stationName, String stationId, boolean openDoorsLeft, boolean openDoorsRight, double scaling) {
        if (world == null || start == null || end == null) {
            return;
        }

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) {
            return;
        }

        String trackKey = buildTrackKey(start, end);

        TrackSegment existingSegment = worldTracks.get(trackKey);
        if (existingSegment == null) {
            trackKey = buildTrackKey(end, start);
            existingSegment = worldTracks.get(trackKey);
        }

        if (existingSegment != null) {
            boolean wasSiding = existingSegment.isSiding();
            boolean isNowSiding = "siding".equals(type);

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
            
            if ("platform".equals(type)) {
                if (stationName == null || stationName.trim().isEmpty()) {
                    stationName = "Station";
                }
                if (stationId == null || stationId.trim().isEmpty()) {
                    if (existingSegment.getStationId() == null || existingSegment.getStationId().trim().isEmpty()) {
                        String newStationId = generateUniqueStationId(world);
                        updatedSegment.setStationId(newStationId);
                    } else {
                        updatedSegment.setStationId(existingSegment.getStationId());
                    }
                } else {
                    updatedSegment.setStationId(stationId);
                }
            }
            updatedSegment.setStationName(stationName);
            updatedSegment.setOpenDoorsLeft(openDoorsLeft);
            updatedSegment.setOpenDoorsRight(openDoorsRight);
            updatedSegment.setScaling(scaling);

            worldTracks.put(trackKey, updatedSegment);

            if (wasSiding || isNowSiding) {
                String sidingKey = trackKey;
                if (wasSiding && !isNowSiding) {
                    TrainManager.handleSidingRemoved(world, sidingKey);
                } else if (!wasSiding && isNowSiding) {
                    if (updatedSegment.getTrainId() != null) {
                        TrainManager.spawnTrainOnSiding(world, sidingKey, "densha-no-omocha:ice1");
                    }
                } else if (wasSiding && isNowSiding) {
                    String trainType = modelId != null ? modelId : "densha-no-omocha:ice1";
                    TrainManager.handleSidingChanged(world, sidingKey, trainType);
                }
            }

            if (world.getBlockEntity(start) instanceof TrackNodeBlockEntity startEntity) {
                startEntity.setDirection(updatedSegment.startDirection());
            }
            if (world.getBlockEntity(end) instanceof TrackNodeBlockEntity endEntity) {
                endEntity.setDirection(updatedSegment.endDirection());
            }

            if (!world.isClient() && world instanceof ServerWorld) {
                TrackPersistenceHandler.saveTracks(world, worldTracks);
                TrainModNetworking.broadcastUpdate((ServerWorld) world, updatedSegment);
                Map<String, Train> trains = TrainManager.getTrainsFor(world);
                
                for (Train train : trains.values()) {
                    TrainManager.buildContinuousPathForTrain(world, train);
                }

                TrainModNetworking.broadcastTrainSync((ServerWorld) world);
            }
        }
    }

    public static void updateTrackSegmentTrainId(World world, BlockPos start, BlockPos end, String trainId) {
        if (world == null || start == null || end == null) {
            return;
        }

        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) {
            return;
        }

        String trackKey = buildTrackKey(start, end);

        TrackSegment existingSegment = worldTracks.get(trackKey);
        if (existingSegment == null) {
            trackKey = buildTrackKey(end, start);
            existingSegment = worldTracks.get(trackKey);
        }

        if (existingSegment != null) {
            existingSegment.setTrainId(trainId);

            if (!world.isClient() && world instanceof ServerWorld serverWorld) {
                TrackPersistenceHandler.saveTracks(world, worldTracks);
                TrainModNetworking.broadcastUpdate(serverWorld, existingSegment);
            }
        }
    }

    public static void removeTracksConnectedToNode(ServerWorld world, BlockPos nodePos) {
        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) {
            return;
        }

        List<String> tracksToRemove = new ArrayList<>();
        for (Map.Entry<String, TrackSegment> entry : worldTracks.entrySet()) {
            TrackSegment segment = entry.getValue();
            if (segment.start().equals(nodePos) || segment.end().equals(nodePos)) {
                tracksToRemove.add(entry.getKey());
            }
        }

        for (String trackKey : tracksToRemove) {
            TrackSegment segment = worldTracks.remove(trackKey);
            if (segment != null) {
                if (segment.isSiding()) {
                    TrainManager.handleSidingRemoved(world, trackKey);
                }

                TrainModNetworking.broadcastRemove(world, segment);
            }
        }

        TrackPersistenceHandler.saveTracks(world, worldTracks);
    }

    public static void breakTrackSegmentAtNode(ServerWorld world, BlockPos nodePos) {
        String dimensionKey = getDimensionKey(world);
        Map<String, TrackSegment> worldTracks = dimensionKeyToTracks.get(dimensionKey);

        if (worldTracks == null) {
            return;
        }

        Set<TrackSegment> connectedSegments = getConnectedSegments(world, nodePos);
        
        if (connectedSegments.size() == 1) {
            TrackSegment segment = connectedSegments.iterator().next();
            String trackKey = buildTrackKey(segment.start(), segment.end());
            if (worldTracks.containsKey(trackKey)) {
                TrackSegment removedSegment = worldTracks.remove(trackKey);
                if (removedSegment.isSiding()) {
                    TrainManager.handleSidingRemoved(world, trackKey);
                }

                TrainModNetworking.broadcastRemove(world, segment);
            } else {
                trackKey = buildTrackKey(segment.end(), segment.start());
                if (worldTracks.containsKey(trackKey)) {
                    TrackSegment removedSegment = worldTracks.remove(trackKey);
                    if (removedSegment.isSiding()) {
                        TrainManager.handleSidingRemoved(world, trackKey);
                    }

                    TrainModNetworking.broadcastRemove(world, segment);
                }
            }
        } else if (connectedSegments.size() > 1) {
            TrackSegment segmentToBreak = findSegmentToBreak(world, nodePos, connectedSegments);
            if (segmentToBreak != null) {
                String trackKey = buildTrackKey(segmentToBreak.start(), segmentToBreak.end());
                if (worldTracks.containsKey(trackKey)) {
                    TrackSegment removedSegment = worldTracks.remove(trackKey);
                    if (removedSegment.isSiding()) {
                        TrainManager.handleSidingRemoved(world, trackKey);
                    }

                    TrainModNetworking.broadcastRemove(world, segmentToBreak);
                } else {
                    trackKey = buildTrackKey(segmentToBreak.end(), segmentToBreak.start());
                    if (worldTracks.containsKey(trackKey)) {
                        TrackSegment removedSegment = worldTracks.remove(trackKey);
                        if (removedSegment.isSiding()) {
                            TrainManager.handleSidingRemoved(world, trackKey);
                        }
                        
                        TrainModNetworking.broadcastRemove(world, segmentToBreak);
                    }
                }
            }
        }

        TrackPersistenceHandler.saveTracks(world, worldTracks);
    }

    private static TrackSegment findSegmentToBreak(World world, BlockPos nodePos, Set<TrackSegment> connectedSegments) {
        if (connectedSegments.isEmpty()) return null;
        
        TrackSegment bestSegment = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (TrackSegment segment : connectedSegments) {
            double score = calculateBreakScore(world, nodePos, segment);
            if (score > bestScore) {
                bestScore = score;
                bestSegment = segment;
            }
        }
        
        return bestSegment;
    }

    private static double calculateBreakScore(World world, BlockPos nodePos, TrackSegment segment) {
        BlockPos otherEnd = segment.start().equals(nodePos) ? segment.end() : segment.start();
        
        if (otherEnd == null) return Double.NEGATIVE_INFINITY;
        
        Set<TrackSegment> otherEndSegments = getConnectedSegments(world, otherEnd);
        
        if (otherEndSegments.size() <= 1) {
            return 10.0;
        }
        
        return 5.0;
    }

    public static void clearWorldData(World world) {
        if (world == null) {
            return;
        }

        String dimensionKey = getDimensionKey(world);
        dimensionKeyToTracks.remove(dimensionKey);
        dimensionKeyToNodes.remove(dimensionKey);
    }

    private static String getDimensionKey(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static String buildTrackKey(BlockPos start, BlockPos end) {
        return start.getX() + "," + start.getY() + "," + start.getZ() + "->" + end.getX() + "," + end.getY() + "," + end.getZ();
    }

    private static String generateUniqueStationId(World world) {
        int random = (int) (Math.random() * 1000000);
        return "station_" + random;
    }
}
