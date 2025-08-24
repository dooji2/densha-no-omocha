package com.dooji.dno.train;

import com.dooji.dno.network.TrainModClientNetworking;
import com.dooji.dno.network.payloads.BoardingResponsePayload;
import com.dooji.dno.network.payloads.BoardingSyncPayload;
import com.dooji.dno.network.payloads.PlayerPositionUpdatePayload;
import com.dooji.dno.network.payloads.RequestBoardingPayload;
import com.dooji.dno.network.payloads.RequestDisembarkPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrainBoardingManager {
    private static final Map<String, List<BoardingSyncPayload.BoardingData>> trainBoardingData = new HashMap<>();
    private static String currentBoardedTrainId = null;
    private static int currentBoardedCarriageIndex = -1;
    private static String currentBoardedCarriageId = null;
    private static Vec3d currentRelativePosition = null;
    private static boolean isWaitingForBoardingResponse = false;
    private static String currentBoardingTrainId = null;
    private static int currentBoardingCarriageIndex = -1;

    private static boolean prevAllowFlying = false;
    private static boolean prevFlying = false;
    private static boolean prevNoGravity = false;

    private static long lastDisembarkTime = 0;
    private static final long BOARDING_COOLDOWN_MS = 2000;

    private static long boardingRequestTime = 0;
    private static final long BOARDING_RESPONSE_TIMEOUT_MS = 5000;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BoardingResponsePayload.ID, TrainBoardingManager::handleBoardingResponse);
        ClientPlayNetworking.registerGlobalReceiver(BoardingSyncPayload.ID, TrainBoardingManager::handleBoardingSync);
        ClientTickEvents.END_CLIENT_TICK.register(TrainBoardingManager::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (currentBoardedTrainId == null) {
            checkForBoardingCollision();
        }
    }

    public static String getCurrentBoardedTrainId() { 
        return currentBoardedTrainId; 
    }
    
    public static int getCurrentBoardedCarriageIndex() {
        return currentBoardedCarriageIndex;
    }
    
    public static String getCurrentBoardedCarriageId() {
        return currentBoardedCarriageId;
    }
    
    public static Vec3d getCurrentRelativePosition() { 
        return currentRelativePosition; 
    }
    
    public static boolean isPlayerBoarded() { 
        return currentBoardedTrainId != null; 
    }
    
    public static void updateRelativePosition(Vec3d newPosition) {
        currentRelativePosition = newPosition;
    }

    private static void checkForBoardingCollision() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDisembarkTime < BOARDING_COOLDOWN_MS) {
            return;
        }
        
        if (isWaitingForBoardingResponse && (currentTime - boardingRequestTime > BOARDING_RESPONSE_TIMEOUT_MS)) {
            isWaitingForBoardingResponse = false;
            currentBoardingTrainId = null;
            currentBoardingCarriageIndex = -1;
        }
        
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        World world = player.getWorld();
        Map<String, TrainClient> trains = TrainManagerClient.getTrainsFor(world);
        if (trains == null) return;

        Box playerBox = player.getBoundingBox();

        for (Map.Entry<String, TrainClient> entry : trains.entrySet()) {
            String trainId = entry.getKey();
            TrainClient train = entry.getValue();

            if (train.getBoundingBoxWidths() == null || train.getBoundingBoxLengths() == null || train.getBoundingBoxHeights() == null) {
                continue;
            }

            List<Vec3d> pathPoints = train.getContinuousPathPoints();
            if (pathPoints == null || pathPoints.isEmpty()) {
                continue;
            }

            double trainPathDistance = train.getCurrentPathDistance();
            List<String> carriageIds = train.getCarriageIds();
            List<Double> carriageLengths = train.getBoundingBoxLengths();
            List<Double> carriageWidths = train.getBoundingBoxWidths();
            List<Double> carriageHeights = train.getBoundingBoxHeights();

            List<String> orderedCarriageIds = new ArrayList<>(carriageIds);
            if (train.isReversed()) {
                Collections.reverse(orderedCarriageIds);
            }

            double currentOffset = 0.0;
            for (int i = 0; i < orderedCarriageIds.size(); i++) {
                String carriageId = orderedCarriageIds.get(i);

                int originalIndex = train.isReversed() ? (carriageIds.size() - 1 - i) : i;

                if (originalIndex >= carriageLengths.size() || originalIndex >= carriageWidths.size() || originalIndex >= carriageHeights.size()) {
                    continue;
                }

                double carriageLength = carriageLengths.get(originalIndex);
                double carriageWidth = carriageWidths.get(originalIndex);
                double carriageHeight = carriageHeights.get(originalIndex);

                if (carriageLength <= 0 || carriageWidth <= 0 || carriageHeight <= 0) {
                    currentOffset += carriageLength;
                    continue;
                }

                TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(carriageId);
                double insetDist = 0.0;
                
                if (trainData != null) {
                    double inset = trainData.bogieInset();
                    insetDist = MathHelper.clamp(inset, 0.0, 0.49) * carriageLength;
                }

                double frontOffset = currentOffset + insetDist;
                double rearOffset = currentOffset + carriageLength - insetDist;

                Vec3d frontPos = train.getPositionAlongContinuousPath(trainPathDistance - frontOffset);
                Vec3d rearPos = train.getPositionAlongContinuousPath(trainPathDistance - rearOffset);

                if (frontPos != null && rearPos != null) {
                    if (trainData != null && trainData.model() != null && trainData.doors() != null && trainData.doors().hasDoors()) {
                        String modelPath = trainData.model().replace("densha-no-omocha:", "");
                        List<TrainDoorDetector.DoorBoundingBox> doorBoxes = TrainDoorDetector.detectDoors(modelPath, trainData.doors().doorParts());
                        
                        if (!doorBoxes.isEmpty()) {
                            List<Vec3d[]> doorWorldCorners = TrainDoorDetector.createDoorBoundingBoxes(doorBoxes, frontPos, rearPos, carriageWidth, carriageHeight, train.isReversed(), trainData.heightOffset());
                            
                            for (Vec3d[] corners : doorWorldCorners) {
                                Box doorBox = createBoxFromCorners(corners);
                                if (playerBox.intersects(doorBox)) {
                                    Vec3d collisionPoint = calculateCollisionPoint(playerBox, doorBox, frontPos, rearPos);
                                    Vec3d relativePos = convertWorldToRelativePosition(collisionPoint, frontPos, rearPos, carriageWidth, carriageHeight);
                                    
                                    currentRelativePosition = relativePos;
                                    currentBoardedCarriageId = carriageId;
                                    requestBoarding(trainId, originalIndex, relativePos.x, relativePos.y, relativePos.z);
                                    return;
                                }
                            }
                        }
                    }
                }
                
                currentOffset += carriageLength + 1.0;
            }
        }
    }



    private static Vec3d calculateCollisionPoint(Box playerBox, Box doorBox, Vec3d frontPos, Vec3d rearPos) {
        Vec3d playerCenter = new Vec3d(
            (playerBox.minX + playerBox.maxX) * 0.5,
            (playerBox.minY + playerBox.maxY) * 0.5,
            (playerBox.minZ + playerBox.maxZ) * 0.5
        );

        double clampedX = Math.max(doorBox.minX, Math.min(doorBox.maxX, playerCenter.x));
        double clampedY = Math.max(doorBox.minY, Math.min(doorBox.maxY, playerCenter.y));
        double clampedZ = Math.max(doorBox.minZ, Math.min(doorBox.maxZ, playerCenter.z));
        
        return new Vec3d(clampedX, clampedY, clampedZ);
    }

    private static Box createBoxFromCorners(Vec3d[] corners) {
        if (corners == null || corners.length != 8) {
            return new Box(0, 0, 0, 0, 0, 0);
        }
        
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        
        for (Vec3d corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3d convertWorldToRelativePosition(Vec3d worldPos, Vec3d frontPos, Vec3d rearPos, double width, double height) {
        Vec3d center = new Vec3d(
            (frontPos.x + rearPos.x) * 0.5,
            (frontPos.y + rearPos.y) * 0.5,
            (frontPos.z + rearPos.z) * 0.5
        );
        
        double dx = rearPos.x - frontPos.x;
        double dz = rearPos.z - frontPos.z;
        double carriageYaw = Math.atan2(dx, dz);

        Vec3d localPos = worldPos.subtract(center);

        double cosYaw = Math.cos(-carriageYaw);
        double sinYaw = Math.sin(-carriageYaw);
        
        double localX = localPos.x * cosYaw - localPos.z * sinYaw;
        double localZ = localPos.x * sinYaw + localPos.z * cosYaw;
        double localY = localPos.y;

        double relativeX = localX;
        double relativeY = localY;
        double relativeZ = localZ;
        
        return new Vec3d(relativeX, relativeY, relativeZ);
    }

    private static void requestBoarding(String trainId, int carriageIndex, double relativeX, double relativeY, double relativeZ) {
        if (isWaitingForBoardingResponse) {
            return;
        }
        
        isWaitingForBoardingResponse = true;
        currentBoardingTrainId = trainId;
        currentBoardingCarriageIndex = carriageIndex;
        boardingRequestTime = System.currentTimeMillis();
        
        RequestBoardingPayload payload = new RequestBoardingPayload(trainId, carriageIndex, relativeX, relativeY, relativeZ);
        
        try {
            TrainModClientNetworking.sendToServer(payload);
        } catch (Exception e) {
            isWaitingForBoardingResponse = false;
            currentBoardingTrainId = null;
            currentBoardingCarriageIndex = -1;
        }
    }

    public static void handleBoardingResponse(BoardingResponsePayload payload, ClientPlayNetworking.Context context) {
        if (!isWaitingForBoardingResponse) {
            return;
        }
        
        if (!payload.trainId().equals(currentBoardingTrainId) || payload.carriageIndex() != currentBoardingCarriageIndex) {
            return;
        }
        
        if (payload.approved()) {
            currentBoardedTrainId = payload.trainId();
            currentBoardedCarriageIndex = payload.carriageIndex();
            currentRelativePosition = new Vec3d(payload.relativeX(), payload.relativeY(), payload.relativeZ());
            
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                prevAllowFlying = player.getAbilities().allowFlying;
                prevFlying = player.getAbilities().flying;
                prevNoGravity = player.hasNoGravity();
                
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.setNoGravity(true);
            }
        }
        
        isWaitingForBoardingResponse = false;
        currentBoardingTrainId = null;
        currentBoardingCarriageIndex = -1;
    }

    private static void handleBoardingSync(BoardingSyncPayload payload, ClientPlayNetworking.Context context) {
        trainBoardingData.putAll(payload.trainBoardingData());
    }

    public static void requestDisembark() {
        if (currentBoardedTrainId == null) {
            return;
        }
        
        RequestDisembarkPayload payload = new RequestDisembarkPayload(currentBoardedTrainId);
        TrainModClientNetworking.sendToServer(payload);
        
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.getAbilities().allowFlying = prevAllowFlying;
            player.getAbilities().flying = prevFlying;
            player.setNoGravity(prevNoGravity);
        }
        
        currentBoardedTrainId = null;
        currentBoardedCarriageIndex = -1;
        currentRelativePosition = null;
        lastDisembarkTime = System.currentTimeMillis();
    }

    public static void sendPositionUpdateToServer() {
        if (currentBoardedTrainId == null || currentBoardedCarriageIndex == -1 || currentRelativePosition == null) {
            return;
        }
        
        PlayerPositionUpdatePayload positionPayload = new PlayerPositionUpdatePayload(
            currentBoardedTrainId, 
            currentBoardedCarriageIndex, 
            currentRelativePosition.x, 
            currentRelativePosition.y, 
            currentRelativePosition.z
        );
        
        TrainModClientNetworking.sendToServer(positionPayload);
    }

    public static void resetBoardingState() {
        currentBoardedTrainId = null;
        currentBoardedCarriageIndex = -1;
        currentBoardedCarriageId = null;
        currentRelativePosition = null;
        isWaitingForBoardingResponse = false;
        currentBoardingTrainId = null;
        currentBoardingCarriageIndex = -1;
        trainBoardingData.clear();

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.getAbilities().allowFlying = prevAllowFlying;
            player.getAbilities().flying = prevFlying;
            player.setNoGravity(prevNoGravity);
        }
    }
}
