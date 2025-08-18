package com.dooji.dno.train;

import com.dooji.dno.track.TrackManager;
import com.dooji.dno.track.TrackSegment;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class Train {
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
    private MovementState movementState;
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
    private Map<String, BoardingData> boardedPlayers;

    public enum MovementState {
        WAITING_IN_DEPOT,
        ACCELERATING,
        CRUISING,
        APPROACHING_PLATFORM,
        DWELLING_AT_PLATFORM,
        RETURNING_TO_DEPOT,
        DWELLING_AT_DEPOT,
        SPAWNED
    }
    
    public record BoardingData(int carriageIndex, double relativeX, double relativeY, double relativeZ) {}

    public Train(String trainId) {
        this.trainId = trainId;
        this.carriageIds = new ArrayList<>();
        this.isReversed = false;
        this.trackSegmentKey = null;
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
        this.movementState = MovementState.SPAWNED;
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
        this.boardedPlayers = new HashMap<>();
    }

    public Train(String trainId, List<String> carriageIds, boolean isReversed) {
        this(trainId);
        this.carriageIds = new ArrayList<>(carriageIds);
        this.isReversed = isReversed;
    }

    public Train(String trainId, List<String> carriageIds, boolean isReversed, String trackSegmentKey) {
        this(trainId, carriageIds, isReversed);
        this.trackSegmentKey = trackSegmentKey;
    }

    public String getTrainId() {
        return trainId;
    }

    public List<String> getCarriageIds() {
        return new ArrayList<>(carriageIds);
    }

    public void setCarriageIds(List<String> carriageIds) {
        this.carriageIds = new ArrayList<>(carriageIds);
    }

    public void addCarriage(String carriageId) {
        carriageIds.add(carriageId);
    }

    public void removeCarriage(int index) {
        if (index >= 0 && index < carriageIds.size()) {
            carriageIds.remove(index);
        }
    }

    public boolean isReversed() {
        return isReversed;
    }

    public void setReversed(boolean reversed) {
        this.isReversed = reversed;
    }

    public int getCarriageCount() {
        return carriageIds.size();
    }

    public String getTrackSegmentKey() {
        return trackSegmentKey;
    }

    public void setTrackSegmentKey(String trackSegmentKey) {
        this.trackSegmentKey = trackSegmentKey;
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

    public MovementState getMovementState() {
        return movementState;
    }

    public void setMovementState(MovementState movementState) {
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

    public double getTotalTrainLength() {
        if (carriageLengths != null && !carriageLengths.isEmpty()) {
            double sum = 0.0;
            for (double l : carriageLengths) sum += Math.max(0.0, l);
            final double JACOBS_REDUCTION_PER_PAIR = 1.0;
            if (carriageIds != null && carriageIds.size() >= 2 && carriageIds.size() == carriageLengths.size()) {
                for (int i = 0; i < carriageIds.size() - 1; i++) {
                    String a = carriageIds.get(i);
                    String b = carriageIds.get(i + 1);
                    if (a != null && a.equals(b)) {
                        sum -= JACOBS_REDUCTION_PER_PAIR;
                    }
                }
            }

            return Math.max(0.0, sum);
        }

        return Math.max(1, carriageIds != null ? carriageIds.size() : 1) * 26.0;
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
    
    public Map<String, BoardingData> getBoardedPlayers() {
        return new HashMap<>(boardedPlayers);
    }
    
    public void addBoardedPlayer(String playerId, int carriageIndex, double relativeX, double relativeY, double relativeZ) {
        boardedPlayers.put(playerId, new BoardingData(carriageIndex, relativeX, relativeY, relativeZ));
    }
    
    public void removeBoardedPlayer(String playerId) {
        boardedPlayers.remove(playerId);
    }
    
    public boolean isPlayerBoarded(String playerId) {
        return boardedPlayers.containsKey(playerId);
    }
    
    public BoardingData getPlayerBoardingData(String playerId) {
        return boardedPlayers.get(playerId);
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

    public void updateMovement(World world) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0;
        lastUpdateTime = currentTime;

        float ticksElapsed = (float) (deltaTime * 20.0);

        updateMovementLogic(ticksElapsed, world);
    }

    public void updateServerMovement(World world) {
        if (continuousPathPoints == null || continuousPathPoints.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0;
        lastUpdateTime = currentTime;

        float ticksElapsed = (float) (deltaTime * 20.0);

        updateMovementLogic(ticksElapsed, world);
    }

    private void updateMovementLogic(float ticksElapsed, World world) {
        switch (movementState) {
            case SPAWNED:
                if (System.currentTimeMillis() - dwellStartTime > 1000) {
                    movementState = MovementState.WAITING_IN_DEPOT;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;

            case WAITING_IN_DEPOT:
                if (System.currentTimeMillis() - dwellStartTime > 10000) {
                    movementState = MovementState.ACCELERATING;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;

            case ACCELERATING:
                double currentSpeedLimit = getCurrentTrackSpeedLimit(world);
                double acceleration = accelerationConstant * ticksElapsed;
                speed = Math.min(speed + acceleration, currentSpeedLimit);

                if (speed >= currentSpeedLimit) {
                    movementState = MovementState.CRUISING;
                }

                if (isApproachingPlatform(world)) {
                    movementState = MovementState.APPROACHING_PLATFORM;
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
                    movementState = MovementState.APPROACHING_PLATFORM;
                } else if (currentPathDistance >= totalPathLength && !hasMorePlatformsToVisit(world)) {
                    movementState = MovementState.RETURNING_TO_DEPOT;
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
                        movementState = MovementState.DWELLING_AT_PLATFORM;
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
                long dwellTicks = (long)(dwellTime / 50.0);

                int platformDwellTime = getDwellTimeForCurrentPlatform(world);
                long platformDwellTicks = platformDwellTime * 20;

                if (dwellTicks < 60) {
                    doorTarget = false;
                } else if (dwellTicks < platformDwellTicks - 60) {
                    doorTarget = true;
                } else {
                    doorTarget = false;
                }

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

                        movementState = MovementState.ACCELERATING;
                    } else {
                        movementState = MovementState.RETURNING_TO_DEPOT;
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
                    movementState = MovementState.DWELLING_AT_DEPOT;
                    isReturningToDepot = false;
                    dwellStartTime = System.currentTimeMillis();
                }
                break;

            case DWELLING_AT_DEPOT:
                if (System.currentTimeMillis() - dwellStartTime > 1000) {
                    visitedPlatforms.clear();
                    currentPlatformIndex = 0;
                    isReversed = false;
                    movementState = MovementState.SPAWNED;
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

    private TrackSegment findTrackSegmentByKey(String trackKey, World world) {
        if (world == null) {
            return null;
        }

        Map<String, TrackSegment> tracks = TrackManager.getTracksFor(world);
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

    private double getDistanceAlongPathTo(BlockPos pos) {
        int idx = getNearestPathIndexTo(pos);
        return getCumulativeDistanceAtIndex(idx);
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

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("trainId", trainId);
        tag.putBoolean("isReversed", isReversed);

        if (trackSegmentKey != null) {
            tag.putString("trackSegmentKey", trackSegmentKey);
        }

        NbtCompound carriageIdsTag = new NbtCompound();
        for (int i = 0; i < carriageIds.size(); i++) {
            carriageIdsTag.putString("carriage_" + i, carriageIds.get(i));
        }

        tag.put("carriageIds", carriageIdsTag);

        tag.putDouble("currentPathDistance", currentPathDistance);
        if (continuousPathPoints != null) {
            NbtCompound continuousPathTag = new NbtCompound();
            for (int i = 0; i < continuousPathPoints.size(); i++) {
                Vec3d point = continuousPathPoints.get(i);
                NbtCompound pointTag = new NbtCompound();
                pointTag.putDouble("x", point.x);
                pointTag.putDouble("y", point.y);
                pointTag.putDouble("z", point.z);
                continuousPathTag.put("point_" + i, pointTag);
            }

            tag.put("continuousPathPoints", continuousPathTag);
        }

        tag.putDouble("speed", speed);
        tag.putDouble("maxSpeed", maxSpeed);
        tag.putDouble("accelerationConstant", accelerationConstant);
        tag.putLong("lastUpdateTime", lastUpdateTime);
        tag.putBoolean("movingForward", movingForward);
        tag.putDouble("depotPathDistance", depotPathDistance);

        tag.putString("movementState", movementState.name());
        tag.putBoolean("isReturningToDepot", isReturningToDepot);
        tag.putLong("dwellStartTime", dwellStartTime);

        if (currentPlatformId != null) {
            tag.putString("currentPlatformId", currentPlatformId);
        }

        NbtCompound visitedPlatformsTag = new NbtCompound();
        int platformIndex = 0;
        for (String platformId : visitedPlatforms) {
            visitedPlatformsTag.putString("platform_" + platformIndex, platformId);
            platformIndex++;
        }

        tag.put("visitedPlatforms", visitedPlatformsTag);

        if (path != null) {
            NbtCompound pathTag = new NbtCompound();
            for (int i = 0; i < path.size(); i++) {
                pathTag.putString("path_" + i, path.get(i));
            }

            tag.put("path", pathTag);
        }

        tag.putInt("currentPlatformIndex", currentPlatformIndex);
        tag.putFloat("doorValue", doorValue);
        tag.putBoolean("doorTarget", doorTarget);

        if (carriageLengths != null && !carriageLengths.isEmpty()) {
            NbtList lengthList = new NbtList();
            for (int i = 0; i < carriageLengths.size(); i++) {
                NbtCompound len = new NbtCompound();
                len.putDouble("v", carriageLengths.get(i));
                lengthList.add(len);
            }

            tag.put("carriageLengths", lengthList);
        }

        if (bogieInsets != null && !bogieInsets.isEmpty()) {
            NbtList insetList = new NbtList();
            for (int i = 0; i < bogieInsets.size(); i++) {
                NbtCompound el = new NbtCompound();
                el.putDouble("v", bogieInsets.get(i));
                insetList.add(el);
            }

            tag.put("bogieInsets", insetList);
        }

        if (boundingBoxWidths != null && !boundingBoxWidths.isEmpty()) {
            NbtList widthList = new NbtList();
            for (int i = 0; i < boundingBoxWidths.size(); i++) {
                NbtCompound width = new NbtCompound();
                width.putDouble("v", boundingBoxWidths.get(i));
                widthList.add(width);
            }

            tag.put("boundingBoxWidths", widthList);
        }

        if (boundingBoxLengths != null && !boundingBoxLengths.isEmpty()) {
            NbtList lengthList = new NbtList();
            for (int i = 0; i < boundingBoxLengths.size(); i++) {
                NbtCompound length = new NbtCompound();
                length.putDouble("v", boundingBoxLengths.get(i));
                lengthList.add(length);
            }

            tag.put("boundingBoxLengths", lengthList);
        }

        if (boundingBoxHeights != null && !boundingBoxHeights.isEmpty()) {
            NbtList heightList = new NbtList();
            for (int i = 0; i < boundingBoxHeights.size(); i++) {
                NbtCompound height = new NbtCompound();
                height.putDouble("v", boundingBoxHeights.get(i));
                heightList.add(height);
            }

            tag.put("boundingBoxHeights", heightList);
        }

        if (boardedPlayers != null && !boardedPlayers.isEmpty()) {
            NbtCompound boardedPlayersTag = new NbtCompound();
            for (Map.Entry<String, BoardingData> entry : boardedPlayers.entrySet()) {
                String playerId = entry.getKey();
                BoardingData data = entry.getValue();
                NbtCompound playerTag = new NbtCompound();
                playerTag.putInt("carriageIndex", data.carriageIndex());
                playerTag.putDouble("relativeX", data.relativeX());
                playerTag.putDouble("relativeY", data.relativeY());
                playerTag.putDouble("relativeZ", data.relativeZ());
                boardedPlayersTag.put(playerId, playerTag);
            }
            tag.put("boardedPlayers", boardedPlayersTag);
        }

        return tag;
    }

    public static Train fromNbt(NbtCompound tag) {
        String trainId = tag.getString("trainId").orElse("");
        boolean isReversed = tag.getBoolean("isReversed").orElse(false);
        String trackSegmentKey = tag.getString("trackSegmentKey").orElse(null);

        List<String> carriageIds = new ArrayList<>();
        NbtCompound carriageIdsTag = tag.getCompound("carriageIds").orElse(new NbtCompound());
        int pointIndex = 0;
        while (carriageIdsTag.contains("carriage_" + pointIndex)) {
            carriageIds.add(carriageIdsTag.getString("carriage_" + pointIndex).orElse(""));
            pointIndex++;
        }

        Train train = new Train(trainId, carriageIds, isReversed, trackSegmentKey);
        train.currentPathDistance = tag.getDouble("currentPathDistance").orElse(0.0);

        NbtCompound continuousPathTag = tag.getCompound("continuousPathPoints").orElse(new NbtCompound());
        List<Vec3d> continuousPoints = new ArrayList<>();
        pointIndex = 0;
        while (continuousPathTag.contains("point_" + pointIndex)) {
            NbtCompound pointTag = continuousPathTag.getCompound("point_" + pointIndex).orElse(new NbtCompound());
            double x = pointTag.getDouble("x").orElse(0.0);
            double y = pointTag.getDouble("y").orElse(0.0);
            double z = pointTag.getDouble("z").orElse(0.0);
            continuousPoints.add(new Vec3d(x, y, z));
            pointIndex++;
        }

        train.setContinuousPathPoints(continuousPoints);

        train.speed = tag.getDouble("speed").orElse(0.0);
        train.maxSpeed = tag.getDouble("maxSpeed").orElse(1.67);
        train.accelerationConstant = tag.getDouble("accelerationConstant").orElse(0.023);
        train.lastUpdateTime = System.currentTimeMillis();
        train.movingForward = tag.getBoolean("movingForward").orElse(true);
        train.depotPathDistance = tag.getDouble("depotPathDistance").orElse(0.0);

        String movementStateName = tag.getString("movementState").orElse(MovementState.SPAWNED.name());
        train.movementState = MovementState.valueOf(movementStateName);

        train.isReturningToDepot = tag.getBoolean("isReturningToDepot").orElse(false);
        train.dwellStartTime = tag.getLong("dwellStartTime").orElse(0L);
        train.currentPlatformId = tag.getString("currentPlatformId").orElse(null);

        Set<String> visitedPlatforms = new HashSet<>();
        NbtCompound visitedPlatformsTag = tag.getCompound("visitedPlatforms").orElse(new NbtCompound());
        int platformIndex = 0;
        
        while (visitedPlatformsTag.contains("platform_" + platformIndex)) {
            visitedPlatforms.add(visitedPlatformsTag.getString("platform_" + platformIndex).orElse(""));
            platformIndex++;
        }

        train.setVisitedPlatforms(visitedPlatforms);

        NbtCompound pathTag = tag.getCompound("path").orElse(new NbtCompound());
        List<String> loadedPath = new ArrayList<>();
        pointIndex = 0;

        while (pathTag.contains("path_" + pointIndex)) {
            loadedPath.add(pathTag.getString("path_" + pointIndex).orElse(""));
            pointIndex++;
        }

        train.setPath(loadedPath);
        train.currentPlatformIndex = tag.getInt("currentPlatformIndex").orElse(0);

        train.doorValue = tag.getFloat("doorValue").orElse(0.0f);
        train.doorTarget = tag.getBoolean("doorTarget").orElse(false);

        train.carriageLengths = new ArrayList<>();
        NbtList lenList = tag.getListOrEmpty("carriageLengths");
        for (int i = 0; i < lenList.size(); i++) {
            NbtCompound len = lenList.getCompound(i).orElse(new NbtCompound());
            train.carriageLengths.add(len.getDouble("v").orElse(0.0));
        }

        train.bogieInsets = new ArrayList<>();
        NbtList insetList = tag.getListOrEmpty("bogieInsets");
        for (int i = 0; i < insetList.size(); i++) {
            NbtCompound el = insetList.getCompound(i).orElse(new NbtCompound());
            train.bogieInsets.add(el.getDouble("v").orElse(0.1));
        }

        train.boundingBoxWidths = new ArrayList<>();
        NbtList widthList = tag.getListOrEmpty("boundingBoxWidths");
        for (int i = 0; i < widthList.size(); i++) {
            NbtCompound width = widthList.getCompound(i).orElse(new NbtCompound());
            train.boundingBoxWidths.add(width.getDouble("v").orElse(1.0));
        }

        train.boundingBoxLengths = new ArrayList<>();
        NbtList lengthList = tag.getListOrEmpty("boundingBoxLengths");
        for (int i = 0; i < lengthList.size(); i++) {
            NbtCompound length = lengthList.getCompound(i).orElse(new NbtCompound());
            train.boundingBoxLengths.add(length.getDouble("v").orElse(1.0));
        }

        train.boundingBoxHeights = new ArrayList<>();
        NbtList heightList = tag.getListOrEmpty("boundingBoxHeights");
        for (int i = 0; i < heightList.size(); i++) {
            NbtCompound height = heightList.getCompound(i).orElse(new NbtCompound());
            train.boundingBoxHeights.add(height.getDouble("v").orElse(1.0));
        }

        train.boardedPlayers = new HashMap<>();
        NbtCompound boardedPlayersTag = tag.getCompound("boardedPlayers").orElse(new NbtCompound());
        for (String playerId : boardedPlayersTag.getKeys()) {
            NbtCompound playerData = boardedPlayersTag.getCompound(playerId).orElse(new NbtCompound());
            int carriageIndex = playerData.getInt("carriageIndex").orElse(0);
            double relativeX = playerData.getDouble("relativeX").orElse(0.0);
            double relativeY = playerData.getDouble("relativeY").orElse(0.0);
            double relativeZ = playerData.getDouble("relativeZ").orElse(0.0);
            train.boardedPlayers.put(playerId, new BoardingData(carriageIndex, relativeX, relativeY, relativeZ));
        }

        return train;
    }
}

