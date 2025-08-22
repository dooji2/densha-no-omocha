package com.dooji.dno.train;

import com.dooji.dno.network.payloads.TrainSyncPayload;
import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.track.TrackSegment;
import com.dooji.dno.track.Route;
import com.dooji.dno.track.RouteManagerClient;
import com.dooji.dno.track.RoutePathfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class TrainManagerClient {
    private static final Map<String, Map<String, TrainClient>> dimensionKeyToTrains = new HashMap<>();

    public static void updateTrainMovementClient(World world, float frameDelta) {
        for (Map.Entry<String, Map<String, TrainClient>> entry : dimensionKeyToTrains.entrySet()) {
            Map<String, TrainClient> worldTrains = entry.getValue();
            for (TrainClient train : worldTrains.values()) {
                if (train.getCarriageIds() == null || train.getCarriageIds().isEmpty()) {
                    continue;
                }
                
                if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
                    buildContinuousPathForTrain(world, train);
                    if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) {
                        continue;
                    }
                }
                
                train.simulateTrainClient(world, frameDelta);
            }
        }
    }

    public static void handleTrainSync(TrainSyncPayload payload) {
        String dimensionKey = payload.dimensionKey();
        Map<String, TrainSyncPayload.TrainData> trainDataMap = payload.trains();
        Map<String, TrainClient> clientTrains = dimensionKeyToTrains.computeIfAbsent(dimensionKey, k -> new HashMap<>());

        for (Map.Entry<String, TrainSyncPayload.TrainData> entry : trainDataMap.entrySet()) {
            String trainId = entry.getKey();
            TrainSyncPayload.TrainData serverData = entry.getValue();
            TrainClient clientTrain = clientTrains.get(trainId);

            if (clientTrain == null) {
                clientTrain = new TrainClient(serverData.trainId(), serverData.carriageIds(), serverData.isReversed(), serverData.trackSegmentKey());
                clientTrains.put(trainId, clientTrain);
            }

            clientTrain.updateFromServerSync(payload);
            
            if (clientTrain.getCarriageIds() == null || clientTrain.getCarriageIds().isEmpty()) {
                clientTrains.remove(trainId);
                continue;
            }
            
            if (clientTrain.getContinuousPathPoints() == null || clientTrain.getContinuousPathPoints().isEmpty()) {
                buildContinuousPathForTrain(MinecraftClient.getInstance().world, clientTrain);
            }
        }
        clientTrains.entrySet().removeIf(entry -> !trainDataMap.containsKey(entry.getKey()));
    }

    public static Map<String, TrainClient> getTrainsFor(World world) {
        if (world == null) return new HashMap<>();
        String dimensionKey = world.getRegistryKey().getValue().toString();
        return dimensionKeyToTrains.getOrDefault(dimensionKey, new HashMap<>());
    }

    public static void clearAllTrains() {
        dimensionKeyToTrains.clear();
    }

    private static TrackSegment getSidingByKey(World world, String trackSegmentKey) {
        if (trackSegmentKey == null || trackSegmentKey.trim().isEmpty()) {
            return null;
        }

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

                    Map<String, TrackSegment> allTracks = TrackManagerClient.getTracksFor(world);
                    for (TrackSegment segment : allTracks.values()) {
                        if (segment.start().equals(start) && segment.end().equals(end)) {
                            return segment;
                        }
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private static void buildContinuousPathForTrain(World world, TrainClient train) {
        TrackSegment sidingTrack = getSidingByKey(world, train.getTrackSegmentKey());
        if (sidingTrack == null) return;

        String routeId = sidingTrack.getRouteId();
        if (routeId == null || routeId.trim().isEmpty()) {
            return;
        }

        Route route = RouteManagerClient.getRoute(routeId);
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
                Vec3d a = continuousPoints.get(i);
                Vec3d b = continuousPoints.get(i + 1);
                totalPathLength += a.distanceTo(b);
            }
        }

        train.setTotalPathLength(totalPathLength);
        
        List<String> pathKeys = new ArrayList<>();
        for (TrackSegment segment : pathSegments) {
            String trackKey = segment.start().getX() + "," + segment.start().getY() + "," + segment.start().getZ() + "->" + segment.end().getX() + "," + segment.end().getY() + "," + segment.end().getZ();
            pathKeys.add(trackKey);
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

    private static boolean pickInitialDirection(World world, TrainClient train) {
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
    
    private static double calculateDepotDistance(TrainClient train, TrackSegment sidingTrack) {
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
