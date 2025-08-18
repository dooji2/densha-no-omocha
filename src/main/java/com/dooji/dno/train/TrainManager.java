package com.dooji.dno.train;

import com.dooji.dno.TrainMod;
import com.dooji.dno.network.TrainModNetworking;
import com.dooji.dno.track.TrackManager;
import com.dooji.dno.track.TrackSegment;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
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
        Map<String, TrackSegment> allTracks = TrackManager.getTracksFor(world);

        if (allTracks.isEmpty()) {
            return;
        }

        TrackSegment sidingTrack = null;
        for (TrackSegment segment : allTracks.values()) {
            if (segment.getType().equals("siding") && segment.getTrainId() != null && segment.getTrainId().equals(train.getTrainId())) {
                sidingTrack = segment;
                break;
            }
        }

        if (sidingTrack == null) {
            return;
        }

        List<TrackSegment> pathSegments = buildPathFromSiding(allTracks, sidingTrack);
        List<Vec3d> continuousPoints = buildContinuousPoints(pathSegments);

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

        train.setPath(pathSegments.stream()
            .map(segment -> segment.start().getX() + "," + segment.start().getY() + "," + segment.start().getZ() + "->" + segment.end().getX() + "," + segment.end().getY() + "," + segment.end().getZ())
            .collect(Collectors.toList()));

        if (!continuousPoints.isEmpty()) {
            if (train.getMovementState() == null || train.getMovementState() == Train.MovementState.SPAWNED) {
                Vec3d sidingStart = new Vec3d(sidingTrack.start().getX() + 0.5, sidingTrack.start().getY(), sidingTrack.start().getZ() + 0.5);
                Vec3d sidingEnd = new Vec3d(sidingTrack.end().getX() + 0.5, sidingTrack.end().getY(), sidingTrack.end().getZ() + 0.5);
                Vec3d sidingMid = new Vec3d((sidingStart.x + sidingEnd.x) * 0.5, (sidingStart.y + sidingEnd.y) * 0.5, (sidingStart.z + sidingEnd.z) * 0.5);
                double depotDistance = getDistanceAlongPath(continuousPoints, sidingMid);

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

        Map<String, TrackSegment> allTracks = TrackManager.getTracksFor(world);
        if (allTracks.isEmpty()) return;

        List<TrackSegment> newSegments = buildPathFromSiding(allTracks, siding);
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
            List<Vec3d> newPoints = buildContinuousPoints(newSegments);
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

    private static List<TrackSegment> buildPathFromSiding(Map<String, TrackSegment> allTracks, TrackSegment startTrack) {
        List<TrackSegment> path = new ArrayList<>();
        Set<TrackSegment> visited = new HashSet<>();
        
        buildPathRecursive(startTrack, allTracks, path, visited);

        return path;
    }

    private static void buildPathRecursive(TrackSegment current, Map<String, TrackSegment> allTracks, List<TrackSegment> path, Set<TrackSegment> visited) {
        if (visited.contains(current)) {
            return;
        }

        visited.add(current);
        path.add(current);

        for (TrackSegment other : allTracks.values()) {
            if (visited.contains(other)) {
                continue;
            }

            if (current.start().equals(other.start()) ||
                    current.start().equals(other.end()) ||
                    current.end().equals(other.start()) ||
                    current.end().equals(other.end())) {
                buildPathRecursive(other, allTracks, path, visited);
            }
        }
    }

    private static List<Vec3d> buildContinuousPoints(List<TrackSegment> segments) {
        List<Vec3d> points = new ArrayList<>();
        BlockPos currentEndpoint = null;

        for (TrackSegment segment : segments) {
            List<Vec3d> forwardPoints = getSegmentPoints(segment);
            List<Vec3d> chosen = forwardPoints;

            if (currentEndpoint == null) {
                currentEndpoint = segment.end();
            } else {
                boolean connectsAtStart = segment.start().equals(currentEndpoint);
                boolean connectsAtEnd = segment.end().equals(currentEndpoint);

                if (connectsAtStart) {
                    chosen = forwardPoints;
                    currentEndpoint = segment.end();
                } else if (connectsAtEnd) {
                    List<Vec3d> reversed = new ArrayList<>(forwardPoints);
                    Collections.reverse(reversed);

                    chosen = reversed;
                    currentEndpoint = segment.start();
                } else {
                    Vec3d last = points.isEmpty() ? null : points.get(points.size() - 1);
                    if (last != null) {
                        Vec3d startCenter = new Vec3d(segment.start().getX() + 0.5, segment.start().getY(), segment.start().getZ() + 0.5);
                        Vec3d endCenter = new Vec3d(segment.end().getX() + 0.5, segment.end().getY(), segment.end().getZ() + 0.5);

                        double ds = last.distanceTo(startCenter);
                        double de = last.distanceTo(endCenter);

                        if (de < ds) {
                            List<Vec3d> reversed = new ArrayList<>(forwardPoints);
                            Collections.reverse(reversed);

                            chosen = reversed;
                            currentEndpoint = segment.start();
                        } else {
                            chosen = forwardPoints;
                            currentEndpoint = segment.end();
                        }
                    } else {
                        currentEndpoint = segment.end();
                    }
                }
            }

            for (Vec3d p : chosen) {
                if (points.isEmpty() || !points.get(points.size() - 1).equals(p)) {
                    points.add(p);
                }
            }
        }

        return points;
    }

    private static List<Vec3d> getSegmentPoints(TrackSegment segment) {
        List<Vec3d> points = new ArrayList<>();

        double totalLength = Math.sqrt(
            Math.pow(segment.end().getX() - segment.start().getX(), 2) + 
            Math.pow(segment.end().getY() - segment.start().getY(), 2) + 
            Math.pow(segment.end().getZ() - segment.start().getZ(), 2)
        );
        
        double startAngle = Math.atan2(segment.startDirection().getOffsetZ(), segment.startDirection().getOffsetX());
        double endAngle = Math.atan2(segment.endDirection().getOffsetZ(), segment.endDirection().getOffsetX());

        double angleDifference = endAngle - startAngle;
        while (angleDifference > Math.PI) angleDifference -= 2 * Math.PI;
        while (angleDifference < -Math.PI) angleDifference += 2 * Math.PI;

        boolean tangentsColinear = segment.startDirection() == segment.endDirection() || segment.startDirection() == segment.endDirection().getOpposite();
        boolean axisAligned;

        if (segment.startDirection().getAxis() == Direction.Axis.X) {
            axisAligned = Math.abs((segment.end().getZ() + 0.5) - (segment.start().getZ() + 0.5)) < 1e-6;
        } else {
            axisAligned = Math.abs((segment.end().getX() + 0.5) - (segment.start().getX() + 0.5)) < 1e-6;
        }
        
        boolean isStraightTrack = tangentsColinear && axisAligned;

        if (isStraightTrack) {
            points.addAll(generateStraightTrackPoints(segment, totalLength));
        } else {
            points.addAll(generateCurvedTrackPoints(segment, totalLength, angleDifference));
        }

        return points;
    }

    private static List<Vec3d> generateStraightTrackPoints(TrackSegment segment, double totalLength) {
        List<Vec3d> points = new ArrayList<>();
        double stepSize = 0.5;

        double startCenterX = segment.start().getX() + 0.5;
        double startCenterZ = segment.start().getZ() + 0.5;
        double endCenterX = segment.end().getX() + 0.5;
        double endCenterZ = segment.end().getZ() + 0.5;
        double startY = segment.start().getY();
        double endY = segment.end().getY();

        for (double distanceAlongPath = 0.0; distanceAlongPath <= totalLength; distanceAlongPath += stepSize) {
            double parameterT = totalLength > 1e-9 ? distanceAlongPath / totalLength : 0.0;
            double verticalT = applyVerticalEase(parameterT, segment.getSlopeCurvature());

            double x = startCenterX + (endCenterX - startCenterX) * parameterT;
            double z = startCenterZ + (endCenterZ - startCenterZ) * parameterT;
            double y = startY + (endY - startY) * verticalT;

            points.add(new Vec3d(x, y, z));
        }

        return points;
    }

    private static List<Vec3d> generateCurvedTrackPoints(TrackSegment segment, double totalLength, double angleDifference) {
        List<Vec3d> points = new ArrayList<>();

        double startCenterX = segment.start().getX() + 0.5;
        double startCenterZ = segment.start().getZ() + 0.5;
        double endCenterX = segment.end().getX() + 0.5;
        double endCenterZ = segment.end().getZ() + 0.5;
        double startY = segment.start().getY();
        double endY = segment.end().getY();

        double deltaAbsX = Math.abs(endCenterX - startCenterX);
        double deltaAbsZ = Math.abs(endCenterZ - startCenterZ);

        Vec3d startDirectionVec = new Vec3d(segment.startDirection().getOffsetX(), 0, segment.startDirection().getOffsetZ()).normalize();
        Vec3d endDirectionVec = new Vec3d(segment.endDirection().getOffsetX(), 0, segment.endDirection().getOffsetZ()).normalize();

        boolean hasStartAxis = segment.startDirection().getAxis() != null;
        boolean hasEndAxis = segment.endDirection().getAxis() != null;
        
        double startAxisDistance = hasStartAxis ? Math.max(deltaAbsX, deltaAbsZ) : Math.min(deltaAbsX, deltaAbsZ);
        double endAxisDistance = hasEndAxis ? Math.max(deltaAbsX, deltaAbsZ) : Math.min(deltaAbsX, deltaAbsZ);
        double circleApproxFactor = 0.55228477;

        double startControlDist = startAxisDistance * circleApproxFactor;
        double endControlDist = endAxisDistance * circleApproxFactor;

        double control1X = startCenterX + startDirectionVec.x * startControlDist;
        double control1Z = startCenterZ + startDirectionVec.z * startControlDist;
        double control2X = endCenterX - endDirectionVec.x * endControlDist;
        double control2Z = endCenterZ - endDirectionVec.z * endControlDist;

        int sampleCount = 200;
        double[] sampledX = new double[sampleCount + 1];
        double[] sampledZ = new double[sampleCount + 1];
        double[] sampledY = new double[sampleCount + 1];
        double[] cumulativeDistances = new double[sampleCount + 1];

        sampledX[0] = startCenterX;
        sampledZ[0] = startCenterZ;
        sampledY[0] = startY;
        cumulativeDistances[0] = 0.0;

        for (int i = 1; i <= sampleCount; i++) {
            double parameterT = (double) i / sampleCount;
            double omt = 1.0 - parameterT;
            double bezierX = omt * omt * omt * startCenterX + 3 * omt * omt * parameterT * control1X + 3 * omt * parameterT * parameterT * control2X + parameterT * parameterT * parameterT * endCenterX;
            double bezierZ = omt * omt * omt * startCenterZ + 3 * omt * omt * parameterT * control1Z + 3 * omt * parameterT * parameterT * control2Z + parameterT * parameterT * parameterT * endCenterZ;
            double verticalT = applyVerticalEase(parameterT, segment.getSlopeCurvature());
            double bezierY = startY + (endY - startY) * verticalT;

            sampledX[i] = bezierX;
            sampledZ[i] = bezierZ;
            sampledY[i] = bezierY;
            double segLen = Math.sqrt((sampledX[i] - sampledX[i - 1]) * (sampledX[i] - sampledX[i - 1]) + (sampledZ[i] - sampledZ[i - 1]) * (sampledZ[i] - sampledZ[i - 1]) + (sampledY[i] - sampledY[i - 1]) * (sampledY[i] - sampledY[i - 1]));
            cumulativeDistances[i] = cumulativeDistances[i - 1] + segLen;
        }

        double stepSize = 0.5;
        for (double distanceAlongPath = 0.0; distanceAlongPath <= cumulativeDistances[sampleCount] + 1e-6; distanceAlongPath += stepSize) {
            int i = 1;

            while (i <= sampleCount && cumulativeDistances[i] < distanceAlongPath) i++;
            if (i > sampleCount) i = sampleCount;

            int i0 = i - 1;
            double seg = cumulativeDistances[i] - cumulativeDistances[i0];
            double alpha = seg > 1e-6 ? (distanceAlongPath - cumulativeDistances[i0]) / seg : 0.0;
            double x = sampledX[i0] + (sampledX[i] - sampledX[i0]) * alpha;
            double y = sampledY[i0] + (sampledY[i] - sampledY[i0]) * alpha;
            double z = sampledZ[i0] + (sampledZ[i] - sampledZ[i0]) * alpha;
            points.add(new Vec3d(x, y, z));
        }

        return points;
    }
    
    private static double applyVerticalEase(double t, double curvature) {
        double tt = Math.max(0.0, Math.min(1.0, t));
        double smooth = tt * tt * (3.0 - 2.0 * tt);
        double smoother = tt * tt * tt * (tt * (tt * 6 - 15) + 10);
        double c = Math.max(-1.0, Math.min(1.0, curvature));

        if (c < 0.0) {
            double a = -c;
            return smooth * (1.0 - a) + tt * a;
        } else if (c > 0.0) {
            double a = c;
            return smooth * (1.0 - a) + smoother * a;
        } else {
            return smooth;
        }
    }

    private static double getDistanceAlongPath(List<Vec3d> points, Vec3d target) {
        if (points == null || points.isEmpty()) return 0.0;
        int bestIndex = 0;
        double best = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            Vec3d p = points.get(i);
            double dx = p.x - target.x;
            double dy = p.y - target.y;
            double dz = p.z - target.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            
            if (d2 < best) {
                best = d2;
                bestIndex = i;
            }
        }

        double acc = 0.0;
        for (int i = 0; i < bestIndex; i++) acc += points.get(i).distanceTo(points.get(i + 1));
        return acc;
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
                if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
                    buildContinuousPathForTrain(world, train);
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
}
