package com.dooji.dno.train;

import com.dooji.dno.TrainMod;
import com.dooji.dno.network.TrainModNetworking;
import com.dooji.dno.track.TrackManager;
import com.dooji.dno.track.TrackSegment;
import com.dooji.dno.track.Route;
import com.dooji.dno.track.RouteManager;
import com.dooji.dno.track.RoutePathfinder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import com.dooji.dno.network.payloads.BoardingSyncPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import com.dooji.dno.network.payloads.BoardingResponsePayload;

public class TrainManager {
    private static final Map<String, Map<String, Train>> dimensionKeyToTrains = new HashMap<>();
    private static final Set<String> pendingRefreshAtDepot = new HashSet<>();

    // I don't know why but I had a comment here saying I should remove this, but I don't know why, probably a 4 AM coding moment
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                updateTrainMovement(world);

                if (world.getTime() % 600 == 0) {
                    saveTrains(world);
                }
            }
        });
    }

    public static void updateTrainConfiguration(World world, String trainId, List<String> carriageIds, List<Double> carriageLengths, List<Double> bogieInsets, String trackSegmentKey, List<Double> boundingBoxWidths, List<Double> boundingBoxLengths, List<Double> boundingBoxHeights) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.computeIfAbsent(dimensionKey, k -> new HashMap<>());

        if (carriageIds == null || carriageIds.isEmpty()) {
            Train existingTrain = worldTrains.get(trainId);
            if (existingTrain != null) {
                worldTrains.remove(trainId);
                if (world instanceof ServerWorld serverWorld) {
                    saveTrains(serverWorld);
                    TrainModNetworking.broadcastTrainSync(serverWorld);
                }
            }

            return;
        }

        TrackSegment siding = getSidingByKey(world, trackSegmentKey);
        if (siding != null && siding.getTrainId() != null && !siding.getTrainId().equals(trainId)) {
            String oldTrainId = siding.getTrainId();
            Train oldTrain = worldTrains.get(oldTrainId);
            if (oldTrain != null) {
                worldTrains.remove(oldTrainId);
            }
        }

        Train train = worldTrains.get(trainId);
        if (train == null) {
            train = new Train(trainId, carriageIds, false, trackSegmentKey);
            worldTrains.put(trainId, train);
        } else {
            train.setCarriageIds(carriageIds);
            train.setTrackSegmentKey(trackSegmentKey);
        }

        if (carriageLengths != null) train.setCarriageLengths(carriageLengths);
        if (bogieInsets != null) train.setBogieInsets(bogieInsets);
        if (boundingBoxWidths != null) train.setBoundingBoxWidths(boundingBoxWidths);
        if (boundingBoxLengths != null) train.setBoundingBoxLengths(boundingBoxLengths);
        if (boundingBoxHeights != null) train.setBoundingBoxHeights(boundingBoxHeights);

        try {
            String[] parts = trackSegmentKey.split("->");
            if (parts.length == 2) {
                String[] startParts = parts[0].split(",");
                String[] endParts = parts[1].split(",");

                if (startParts.length == 3 && endParts.length == 3) {
                    BlockPos start = new BlockPos(
                        Integer.parseInt(startParts[0]),
                        Integer.parseInt(startParts[1]),
                        Integer.parseInt(startParts[2])
                    );
                    BlockPos end = new BlockPos(
                        Integer.parseInt(endParts[0]),
                        Integer.parseInt(endParts[1]),
                        Integer.parseInt(endParts[2])
                    );

                    TrackManager.updateTrackSegmentTrainId(world, start, end, trainId);
                }
            }
        } catch (Exception e) {
            TrainMod.LOGGER.error("Failed to update track segment train ID", e);
        }

        if (world instanceof ServerWorld serverWorld) {
            saveTrains(serverWorld);
            TrainModNetworking.broadcastTrainSync(serverWorld);
        }
    }

    public static void buildContinuousPathForTrain(World world, Train train) {
        if (world == null || train == null) return;

        TrackSegment sidingTrack = getSidingByKey(world, train.getTrackSegmentKey());
        if (sidingTrack == null) {
            return;
        }

        String routeId = sidingTrack.getRouteId();
        if (routeId == null || routeId.trim().isEmpty()) {
            return;
        }

        Route route = RouteManager.getRoute(routeId);
        if (route == null || route.getStationIds().isEmpty()) {
            return;
        }

        List<TrackSegment> pathSegments = RoutePathfinder.generatePathFromRoute(world, route, sidingTrack);
        if (pathSegments.isEmpty()) {
            return;
        }

        List<Vec3d> continuousPoints = RoutePathfinder.buildContinuousPoints(pathSegments);
        train.setContinuousPathPoints(continuousPoints);

        double totalPathLength = 0.0;
        if (continuousPoints.size() > 1) {
            for (int i = 0; i < continuousPoints.size() - 1; i++) {
                Vec3d current = continuousPoints.get(i);
                Vec3d next = continuousPoints.get(i + 1);
                totalPathLength += current.distanceTo(next);
            }
        }

        train.setTotalPathLength(totalPathLength);

        List<String> pathKeys = new ArrayList<>();
        for (TrackSegment seg : pathSegments) {
            pathKeys.add(seg.start().getX() + "," + seg.start().getY() + "," + seg.start().getZ() + "->" + seg.end().getX() + "," + seg.end().getY() + "," + seg.end().getZ());
        }

        train.setPath(pathKeys);

        if (!continuousPoints.isEmpty()) {
            if (train.getMovementState() == null || train.getMovementState() == Train.MovementState.SPAWNED) {
                double depotDistance = calculateDepotDistance(train, sidingTrack);
                train.setDepotPathDistance(depotDistance);

                if (train.getCurrentPathDistance() <= 1e-6) {
                    train.setCurrentPathDistance(depotDistance);
                }
                
                train.setCurrentPlatformIndex(0);
                train.setMovementState(Train.MovementState.SPAWNED);
                train.setSpeed(0.0);
                train.setDwellStartTime(System.currentTimeMillis());
                train.getVisitedPlatforms().clear();
                train.setReturningToDepot(false);
                train.setReversed(false);

                if (train.getPath() == null || train.getPath().isEmpty()) {
                    boolean forwardDir = pickInitialDirection(world, train);
                    train.setMovingForward(forwardDir);
                }
            }
        }
    }

    public static void handleRefreshPathRequest(ServerWorld world, String trainId, BlockPos sidingStart, BlockPos sidingEnd) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains == null) return;
        Train train = worldTrains.get(trainId);
        if (train == null) return;

        TrackSegment siding = TrackManager.getTrackSegment(world, sidingStart);
        if (siding == null) return;

        boolean atDepot = train.getMovementState() == Train.MovementState.DWELLING_AT_DEPOT || (train.getCurrentPathDistance() <= 1e-6);
        boolean returning = train.getMovementState() == Train.MovementState.RETURNING_TO_DEPOT;

        if (returning) {
            pendingRefreshAtDepot.add(trainId);
            saveTrains(world);
            TrainModNetworking.broadcastTrainSync(world);
            return;
        }

        String routeId = siding.getRouteId();
        if (routeId == null || routeId.trim().isEmpty()) {
            return;
        }

        Route route = RouteManager.getRoute(routeId);
        if (route == null || route.getStationIds().isEmpty()) {
            return;
        }

        List<TrackSegment> newSegments = RoutePathfinder.generatePathFromRoute(world, route, siding);
        if (newSegments.isEmpty()) {
            return;
        }

        List<String> newPathKeys = new ArrayList<>();
        for (TrackSegment seg : newSegments) {
            newPathKeys.add(seg.start().getX() + "," + seg.start().getY() + "," + seg.start().getZ() + "->" + seg.end().getX() + "," + seg.end().getY() + "," + seg.end().getZ());
        }

        List<String> oldPath = train.getPath();
        if (oldPath == null) oldPath = new ArrayList<>();

        boolean pathUnchanged = oldPath.equals(newPathKeys);
        if (pathUnchanged) {
            saveTrains(world);
            TrainModNetworking.broadcastTrainSync(world);
            return;
        }

        Set<String> newPathSet = new HashSet<>(newPathKeys);
        Set<String> removedUnvisited = new HashSet<>();
        String nextUnvisited = null;

        for (int i = 0; i < oldPath.size(); i++) {
            String key = oldPath.get(i);
            TrackSegment seg = getSidingByKey(world, key);
            boolean isPlatform = seg != null && seg.isPlatform() && "platform".equals(seg.getType());

            if (isPlatform) {
                boolean visited = train.getVisitedPlatforms().contains(key) || i < train.getCurrentPlatformIndex();
                if (!visited && nextUnvisited == null) nextUnvisited = key;
                if (!newPathSet.contains(key) && !visited) removedUnvisited.add(key);
            }
        }

        boolean nextRemoved = nextUnvisited != null && removedUnvisited.contains(nextUnvisited);

        if (atDepot) {
            buildContinuousPathForTrain(world, train);
        } else if (nextRemoved) {
            pendingRefreshAtDepot.add(trainId);
        } else if (!removedUnvisited.isEmpty()) {
            List<Vec3d> newPoints = RoutePathfinder.buildContinuousPoints(newSegments);
            List<Vec3d> existing = train.getContinuousPathPoints();

            if (existing != null && !existing.isEmpty()) {
                List<Vec3d> merged = new ArrayList<>(existing);
                if (!newPoints.isEmpty()) merged.addAll(newPoints);

                train.setContinuousPathPoints(merged);
                List<String> newPath = new ArrayList<>(train.getPath());
                newPath.addAll(newPathKeys);
                
                train.setPath(newPath);
                double totalLen = 0.0;
                List<Vec3d> pts = train.getContinuousPathPoints();

                for (int i = 0; i < pts.size() - 1; i++) totalLen += pts.get(i).distanceTo(pts.get(i + 1));
                train.setTotalPathLength(totalLen);
            } else {
                buildContinuousPathForTrain(world, train);
            }
        } else {
            pendingRefreshAtDepot.add(trainId);
        }

        saveTrains(world);
        TrainModNetworking.broadcastTrainSync(world);
    }

    public static void handleGeneratePathRequest(ServerWorld world, String trainId, String routeId, BlockPos sidingStart, BlockPos sidingEnd) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains == null) return;
        
        Train train = worldTrains.get(trainId);
        if (train == null) return;
        
        TrackSegment siding = TrackManager.getTrackSegment(world, sidingStart);
        if (siding == null) return;
        
        String routeIdFromSiding = siding.getRouteId();
        if (routeIdFromSiding == null || !routeIdFromSiding.equals(routeId)) {
            return;
        }
        
        Route route = RouteManager.getRoute(routeId);
        if (route == null || route.getStationIds().isEmpty()) {
            return;
        }
        
        List<TrackSegment> pathSegments = RoutePathfinder.generatePathFromRoute(world, route, siding);
        if (pathSegments.isEmpty()) {
            return;
        }
        
        List<String> pathKeys = new ArrayList<>();
        for (TrackSegment segment : pathSegments) {
            String trackKey = segment.start().getX() + "," + segment.start().getY() + "," + segment.start().getZ() + "->" + segment.end().getX() + "," + segment.end().getY() + "," + segment.end().getZ();
            pathKeys.add(trackKey);
        }
        
        train.setPath(pathKeys);
        
        List<Vec3d> continuousPoints = RoutePathfinder.buildContinuousPoints(pathSegments);
        train.setContinuousPathPoints(continuousPoints);
        
        double totalPathLength = 0.0;
        if (continuousPoints.size() > 1) {
            for (int i = 0; i < continuousPoints.size() - 1; i++) {
                Vec3d a = continuousPoints.get(i);
                Vec3d b = continuousPoints.get(i + 1);
                totalPathLength += a.distanceTo(b);
            }
        }
        
        train.setTotalPathLength(totalPathLength);
        
        saveTrains(world);
        TrainModNetworking.broadcastTrainSync(world);
    }

    public static void handleBoardingRequest(ServerWorld world, ServerPlayerEntity player, String trainId, int carriageIndex, double relativeX, double relativeY, double relativeZ) {  
        Train train = getTrainById(world, trainId);
        if (train == null) {
            sendBoardingResponse(world, player, trainId, carriageIndex, false);
            return;
        }
        
        if (train.getMovementState() != Train.MovementState.DWELLING_AT_PLATFORM) {
            sendBoardingResponse(world, player, trainId, carriageIndex, false);
            return;
        }

        if (train.isPlayerBoarded(player.getUuidAsString())) {
            sendBoardingResponse(world, player, trainId, carriageIndex, false);
            return;
        }

        train.addBoardedPlayer(player.getUuidAsString(), carriageIndex, relativeX, relativeY, relativeZ);
        sendBoardingResponse(world, player, trainId, carriageIndex, true);
        broadcastBoardingSync(world);
    }

    public static void handleDisembarkRequest(ServerWorld world, ServerPlayerEntity player, String trainId) {
        Train train = getTrainById(world, trainId);
        if (train == null) {
            return;
        }

        if (!train.isPlayerBoarded(player.getUuidAsString())) {
            return;
        }

        train.removeBoardedPlayer(player.getUuidAsString());
        broadcastBoardingSync(world);
    }

    public static void handlePlayerPositionUpdate(ServerWorld world, ServerPlayerEntity player, String trainId, int carriageIndex, double relativeX, double relativeY, double relativeZ) {
        Train train = getTrainById(world, trainId);
        if (train == null) {
            return;
        }

        if (!train.isPlayerBoarded(player.getUuidAsString())) {
            return;
        }

        train.addBoardedPlayer(player.getUuidAsString(), carriageIndex, relativeX, relativeY, relativeZ);
        broadcastBoardingSync(world);
    }

    private static boolean pickInitialDirection(World world, Train train) {
        boolean prev = train.isMovingForward();
        train.setMovingForward(true);
        double forward = train.getDistanceToNextPlatform(world);
        train.setMovingForward(false);
        double backward = train.getDistanceToNextPlatform(world);
        train.setMovingForward(prev);

        boolean hasForward = forward < Double.MAX_VALUE && forward > 0.0 && !Double.isInfinite(forward);
        boolean hasBackward = backward < Double.MAX_VALUE && backward > 0.0 && !Double.isInfinite(backward);

        if (hasForward && hasBackward) return forward <= backward;
        if (hasForward) return true;
        if (hasBackward) return false;

        double depot = train.getDepotPathDistance();
        double forwardLen = train.getTotalPathLength() - depot;
        double backwardLen = depot;
        return forwardLen >= backwardLen;
    }

    public static Map<String, Train> getTrainsFor(World world) {
        String dimensionKey = getDimensionKey(world);
        return dimensionKeyToTrains.getOrDefault(dimensionKey, new HashMap<>());
    }

    public static void clearAllTrains(ServerWorld world) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains != null) {
            worldTrains.clear();
        }
    }

    public static void despawnTrain(World world, String trainId) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains != null) {
            Train train = worldTrains.remove(trainId);
            if (train != null) {
                String trackSegmentKey = train.getTrackSegmentKey();
                if (trackSegmentKey != null) {
                    TrackSegment siding = getSidingByKey(world, trackSegmentKey);
                    if (siding != null) {
                        siding.setTrainId(null);
                    }
                }
            }
        }
    }

    public static void despawnTrainsOnSiding(World world, String sidingKey) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains != null) {
            List<String> trainsToRemove = new ArrayList<>();
            for (Map.Entry<String, Train> entry : worldTrains.entrySet()) {
                Train train = entry.getValue();
                if (sidingKey.equals(train.getTrackSegmentKey())) {
                    trainsToRemove.add(entry.getKey());
                }
            }

            for (String trainId : trainsToRemove) {
                despawnTrain(world, trainId);
            }
        }
    }

    public static Train getTrainById(World world, String trainId) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains != null) {
            return worldTrains.get(trainId);
        }
        return null;
    }

    public static void handleSidingRemoved(World world, String sidingKey) {
        despawnTrainsOnSiding(world, sidingKey);

        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);
        if (worldTrains != null) {
            cleanupOrphanedTrains(world, worldTrains);
        }
    }

    public static void handleSidingChanged(World world, String sidingKey, String newTrainType) {
        TrackSegment siding = getSidingByKey(world, sidingKey);
        if (siding == null) return;
        
        String existingTrainId = siding.getTrainId();
        
        if (existingTrainId != null) {
            Train existingTrain = getTrainById(world, existingTrainId);
            if (existingTrain != null) {
                TrainMod.LOGGER.info("Updated existing train {} configuration for siding {}", existingTrainId, sidingKey);
                return;
            }
        }

        if (newTrainType != null && !newTrainType.isEmpty()) {
            spawnTrainOnSiding(world, sidingKey, newTrainType);
        }
    }

    public static void spawnTrainOnSiding(World world, String sidingKey, String trainType) {
        TrackSegment siding = getSidingByKey(world, sidingKey);
        if (siding == null) {
            return;
        }

        if (siding.getTrainId() != null) {
            return;
        }

        String trainId = "train_" + System.currentTimeMillis();
        Train train = new Train(trainId);

        train.setTrackSegmentKey(sidingKey);

        siding.setTrainId(trainId);
        if (world instanceof ServerWorld serverWorld) {
            TrainModNetworking.broadcastUpdate(serverWorld, siding);
        }

        dimensionKeyToTrains.computeIfAbsent(getDimensionKey(world), k -> new HashMap<>()).put(trainId, train);

        buildContinuousPathForTrain(world, train);
        syncTrainToClient(world, train);
    }

    public static void saveTrains(ServerWorld world) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);

        if (worldTrains == null) {
            return;
        }

        try {
            File saveDir = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data").toFile();
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            File saveFile = saveDir.toPath().resolve("densha-no-omocha-trains-" + dimensionKey.replace(":", "_") + ".dat").toFile();

            NbtCompound rootTag = new NbtCompound();
            NbtList trainList = new NbtList();

            for (Train train : worldTrains.values()) {
                trainList.add(train.toNbt());
            }

            rootTag.put("trains", trainList);

            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                NbtIo.writeCompressed(rootTag, fos);
            }

            TrainMod.LOGGER.info("Saved {} trains for dimension {}", worldTrains.size(), dimensionKey);

        } catch (IOException e) {
            TrainMod.LOGGER.error("Failed to save trains", e);
        }
    }

    public static void loadTrains(ServerWorld world) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = new HashMap<>();

        try {
            File saveDir = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data").toFile();
            File saveFile = saveDir.toPath().resolve("densha-no-omocha-trains-" + dimensionKey.replace(":", "_") + ".dat").toFile();

            if (!saveFile.exists()) {
                TrainMod.LOGGER.info("No existing train save file found for dimension {}", dimensionKey);
                dimensionKeyToTrains.put(dimensionKey, worldTrains);
                return;
            }

            try (FileInputStream fis = new FileInputStream(saveFile)) {
                NbtCompound rootTag = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());

                if (rootTag.contains("trains")) {
                    NbtList trainList = rootTag.getListOrEmpty("trains");

                    for (int i = 0; i < trainList.size(); i++) {
                        NbtCompound trainTag = trainList.getCompound(i).orElse(new NbtCompound());
                        Train train = Train.fromNbt(trainTag);
                        worldTrains.put(train.getTrainId(), train);
                    }

                    TrainMod.LOGGER.info("Loaded {} trains for dimension {}", worldTrains.size(), dimensionKey);
                }
            }
        } catch (IOException e) {
            TrainMod.LOGGER.error("Failed to load trains", e);
        }

        dimensionKeyToTrains.put(dimensionKey, worldTrains);

        for (Train train : worldTrains.values()) {
            if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
                buildContinuousPathForTrain(world, train);
            }
        }
    }

    public static void updateTrainMovement(World world) {
        String dimensionKey = getDimensionKey(world);
        Map<String, Train> worldTrains = dimensionKeyToTrains.get(dimensionKey);

        if (worldTrains != null) {
            cleanupOrphanedTrains(world, worldTrains);
            
            for (Train train : worldTrains.values()) {
                if (train.getCarriageIds() == null || train.getCarriageIds().isEmpty()) {
                    continue;
                }
                
                if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
                    buildContinuousPathForTrain(world, train);
                    if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
                        continue;
                    }
                }

                train.updateMovement(world);

                if (world instanceof ServerWorld serverWorld) {
                    if (pendingRefreshAtDepot.contains(train.getTrainId()) && train.getMovementState() == Train.MovementState.DWELLING_AT_DEPOT) {
                        buildContinuousPathForTrain(serverWorld, train);
                        pendingRefreshAtDepot.remove(train.getTrainId());
                        
                        saveTrains(serverWorld);
                        TrainModNetworking.broadcastTrainSync(serverWorld);
                    }
                }

                if (world instanceof ServerWorld && world.getTime() % 20 == 0) {
                    syncTrainToClient(world, train);
                }
            }
        }
    }

    private static void cleanupOrphanedTrains(World world, Map<String, Train> worldTrains) {
        List<String> trainsToRemove = new ArrayList<>();
        
        for (Map.Entry<String, Train> entry : worldTrains.entrySet()) {
            Train train = entry.getValue();
            
            if (train.getCarriageIds() == null || train.getCarriageIds().isEmpty()) {
                trainsToRemove.add(entry.getKey());
                TrainMod.LOGGER.info("Removing train {} with invalid configuration", train.getTrainId());
                continue;
            }
            
            String trackSegmentKey = train.getTrackSegmentKey();
            if (trackSegmentKey != null) {
                TrackSegment siding = getSidingByKey(world, trackSegmentKey);
                if (siding == null || !siding.isSiding() || !train.getTrainId().equals(siding.getTrainId())) {
                    trainsToRemove.add(entry.getKey());
                    TrainMod.LOGGER.info("Removing orphaned train {} from siding {}", train.getTrainId(), trackSegmentKey);
                }
            }
        }
        
        for (String trainId : trainsToRemove) {
            worldTrains.remove(trainId);
        }
        
        if (!trainsToRemove.isEmpty()) {
            TrainMod.LOGGER.info("Cleaned up {} orphaned trains", trainsToRemove.size());
        }
    }

    private static String getDimensionKey(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static TrackSegment getSidingByKey(World world, String sidingKey) {
        String[] parts = sidingKey.split("->");
        if (parts.length != 2) {
            return null;
        }

        BlockPos start = parseBlockPos(parts[0]);
        BlockPos end = parseBlockPos(parts[1]);

        if (start == null || end == null) {
            return null;
        }

        return TrackManager.getTrackSegment(world, start);
    }

    private static BlockPos parseBlockPos(String posString) {
        String[] parts = posString.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            TrainMod.LOGGER.warn("Failed to parse block position: {}", posString, e);
            return null;
        }
    }

    private static void syncTrainToClient(World world, Train train) {
        if (world instanceof ServerWorld serverWorld) {
            TrainModNetworking.broadcastTrainSync(serverWorld);
        }
    }

    private static void broadcastBoardingSync(ServerWorld world) {
        Map<String, List<BoardingSyncPayload.BoardingData>> allBoardingData = new HashMap<>();
        
        for (Train train : getTrainsFor(world).values()) {
            List<BoardingSyncPayload.BoardingData> trainBoardingData = new ArrayList<>();
            for (Map.Entry<String, Train.BoardingData> entry : train.getBoardedPlayers().entrySet()) {
                String playerId = entry.getKey();
                Train.BoardingData data = entry.getValue();
                trainBoardingData.add(new BoardingSyncPayload.BoardingData(playerId, data.carriageIndex(), data.relativeX(), data.relativeY(), data.relativeZ()));
            }

            allBoardingData.put(train.getTrainId(), trainBoardingData);
        }
        
        BoardingSyncPayload payload = new BoardingSyncPayload(allBoardingData);
        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void sendBoardingResponse(ServerWorld world, ServerPlayerEntity player, String trainId, int carriageIndex, boolean success) {
        BoardingResponsePayload response = new BoardingResponsePayload(trainId, carriageIndex, success, 0.0, 0.0, 0.0);
        ServerPlayNetworking.send(player, response);
    }
    
    private static double calculateDepotDistance(Train train, TrackSegment sidingTrack) {
        if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
            return 0.0;
        }
        
        Vec3d sidingStart = new Vec3d(sidingTrack.start().getX() + 0.5, sidingTrack.start().getY(), sidingTrack.start().getZ() + 0.5);
        Vec3d sidingEnd = new Vec3d(sidingTrack.end().getX() + 0.5, sidingTrack.end().getY(), sidingTrack.end().getZ() + 0.5);
        Vec3d sidingMid = new Vec3d((sidingStart.x + sidingEnd.x) * 0.5, (sidingStart.y + sidingEnd.y) * 0.5, (sidingStart.z + sidingEnd.z) * 0.5);
        
        List<Vec3d> pathPoints = train.getContinuousPathPoints();
        int bestIndex = 0;
        double bestDistSq = Double.MAX_VALUE;
        
        for (int i = 0; i < pathPoints.size(); i++) {
            Vec3d p = pathPoints.get(i);
            double dx = p.x - sidingMid.x;
            double dy = p.y - sidingMid.y;
            double dz = p.z - sidingMid.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestIndex = i;
            }
        }
        
        double depotDistance = 0.0;
        for (int i = 0; i < bestIndex; i++) {
            if (i + 1 < pathPoints.size()) {
                depotDistance += pathPoints.get(i).distanceTo(pathPoints.get(i + 1));
            }
        }
        
        return depotDistance;
    }
}
