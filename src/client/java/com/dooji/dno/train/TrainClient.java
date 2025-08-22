package com.dooji.dno.train;

import com.dooji.dno.TrainModClient;
import com.dooji.dno.network.payloads.TrainSyncPayload;
import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.track.TrackSegment;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public class TrainClient {
    private static final long SYNC_INTERVAL_MS = 10000;
    private long lastSyncAtMillis = 0;

    private final String trainId;
    private List<String> carriageIds;
    private boolean isReversed;
    private String trackSegmentKey;
    private double speed;
    private double maxSpeed;
    private double accelerationConstant;
    private long lastUpdateTime;
    private Set<String> visitedPlatforms;
    private String currentPlatformId;
    private long dwellStartTime;
    private boolean isReturningToDepot;
    private float doorValue;
    private boolean doorTarget;
    private Train.MovementState movementState;
    private List<Vec3d> continuousPathPoints;
    private double totalPathLength;
    private double currentPathDistance;
    
    private boolean movingForward;
    private double depotPathDistance;
    private List<String> path;
    private int currentPlatformIndex;
    private List<Double> carriageLengths;
    private List<Double> bogieInsets;
    private List<Double> boundingBoxWidths;
    private List<Double> boundingBoxLengths;
    private List<Double> boundingBoxHeights;

    public TrainClient(String trainId, List<String> carriageIds, boolean isReversed, String trackSegmentKey) {
        this.trainId = trainId;
        this.carriageIds = new ArrayList<>(carriageIds);
        this.isReversed = isReversed;
        this.trackSegmentKey = trackSegmentKey;
        this.speed = 0.0;
        this.maxSpeed = 1.67;
        this.accelerationConstant = 0.023;
        this.lastUpdateTime = System.currentTimeMillis();
        this.visitedPlatforms = new HashSet<>();
        this.currentPlatformId = null;
        this.dwellStartTime = 0L;
        this.isReturningToDepot = false;
        this.doorValue = 0.0f;
        this.doorTarget = false;
        this.movementState = Train.MovementState.SPAWNED;
        this.continuousPathPoints = new ArrayList<>();
        this.totalPathLength = 0.0;
        this.currentPathDistance = 0.0;
        this.movingForward = true;
        this.depotPathDistance = 0.0;
        this.path = new ArrayList<>();
        this.currentPlatformIndex = 0;
        this.carriageLengths = new ArrayList<>();
        this.bogieInsets = new ArrayList<>();
        this.boundingBoxWidths = new ArrayList<>();
        this.boundingBoxLengths = new ArrayList<>();
        this.boundingBoxHeights = new ArrayList<>();
    }

    public void simulateTrainClient(World world, float frameDelta) {
        updateMovementClient(frameDelta, world);
        updateDoorAnimation(frameDelta);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastSyncAtMillis > SYNC_INTERVAL_MS) {
            lastSyncAtMillis = currentTime;
        }
    }

    private void updateDoorAnimation(float frameDelta) {
        boolean target = movementState == Train.MovementState.DWELLING_AT_PLATFORM && doorTarget;
        float step = 0.12f * frameDelta;
        
        if (target) {
            doorValue = Math.min(1.0f, doorValue + step);
        } else {
            doorValue = Math.max(0.0f, doorValue - step);
        }
    }

    public void updateFromServerSync(TrainSyncPayload payload) {
        Map<String, TrainSyncPayload.TrainData> trainDataMap = payload.trains();
        TrainSyncPayload.TrainData serverData = trainDataMap.get(trainId);
        if (serverData != null) {
            this.carriageIds = new ArrayList<>(serverData.carriageIds());
            this.trackSegmentKey = serverData.trackSegmentKey();
            this.isReversed = serverData.isReversed();

            if (serverData.speed() != null) this.speed = serverData.speed();
            if (serverData.maxSpeed() != null) this.maxSpeed = serverData.maxSpeed();
            if (serverData.accelerationConstant() != null) this.accelerationConstant = serverData.accelerationConstant();
            if (serverData.doorValue() != null) this.doorValue = serverData.doorValue();
            if (serverData.doorTarget() != null) this.doorTarget = serverData.doorTarget();
            if (serverData.isReturningToDepot() != null) this.isReturningToDepot = serverData.isReturningToDepot();

            if (serverData.continuousPathPoints() != null) {
                this.continuousPathPoints = new ArrayList<>(serverData.continuousPathPoints());
            }
            
            if (serverData.path() != null) {
                this.path = new ArrayList<>(serverData.path());
            }
            
            if (serverData.currentPlatformIndex() != null) {
                this.currentPlatformIndex = serverData.currentPlatformIndex();
            }
            
            if (serverData.totalPathLength() != null) {
                this.totalPathLength = serverData.totalPathLength();
            }
            
            if (serverData.movingForward() != null) {
                this.movingForward = serverData.movingForward();
            }

            if (serverData.carriageLengths() != null) {
                this.carriageLengths = new ArrayList<>(serverData.carriageLengths());
            }

            if (serverData.bogieInsets() != null) {
                this.bogieInsets = new ArrayList<>(serverData.bogieInsets());
            }

            if (serverData.boundingBoxWidths() != null) {
                this.boundingBoxWidths = new ArrayList<>(serverData.boundingBoxWidths());
            }

            if (serverData.boundingBoxLengths() != null) {
                this.boundingBoxLengths = new ArrayList<>(serverData.boundingBoxLengths());
            }

            if (serverData.boundingBoxHeights() != null) {
                this.boundingBoxHeights = new ArrayList<>(serverData.boundingBoxHeights());
            }

            String state = serverData.movementState();
            if (state != null) {
                try {
                    Train.MovementState serverState = Train.MovementState.valueOf(state);
                    this.movementState = serverState;
                    
                    if (serverState == Train.MovementState.DWELLING_AT_PLATFORM || serverState == Train.MovementState.DWELLING_AT_DEPOT) {
                        if (serverData.currentPathDistance() != null) {
                            
                            this.currentPathDistance = serverData.currentPathDistance();
                            this.speed = 0.0;
                        }

                        if (serverData.dwellStartTime() != null) {
                            this.dwellStartTime = serverData.dwellStartTime();
                        }

                        if (serverData.currentPlatformId() != null) {
                            this.currentPlatformId = serverData.currentPlatformId();
                        }

                        if (serverData.visitedPlatforms() != null && !serverData.visitedPlatforms().isEmpty()) {
                            this.visitedPlatforms = new HashSet<>(serverData.visitedPlatforms());
                        }
                    } else {
                        if (serverData.currentPathDistance() != null) {
                            
                            this.currentPathDistance = serverData.currentPathDistance();
                        }
                        
                        if (serverData.currentPlatformId() != null) {
                            this.currentPlatformId = serverData.currentPlatformId();
                        }
                        
                        if (serverData.visitedPlatforms() != null) {
                            this.visitedPlatforms = new HashSet<>(serverData.visitedPlatforms());
                        }
                    }
                } catch (IllegalArgumentException e) {
                    TrainModClient.LOGGER.error("Failed to parse server data", e);
                }
            }
        }
    }

    public int getDwellTimeForCurrentPlatform(World world) {
        if (currentPlatformId == null) {
            return 15;
        }

        TrackSegment segment = findTrackSegmentByKey(currentPlatformId, world);
        if (segment != null) {
            return segment.getDwellTimeSeconds();
        }

        return 15;
    }

    public double getDistanceToNextPlatform(World world) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty() || path == null || path.isEmpty()) {
            return Double.MAX_VALUE;
        }

        for (int i = currentPlatformIndex; i < path.size(); i++) {
            String trackKey = path.get(i);
            TrackSegment segment = findTrackSegmentByKey(trackKey, world);

            if (segment != null && segment.isPlatform()) {
                String platformId = trackKey;
                if (visitedPlatforms.contains(platformId)) {
                    continue;
                }

                if (!segment.getType().equals("platform")) {
                    continue;
                }

                double platformStartDistance = getDistanceAlongPathTo(segment.start());
                double platformEndDistance = getDistanceAlongPathTo(segment.end());

                if (platformEndDistance < platformStartDistance) {
                    platformEndDistance = platformStartDistance;
                }

                double safety = 1.0;
                double stopDistance = Math.min(totalPathLength, platformEndDistance - safety);
                double distanceToStop = stopDistance - currentPathDistance;

                return Math.max(0.1, distanceToStop);
            }
        }

        return Double.MAX_VALUE;
    }

    private TrackSegment findTrackSegmentByKey(String trackKey, World world) {
        if (world == null) {
            return null;
        }

        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(world);
        return tracks.get(trackKey);
    }

    private TrackSegment getCurrentTrackSegment(World world) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty() || path == null || path.isEmpty()) {
            return null;
        }

        int currentIndex = getCurrentPathIndex();
        if (currentIndex < 0 || currentIndex >= path.size()) {
            return null;
        }

        String currentTrackKey = path.get(currentIndex);
        return findTrackSegmentByKey(currentTrackKey, world);
    }

    private int getCurrentPathIndex() {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty() || path == null || path.isEmpty()) {
            return -1;
        }

        if (currentPathDistance <= 0) {
            return 0;
        }

        if (currentPathDistance >= totalPathLength) {
            return path.size() - 1;
        }

        double progressRatio = currentPathDistance / totalPathLength;
        int pathIndex = (int) (progressRatio * path.size());
        return Math.min(Math.max(pathIndex, 0), path.size() - 1);
    }

    private double getCurrentTrackSpeedLimit(World world) {
        TrackSegment currentSegment = getCurrentTrackSegment(world);
        if (currentSegment != null) {
            return currentSegment.getMaxSpeedKmh() / 3.6;
        }

        return maxSpeed;
    }

    private double getDistanceAlongPathTo(BlockPos pos) {
        int idx = getNearestPathIndexTo(pos);
        return getCumulativeDistanceAtIndex(idx);
    }

    private int getNearestPathIndexTo(BlockPos pos) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty()) return 0;

        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        int bestIndex = 0;
        double bestDistSq = Double.MAX_VALUE;

        for (int i = 0; i < continuousPathPoints.size(); i++) {
            Vec3d p = continuousPathPoints.get(i);
            double dx = p.x - target.x;
            double dy = p.y - target.y;
            double dz = p.z - target.z;
            double d2 = dx * dx + dy * dy + dz * dz;

            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private double getCumulativeDistanceAtIndex(int index) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty()) return 0.0;

        index = Math.max(0, Math.min(index, continuousPathPoints.size() - 1));
        double acc = 0.0;

        for (int i = 0; i < index; i++) {
            acc += continuousPathPoints.get(i).distanceTo(continuousPathPoints.get(i + 1));
        }

        return acc;
    }

    public String getTrainId() {
        return trainId;
    }

    public List<String> getCarriageIds() {
        return new ArrayList<>(carriageIds);
    }

    public boolean isReversed() {
        return isReversed;
    }

    public void setReversed(boolean reversed) {
        this.isReversed = reversed;
    }

    public String getTrackSegmentKey() {
        return trackSegmentKey;
    }

    public List<Vec3d> getContinuousPathPoints() {
        return new ArrayList<>(continuousPathPoints);
    }

    public void setContinuousPathPoints(List<Vec3d> points) {
        this.continuousPathPoints = new ArrayList<>(points);
        calculateTotalPathLength();
    }

    public double getTotalPathLength() {
        return totalPathLength;
    }

    public void setTotalPathLength(double totalPathLength) {
        this.totalPathLength = totalPathLength;
    }

    public double getCurrentPathDistance() {
        return currentPathDistance;
    }

    public void setCurrentPathDistance(double distance) {
        this.currentPathDistance = distance;
    }

    public boolean isMovingForward() {
        return movingForward;
    }

    public void setMovingForward(boolean movingForward) {
        this.movingForward = movingForward;
    }

    public double getDepotPathDistance() {
        return depotPathDistance;
    }

    public void setDepotPathDistance(double depotPathDistance) {
        this.depotPathDistance = depotPathDistance;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public double getAccelerationConstant() {
        return accelerationConstant;
    }

    public void setAccelerationConstant(double accelerationConstant) {
        this.accelerationConstant = accelerationConstant;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Set<String> getVisitedPlatforms() {
        return new HashSet<>(visitedPlatforms);
    }

    public void setVisitedPlatforms(Set<String> visitedPlatforms) {
        this.visitedPlatforms = new HashSet<>(visitedPlatforms);
    }

    public String getCurrentPlatformId() {
        return currentPlatformId;
    }

    public void setCurrentPlatformId(String currentPlatformId) {
        this.currentPlatformId = currentPlatformId;
    }

    public long getDwellStartTime() {
        return dwellStartTime;
    }

    public void setDwellStartTime(long dwellStartTime) {
        this.dwellStartTime = dwellStartTime;
    }

    public boolean isReturningToDepot() {
        return isReturningToDepot;
    }

    public void setReturningToDepot(boolean returningToDepot) {
        this.isReturningToDepot = returningToDepot;
    }

    public Train.MovementState getMovementState() {
        return movementState;
    }

    public void setMovementState(Train.MovementState movementState) {
        this.movementState = movementState;
    }

    public List<String> getPath() {
        return new ArrayList<>(path);
    }

    public void setPath(List<String> path) {
        this.path = new ArrayList<>(path);
    }

    public int getCurrentPlatformIndex() {
        return currentPlatformIndex;
    }

    public void setCurrentPlatformIndex(int currentPlatformIndex) {
        this.currentPlatformIndex = currentPlatformIndex;
    }

    public void setCarriageLengths(List<Double> lengths) {
        this.carriageLengths = new ArrayList<>(lengths);
    }

    public List<Double> getCarriageLengths() {
        return new ArrayList<>(carriageLengths);
    }

    public void setBogieInsets(List<Double> insets) {
        this.bogieInsets = new ArrayList<>(insets);
    }

    public List<Double> getBogieInsets() {
        return new ArrayList<>(bogieInsets);
    }

    public void setBoundingBoxWidths(List<Double> widths) {
        this.boundingBoxWidths = new ArrayList<>(widths);
    }

    public List<Double> getBoundingBoxWidths() {
        return new ArrayList<>(boundingBoxWidths);
    }

    public void setBoundingBoxLengths(List<Double> lengths) {
        this.boundingBoxLengths = new ArrayList<>(lengths);
    }

    public List<Double> getBoundingBoxLengths() {
        return new ArrayList<>(boundingBoxLengths);
    }

    public void setBoundingBoxHeights(List<Double> heights) {
        this.boundingBoxHeights = new ArrayList<>(heights);
    }

    public List<Double> getBoundingBoxHeights() {
        return new ArrayList<>(boundingBoxHeights);
    }

    public float getDoorValue() {
        return doorValue;
    }

    public void setDoorValue(float doorValue) {
        this.doorValue = doorValue;
    }

    public boolean getDoorTarget() {
        return doorTarget;
    }

    public void setDoorTarget(boolean doorTarget) {
        this.doorTarget = doorTarget;
    }

    private void updateMovementClient(float ticksElapsed, World world) {
        switch (movementState) {
            case SPAWNED:
                if (System.currentTimeMillis() - dwellStartTime > 1000) {
                    movementState = Train.MovementState.WAITING_IN_DEPOT;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;

            case WAITING_IN_DEPOT:
                if (System.currentTimeMillis() - dwellStartTime > 10000) {
                    movementState = Train.MovementState.ACCELERATING;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;

            case ACCELERATING:
                double currentSpeedLimit = getCurrentTrackSpeedLimit(world);
                double acceleration = accelerationConstant * ticksElapsed;
                speed = Math.min(speed + acceleration, currentSpeedLimit);

                if (speed >= currentSpeedLimit) {
                    movementState = Train.MovementState.CRUISING;
                }

                if (isApproachingPlatform(world)) {
                    movementState = Train.MovementState.APPROACHING_PLATFORM;
                }

                currentPathDistance += speed * ticksElapsed;
                setCurrentPathDistance(currentPathDistance);
                break;

            case CRUISING:
                currentSpeedLimit = getCurrentTrackSpeedLimit(world);
                if (speed > currentSpeedLimit) {
                    double deceleration = accelerationConstant * ticksElapsed;
                    speed = Math.max(speed - deceleration, currentSpeedLimit);
                }

                if (isApproachingPlatform(world)) {
                    movementState = Train.MovementState.APPROACHING_PLATFORM;
                } else if (currentPathDistance >= totalPathLength && !hasMorePlatformsToVisit(world)) {
                    movementState = Train.MovementState.RETURNING_TO_DEPOT;
                    isReturningToDepot = true;
                    isReversed = true;
                }

                currentPathDistance += speed * ticksElapsed;
                setCurrentPathDistance(currentPathDistance);
                break;

            case APPROACHING_PLATFORM:
                double stoppingDistance = (speed * speed) / (2.0 * accelerationConstant);
                double distanceToPlatform = getDistanceToNextPlatform(world);

                if (distanceToPlatform <= stoppingDistance) {
                    double deceleration = accelerationConstant * ticksElapsed;
                    speed = Math.max(speed - deceleration, 0.0);

                    if (speed <= 0.1) {
                        speed = 0.0;
                        movementState = Train.MovementState.DWELLING_AT_PLATFORM;
                        dwellStartTime = System.currentTimeMillis();

                        String arrivingPlatformId = null;
                        for (int i = currentPlatformIndex; i < path.size(); i++) {
                            TrackSegment seg = findTrackSegmentByKey(path.get(i), world);
                            if (seg != null && seg.isPlatform()) {
                                arrivingPlatformId = path.get(i);
                                currentPlatformIndex = i;
                                break;
                            }
                        }
                        if (arrivingPlatformId != null) {
                            setCurrentPlatformId(arrivingPlatformId);
                            visitedPlatforms.add(arrivingPlatformId);
                        }
                    }
                }

                currentPathDistance += speed * ticksElapsed;
                setCurrentPathDistance(currentPathDistance);
                break;

            case DWELLING_AT_PLATFORM:
                long dwellTime = System.currentTimeMillis() - dwellStartTime;
                int platformDwellTime = getDwellTimeForCurrentPlatform(world);
                long platformDwellTicks = platformDwellTime * 20;

                if (dwellTime >= (platformDwellTicks * 50) + 1000) {
                    if (hasMorePlatformsToVisit(world)) {
                        currentPlatformIndex++;

                        for (int i = currentPlatformIndex; i < path.size(); i++) {
                            String trackKey = path.get(i);
                            TrackSegment segment = findTrackSegmentByKey(trackKey, world);

                            if (segment != null && segment.isPlatform()) {
                                String platformId = trackKey;
                                if (!visitedPlatforms.contains(platformId)) {
                                    if (segment.getType().equals("platform")) {
                                        setCurrentPlatformId(platformId);
                                        break;
                                    }
                                }
                            }
                        }

                        movementState = Train.MovementState.ACCELERATING;
                    } else {
                        movementState = Train.MovementState.RETURNING_TO_DEPOT;
                        isReturningToDepot = true;
                        isReversed = true;
                    }
                }
                break;

            case RETURNING_TO_DEPOT:
                currentSpeedLimit = getCurrentTrackSpeedLimit(world);
                double returnAcceleration = accelerationConstant * ticksElapsed;
                speed = Math.min(speed + returnAcceleration, currentSpeedLimit);
                currentPathDistance -= speed * ticksElapsed;
                setCurrentPathDistance(currentPathDistance);

                if (currentPathDistance <= 0.0) {
                    currentPathDistance = 0.0;
                    setCurrentPathDistance(currentPathDistance);
                    speed = 0.0;
                    movementState = Train.MovementState.DWELLING_AT_DEPOT;
                    isReturningToDepot = false;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;

            case DWELLING_AT_DEPOT:
                if (System.currentTimeMillis() - dwellStartTime > 1000) {
                    visitedPlatforms.clear();
                    currentPlatformIndex = 0;
                    isReversed = false;
                    movementState = Train.MovementState.SPAWNED;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;
        }
    }

    private boolean isApproachingPlatform(World world) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        double distanceToNextPlatform = getDistanceToNextPlatform(world);

        if (distanceToNextPlatform <= 0.0) {
            return false;
        }

        double stoppingDistance = (speed * speed) / (2.0 * accelerationConstant);
        double minApproachDistance = 2.0;

        return distanceToNextPlatform <= stoppingDistance && distanceToNextPlatform >= minApproachDistance;
    }

    private boolean hasMorePlatformsToVisit(World world) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        for (int i = currentPlatformIndex + 1; i < path.size(); i++) {
            String trackKey = path.get(i);
            TrackSegment segment = findTrackSegmentByKey(trackKey, world);

            if (segment != null && segment.isPlatform()) {
                String platformId = trackKey;
                if (!visitedPlatforms.contains(platformId)) {
                    if (segment.getType().equals("platform")) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    private void calculateTotalPathLength() {
        totalPathLength = 0.0;
        if (continuousPathPoints != null && continuousPathPoints.size() > 1) {
            for (int i = 0; i < continuousPathPoints.size() - 1; i++) {
                Vec3d current = continuousPathPoints.get(i);
                Vec3d next = continuousPathPoints.get(i + 1);
                totalPathLength += current.distanceTo(next);
            }
        }
    }

    public Vec3d getPositionAlongContinuousPath(double distance) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty()) {
            return new Vec3d(0, 0, 0);
        }

        if (distance <= 0) {
            return continuousPathPoints.get(0);
        }

        if (distance >= totalPathLength) {
            return continuousPathPoints.get(continuousPathPoints.size() - 1);
        }

        double accumulatedDistance = 0.0;
        for (int i = 0; i < continuousPathPoints.size() - 1; i++) {
            Vec3d current = continuousPathPoints.get(i);
            Vec3d next = continuousPathPoints.get(i + 1);
            double segmentLength = current.distanceTo(next);

            if (accumulatedDistance + segmentLength >= distance) {
                double segmentProgress = (distance - accumulatedDistance) / segmentLength;
                return current.lerp(next, segmentProgress);
            }

            accumulatedDistance += segmentLength;
        }

        return continuousPathPoints.get(continuousPathPoints.size() - 1);
    }

    public double getRotationAlongContinuousPath(double distance) {
        if (continuousPathPoints == null || continuousPathPoints.size() < 2) {
            return 0.0;
        }

        if (distance <= 0) {
            double rotation = getDirectionBetweenPoints(continuousPathPoints.get(0), continuousPathPoints.get(1));
            return rotation;
        }

        if (distance >= totalPathLength) {
            int lastIndex = continuousPathPoints.size() - 1;
            double rotation = getDirectionBetweenPoints(continuousPathPoints.get(lastIndex - 1), continuousPathPoints.get(lastIndex));
            return rotation;
        }

        double accumulatedDistance = 0.0;
        for (int i = 0; i < continuousPathPoints.size() - 1; i++) {
            Vec3d current = continuousPathPoints.get(i);
            Vec3d next = continuousPathPoints.get(i + 1);
            double segmentLength = current.distanceTo(next);

            if (accumulatedDistance + segmentLength >= distance) {
                double rotation = getDirectionBetweenPoints(current, next);
                return rotation;
            }

            accumulatedDistance += segmentLength;
        }

        int lastIndex = continuousPathPoints.size() - 1;
        return getDirectionBetweenPoints(continuousPathPoints.get(lastIndex - 1), continuousPathPoints.get(lastIndex));
    }

    private double getDirectionBetweenPoints(Vec3d from, Vec3d to) {
        return Math.atan2(to.x - from.x, to.z - from.z);
    }
}
