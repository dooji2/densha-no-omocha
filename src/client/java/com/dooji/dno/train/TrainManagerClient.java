package com.dooji.dno.train;

import com.dooji.dno.network.payloads.TrainSyncPayload;
import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.track.TrackSegment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

public class TrainManagerClient {
    private static final Map<String, Map<String, TrainClient>> dimensionKeyToTrains = new HashMap<>();

    public static void updateTrainMovementClient(World world, float frameDelta) {
        for (Map.Entry<String, Map<String, TrainClient>> entry : dimensionKeyToTrains.entrySet()) {
            Map<String, TrainClient> worldTrains = entry.getValue();
            for (TrainClient train : worldTrains.values()) {
                if (train.getContinuousPathPoints() == null || train.getContinuousPathPoints().isEmpty()) buildContinuousPathForTrain(world, train);
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
            if (clientTrain.getContinuousPathPoints() == null || clientTrain.getContinuousPathPoints().isEmpty()) buildContinuousPathForTrain(MinecraftClient.getInstance().world, clientTrain);
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

    private static void buildContinuousPathForTrain(World world, TrainClient train) {
        Map<String, TrackSegment> allTracks = TrackManagerClient.getTracksFor(world);
        if (allTracks.isEmpty()) return;
        TrackSegment sidingTrack = null;

        for (TrackSegment segment : allTracks.values()) {
            if ("siding".equals(segment.getType()) && segment.getTrainId() != null && segment.getTrainId().equals(train.getTrainId())) {
                sidingTrack = segment;
                break;
            }
        }

        if (sidingTrack == null) return;
        List<TrackSegment> pathSegments = buildPathFromSiding(allTracks, sidingTrack);
        List<Vec3d> continuousPoints = buildContinuousPoints(pathSegments);
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
                            java.util.Collections.reverse(reversed);
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

        for (double distance = 0; distance <= totalLength; distance += stepSize) {
            double t = distance / totalLength;
            double verticalT = applyVerticalEase(t, segment.getSlopeCurvature());
            double x = startCenterX + (endCenterX - startCenterX) * t;
            double z = startCenterZ + (endCenterZ - startCenterZ) * t;
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
            double t = (double) i / sampleCount;
            double omt = 1.0 - t;
            double bezierX = omt * omt * omt * startCenterX + 3 * omt * omt * t * control1X + 3 * omt * t * t * control2X + t * t * t * endCenterX;
            double bezierZ = omt * omt * omt * startCenterZ + 3 * omt * omt * t * control1Z + 3 * omt * t * t * control2Z + t * t * t * endCenterZ;
            double verticalT = applyVerticalEase(t, segment.getSlopeCurvature());
            double bezierY = startY + (endY - startY) * verticalT;

            sampledX[i] = bezierX;
            sampledZ[i] = bezierZ;
            sampledY[i] = bezierY;

            double segLen = Math.sqrt((sampledX[i] - sampledX[i - 1]) * (sampledX[i] - sampledX[i - 1]) + (sampledZ[i] - sampledZ[i - 1]) * (sampledZ[i] - sampledZ[i - 1]) + (sampledY[i] - sampledY[i - 1]) * (sampledY[i] - sampledY[i - 1]));
            cumulativeDistances[i] = cumulativeDistances[i - 1] + segLen;
        }

        double stepSize = 0.5;
        for (double distance = 0.0; distance <= cumulativeDistances[sampleCount] + 1e-6; distance += stepSize) {
            int i = 1;
            while (i <= sampleCount && cumulativeDistances[i] < distance) i++;

            if (i > sampleCount) i = sampleCount;
            int i0 = i - 1;
            double seg = cumulativeDistances[i] - cumulativeDistances[i0];
            double alpha = seg > 1e-6 ? (distance - cumulativeDistances[i0]) / seg : 0.0;
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
}
