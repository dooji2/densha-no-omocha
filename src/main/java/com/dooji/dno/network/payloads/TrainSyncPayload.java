package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record TrainSyncPayload(String dimensionKey, Map<String, TrainData> trains) implements CustomPayload {
    public record TrainData(
        String trainId,
        List<String> carriageIds,
        boolean isReversed,
        String trackSegmentKey,
        Double speed,
        Double maxSpeed,
        Double accelerationConstant,
        String movementState,
        Boolean isReturningToDepot,
        Long dwellStartTime,
        String currentPlatformId,
        List<String> visitedPlatforms,
        Double currentPathDistance,
        List<Vec3d> continuousPathPoints,
        List<String> path,
        Integer currentPlatformIndex,
        Float doorValue,
        Boolean doorTarget,
        Double totalPathLength,
        Boolean movingForward
    ) {}
    public static final CustomPayload.Id<TrainSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "train_sync"));
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    public static final PacketCodec<PacketByteBuf, TrainSyncPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            buf.writeString(payload.dimensionKey);
            buf.writeInt(payload.trains.size());
            for (Map.Entry<String, TrainData> entry : payload.trains.entrySet()) {
                buf.writeString(entry.getKey());
                writeTrainData(buf, entry.getValue());
            }
        },
        buf -> {
            String dimensionKey = buf.readString();
            int trainCount = buf.readInt();
            Map<String, TrainData> trains = new java.util.HashMap<>();
            for (int i = 0; i < trainCount; i++) {
                String trainId = buf.readString();
                TrainData trainData = readTrainData(buf);
                trains.put(trainId, trainData);
            }
            return new TrainSyncPayload(dimensionKey, trains);
        }
    );
    
    private static void writeTrainData(PacketByteBuf buf, TrainData trainData) {
        buf.writeString(trainData.trainId());
        buf.writeBoolean(trainData.isReversed());
        if (trainData.trackSegmentKey() != null) {
            buf.writeBoolean(true);
            buf.writeString(trainData.trackSegmentKey());
        } else {
            buf.writeBoolean(false);
        }

        List<String> carriageIds = trainData.carriageIds();
        buf.writeInt(carriageIds.size());
        for (String carriageId : carriageIds) {
            buf.writeString(carriageId);
        }

        buf.writeBoolean(trainData.speed() != null);
        if (trainData.speed() != null) {
            buf.writeDouble(trainData.speed());
        }
        
        buf.writeBoolean(trainData.maxSpeed() != null);
        if (trainData.maxSpeed() != null) {
            buf.writeDouble(trainData.maxSpeed());
        }
        
        buf.writeBoolean(trainData.accelerationConstant() != null);
        if (trainData.accelerationConstant() != null) {
            buf.writeDouble(trainData.accelerationConstant());
        }
        
        buf.writeBoolean(trainData.movementState() != null);
        if (trainData.movementState() != null) {
            buf.writeString(trainData.movementState());
        }
        
        buf.writeBoolean(trainData.isReturningToDepot() != null);
        if (trainData.isReturningToDepot() != null) {
            buf.writeBoolean(trainData.isReturningToDepot());
        }
        
        buf.writeBoolean(trainData.dwellStartTime() != null);
        if (trainData.dwellStartTime() != null) {
            buf.writeLong(trainData.dwellStartTime());
        }
        
        buf.writeBoolean(trainData.currentPlatformId() != null);
        if (trainData.currentPlatformId() != null) {
            buf.writeString(trainData.currentPlatformId());
        }

        List<String> visitedPlatforms = trainData.visitedPlatforms();
        if (visitedPlatforms != null) {
            buf.writeBoolean(true);
            buf.writeInt(visitedPlatforms.size());
            for (String platformId : visitedPlatforms) {
                buf.writeString(platformId);
            }
        } else {
            buf.writeBoolean(false);
        }
        
        buf.writeBoolean(trainData.currentPathDistance() != null);
        if (trainData.currentPathDistance() != null) {
            buf.writeDouble(trainData.currentPathDistance());
        }

        List<Vec3d> continuousPathPoints = trainData.continuousPathPoints();
        if (continuousPathPoints != null) {
            buf.writeBoolean(true);
            buf.writeInt(continuousPathPoints.size());
            for (Vec3d point : continuousPathPoints) {
                buf.writeDouble(point.x);
                buf.writeDouble(point.y);
                buf.writeDouble(point.z);
            }
        } else {
            buf.writeBoolean(false);
        }

        List<String> path = trainData.path();
        if (path != null) {
            buf.writeBoolean(true);
            buf.writeInt(path.size());
            for (String pathKey : path) {
                buf.writeString(pathKey);
            }
        } else {
            buf.writeBoolean(false);
        }

        Integer currentPlatformIndex = trainData.currentPlatformIndex();
        if (currentPlatformIndex != null) {
            buf.writeBoolean(true);
            buf.writeInt(currentPlatformIndex);
        } else {
            buf.writeBoolean(false);
        }

        buf.writeBoolean(trainData.doorValue() != null);
        if (trainData.doorValue() != null) {
            buf.writeFloat(trainData.doorValue());
        }
        
        buf.writeBoolean(trainData.doorTarget() != null);
        if (trainData.doorTarget() != null) {
            buf.writeBoolean(trainData.doorTarget());
        }
        
        buf.writeBoolean(trainData.totalPathLength() != null);
        if (trainData.totalPathLength() != null) {
            buf.writeDouble(trainData.totalPathLength());
        }
        
        buf.writeBoolean(trainData.movingForward() != null);
        if (trainData.movingForward() != null) {
            buf.writeBoolean(trainData.movingForward());
        }
    }
    
    private static TrainData readTrainData(PacketByteBuf buf) {
        String trainId = buf.readString();
        boolean isReversed = buf.readBoolean();
        String trackSegmentKey = null;
        if (buf.readBoolean()) {
            trackSegmentKey = buf.readString();
        }

        int carriageCount = buf.readInt();
        List<String> carriageIds = new ArrayList<>();
        for (int i = 0; i < carriageCount; i++) {
            carriageIds.add(buf.readString());
        }

        Double speed = null;
        if (buf.readBoolean()) {
            speed = buf.readDouble();
        }
        
        Double maxSpeed = null;
        if (buf.readBoolean()) {
            maxSpeed = buf.readDouble();
        }
        
        Double accelerationConstant = null;
        if (buf.readBoolean()) {
            accelerationConstant = buf.readDouble();
        }
        
        String movementState = null;
        if (buf.readBoolean()) {
            movementState = buf.readString();
        }
        
        Boolean isReturningToDepot = null;
        if (buf.readBoolean()) {
            isReturningToDepot = buf.readBoolean();
        }
        
        Long dwellStartTime = null;
        if (buf.readBoolean()) {
            dwellStartTime = buf.readLong();
        }
        
        String currentPlatformId = null;
        if (buf.readBoolean()) {
            currentPlatformId = buf.readString();
        }

        List<String> visitedPlatforms = null;
        if (buf.readBoolean()) {
            int platformCount = buf.readInt();
            visitedPlatforms = new ArrayList<>();
            for (int i = 0; i < platformCount; i++) {
                visitedPlatforms.add(buf.readString());
            }
        }
        
        Double currentPathDistance = null;
        if (buf.readBoolean()) {
            currentPathDistance = buf.readDouble();
        }

        List<Vec3d> continuousPathPoints = null;
        if (buf.readBoolean()) {
            int pointCount = buf.readInt();
            continuousPathPoints = new ArrayList<>();
            for (int i = 0; i < pointCount; i++) {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                continuousPathPoints.add(new Vec3d(x, y, z));
            }
        }

        List<String> path = null;
        if (buf.readBoolean()) {
            int pathCount = buf.readInt();
            path = new ArrayList<>();
            for (int i = 0; i < pathCount; i++) {
                path.add(buf.readString());
            }
        }

        Integer currentPlatformIndex = null;
        if (buf.readBoolean()) {
            currentPlatformIndex = buf.readInt();
        }

        Float doorValue = null;
        if (buf.readBoolean()) {
            doorValue = buf.readFloat();
        }
        
        Boolean doorTarget = null;
        if (buf.readBoolean()) {
            doorTarget = buf.readBoolean();
        }
        
        Double totalPathLength = null;
        if (buf.readBoolean()) {
            totalPathLength = buf.readDouble();
        }
        
        Boolean movingForward = null;
        if (buf.readBoolean()) {
            movingForward = buf.readBoolean();
        }
        
        return new TrainData(trainId, carriageIds, isReversed, trackSegmentKey, 
           speed, maxSpeed, accelerationConstant, movementState,
           isReturningToDepot, dwellStartTime, currentPlatformId,
           visitedPlatforms, currentPathDistance, continuousPathPoints,
           path, currentPlatformIndex, doorValue, doorTarget, totalPathLength, movingForward);
    }
}
