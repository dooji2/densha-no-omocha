package com.dooji.dno.train;

import com.dooji.dno.TrainModClient;
import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.track.TrackSegment;
import com.dooji.renderix.ObjModel;
import com.dooji.renderix.Renderix;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.LightType;

import java.util.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

public class TrainRenderer {
    private static final Map<String, ObjModel> modelCache = new HashMap<>();
    private static final Map<String, Double> trainPathPrev = new HashMap<>();

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TrainRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        World world = client.player.getWorld();
        Map<String, TrainClient> trains = TrainManagerClient.getTrainsFor(world);

        if (trains.isEmpty()) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();

        Renderix.clearInstances();

        for (TrainClient train : trains.values()) {
            renderTrain(context, train, cameraPos, matrices, vertexConsumers, world);
        }

        Renderix.flushInstances(vertexConsumers);
    }

    private static double smoothPathDistance(TrainClient train, float tickDelta) {
        double prev = trainPathPrev.getOrDefault(train.getTrainId(), train.getCurrentPathDistance());
        double curr = train.getCurrentPathDistance();
        float alpha = MathHelper.clamp(tickDelta, 0.0f, 1.0f);
        double smoothed = prev + (curr - prev) * alpha;
        trainPathPrev.put(train.getTrainId(), smoothed);
        return smoothed;
    }

    private static void renderTrain(WorldRenderContext context, TrainClient train, Vec3d cameraPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, World world) {
        TrackSegment siding = findTrackSegmentForTrain(train);
        if (siding == null) {
            return;
        }

        List<String> carriageIds = train.getCarriageIds();
        if (carriageIds.isEmpty()) {
            return;
        }

        List<Vec3d> pathPoints = train.getContinuousPathPoints();
        if (pathPoints == null || pathPoints.isEmpty()) {
            renderTrainStatic(context, train, cameraPos, matrices, vertexConsumers, siding);
            return;
        }

        float ticksElapsed = MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks();
        train.simulateTrainClient(world, ticksElapsed);
        
        double trainPathDistance = smoothPathDistance(train, ticksElapsed);

        List<String> orderedCarriageIds = new ArrayList<>(carriageIds);
        if (train.isReversed()) {
            Collections.reverse(orderedCarriageIds);
        }

        double currentOffset = 0.0;

        for (int i = 0; i < orderedCarriageIds.size(); i++) {
            String carriageId = orderedCarriageIds.get(i);
            TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(carriageId);

            if (trainData == null) {
                continue;
            }

            double carriageLength = trainData.length();
            double inset = trainData.bogieInset();
            double insetDist = MathHelper.clamp(inset, 0.0, 0.49) * carriageLength;

            double frontOffset = currentOffset + insetDist;
            double rearOffset = currentOffset + carriageLength - insetDist;

            Vec3d frontPos = train.getPositionAlongContinuousPath(trainPathDistance - frontOffset);
            Vec3d rearPos = train.getPositionAlongContinuousPath(trainPathDistance - rearOffset);

            Vec3d carriageCenter = new Vec3d(
                (frontPos.x + rearPos.x) / 2.0,
                (frontPos.y + rearPos.y) / 2.0,
                (frontPos.z + rearPos.z) / 2.0
            );

            double dx = rearPos.x - frontPos.x;
            double dz = rearPos.z - frontPos.z;
            double dy = frontPos.y - rearPos.y;

            double horiz = Math.sqrt(dx * dx + dz * dz);
            double carriageYaw = Math.atan2(dx, dz);
            double carriagePitch = horiz > 1e-4 ? Math.atan2(dy, horiz) : 0.0;

            if (train.isReversed()) {
                carriageYaw += Math.PI;
                carriagePitch = -carriagePitch;
            }

            int originalIndex = train.isReversed() ? (carriageIds.size() - 1 - i) : i;
            String instanceKey = train.getTrainId() + "#" + originalIndex;

            renderCarriage(context, train, carriageCenter, carriageYaw, carriagePitch, cameraPos, matrices, vertexConsumers, trainData, originalIndex, carriageIds.size(), train.getDoorValue(), instanceKey);
            renderBogies(context, train, trainData, trainPathDistance, currentOffset, insetDist, carriageCenter, carriageYaw, cameraPos, matrices, vertexConsumers, originalIndex);

            currentOffset += carriageLength + 1.0;
        }
    }

    private static void renderTrainStatic(WorldRenderContext context, TrainClient train, Vec3d cameraPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, TrackSegment siding) {
        List<String> carriageIds = train.getCarriageIds();
        if (carriageIds.isEmpty()) {
            return;
        }

        double totalLength = 0;
        for (String carriageId : carriageIds) {
            TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(carriageId);
            if (trainData != null) {
                totalLength += trainData.length() + 1.0;
            }
        }

        if (totalLength > 0) {
            totalLength -= 1.0;
        }

        double trackLength = getTrackLength(siding);
        if (trackLength <= 0) {
            return;
        }

        double trainStartOffset = (trackLength - totalLength) / 2.0;
        if (trainStartOffset < 0) {
            trainStartOffset = 0;
        }

        double currentOffset = trainStartOffset;

        for (int i = 0; i < carriageIds.size(); i++) {
            String carriageId = carriageIds.get(i);
            TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(carriageId);

            if (trainData == null) {
                continue;
            }

            double carriageLength = trainData.length();
            double inset = trainData.bogieInset();
            double insetDist = MathHelper.clamp(inset, 0.0, 0.49) * carriageLength;

            double frontOffset = currentOffset + insetDist;
            double rearOffset = currentOffset + carriageLength - insetDist;

            Vec3d frontPos = getPositionAlongTrack(siding, frontOffset / trackLength);
            Vec3d rearPos = getPositionAlongTrack(siding, rearOffset / trackLength);

            Vec3d carriageCenter = new Vec3d(
                (frontPos.x + rearPos.x) / 2.0,
                (frontPos.y + rearPos.y) / 2.0,
                (frontPos.z + rearPos.z) / 2.0
            );

            double dx = rearPos.x - frontPos.x;
            double dz = rearPos.z - frontPos.z;
            double dy = frontPos.y - rearPos.y;

            double horiz = Math.sqrt(dx * dx + dz * dz);
            double carriageYaw = Math.atan2(dx, dz);
            double carriagePitch = horiz > 1e-4 ? Math.asin(MathHelper.clamp(dy / Math.sqrt(dx * dx + dy * dy + dz * dz), -1.0, 1.0)) : 0.0;

            if (carriagePitch > 0 && dz * Math.cos(carriageYaw) + dx * Math.sin(carriageYaw) < 0) {
                carriagePitch = -carriagePitch;
            }

            if (train.isReversed()) {
                carriageYaw += Math.PI;
            }

            String instanceKey = train.getTrainId() + "#" + i;
            renderCarriage(context, train, carriageCenter, carriageYaw, carriagePitch, cameraPos, matrices, vertexConsumers, trainData, i, carriageIds.size(), 0.0f, instanceKey);
            currentOffset += carriageLength + 1.0;
        }
    }

    private static double getTrackLength(TrackSegment siding) {
        return Math.sqrt(
            Math.pow(siding.end().getX() - siding.start().getX(), 2) +
            Math.pow(siding.end().getY() - siding.start().getY(), 2) +
            Math.pow(siding.end().getZ() - siding.start().getZ(), 2)
        );
    }

    private static Vec3d getPositionAlongTrack(TrackSegment siding, double progress) {
        double startX = siding.start().getX() + 0.5;
        double startY = siding.start().getY();
        double startZ = siding.start().getZ() + 0.5;
        double endX = siding.end().getX() + 0.5;
        double endY = siding.end().getY();
        double endZ = siding.end().getZ() + 0.5;

        return new Vec3d(
            startX + (endX - startX) * progress,
            startY + (endY - startY) * progress,
            startZ + (endZ - startZ) * progress
        );
    }

    private static void renderCarriage(WorldRenderContext context, TrainClient train, Vec3d position, double yaw, double pitch, Vec3d cameraPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, TrainConfigLoader.TrainTypeData trainData, int carriageIndex, int totalCarriages, float doorValue, String instanceKey) {
        String modelPath = trainData.model();
        if (modelPath == null || modelPath.isEmpty()) {
            return;
        }

        try {
            ObjModel model = getOrLoadModel(modelPath, trainData.flipV());
            if (model == null) {
                return;
            }

            matrices.push();

            final double renderYOffset = 0.75;
            matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y + renderYOffset, position.z - cameraPos.z);

            float yawDeg = (float) Math.toDegrees(yaw) + 180;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
            float pitchDeg = (float) Math.toDegrees(pitch);
            if (Math.abs(pitchDeg) > 0.0001f) {
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(pitchDeg));
            }

            if (trainData.isReversed()) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            }

            World world = MinecraftClient.getInstance().world;
            if (world != null) {
                BlockPos blockPos = BlockPos.ofFloored(position.x, position.y, position.z);
                int block = world.getLightLevel(LightType.BLOCK, blockPos);
                int sky = world.getDimension().hasSkyLight() ? world.getLightLevel(LightType.SKY, blockPos) : 0;
                int packedLight = (sky << 20) | (block << 4);
                int packedOverlay = OverlayTexture.DEFAULT_UV;

                TrainConfigLoader.DoorConfig doors = trainData.doors();
                Train.MovementState state = train.getMovementState();
                boolean interiorLit = state != null && state != Train.MovementState.WAITING_IN_DEPOT && state != Train.MovementState.DWELLING_AT_DEPOT && state != Train.MovementState.SPAWNED;
                
                Matrix4f transform = matrices.peek().getPositionMatrix();
                
                if (doors.hasDoors()) {
                    double doorWidth = doors.calculateDoorWidth(trainData.model());
                    double doorOffset = doorWidth * doorValue;
                    
                    Renderix.renderModelWithDoorAnimation(model, matrices, vertexConsumers, packedLight, packedOverlay, doorOffset, doors.slideDirection(), doors.doorParts(), instanceKey, interiorLit, trainData.interiorPart(), trainData.isReversed());
                } else {
                    Renderix.enqueueInstance(model, transform, packedLight, packedOverlay, 0xFFFFFFFF, interiorLit, trainData.interiorPart());
                }
            }

            matrices.pop();

        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to render train carriage: {}", modelPath, e);
        }
    }

    private static void renderBogies(WorldRenderContext context, TrainClient train, TrainConfigLoader.TrainTypeData data, double trainPathDistance, double currentOffset, double insetDist, Vec3d carriageCenter, double carriageYaw, Vec3d cameraPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int carriageIndex) {
        List<TrainConfigLoader.BogiePart> parts = data.bogies().bogieParts();
        if (parts == null || parts.isEmpty()) {
            return;
        }

        String frontModel = null;
        String rearModel = null;
        double frontInsetOverride = -1.0;
        double rearInsetOverride = -1.0;
        List<String> frontWheels = new ArrayList<>();
        List<String> rearWheels = new ArrayList<>();

        if (parts.size() == 1) {
            frontModel = parts.get(0).model();
            rearModel = parts.get(0).model();
            frontWheels = parts.get(0).wheels();
            rearWheels = parts.get(0).wheels();
        } else {
            for (TrainConfigLoader.BogiePart part : parts) {
                if ("FRONT".equalsIgnoreCase(part.position()) || "BEGINNING".equalsIgnoreCase(part.position())) {
                    frontModel = part.model();
                    frontWheels = part.wheels();
                    frontInsetOverride = part.inset();
                }
                
                if ("REAR".equalsIgnoreCase(part.position()) || "END".equalsIgnoreCase(part.position())) {
                    rearModel = part.model();
                    rearWheels = part.wheels();
                    rearInsetOverride = part.inset();
                }
            }
            
            if (frontModel == null) {
                frontModel = parts.get(0).model();
                frontWheels = parts.get(0).wheels();
            }
            
            if (rearModel == null) {
                rearModel = parts.get(parts.size() - 1).model();
                rearWheels = parts.get(parts.size() - 1).wheels();
            }
        }

        double actualFrontInset = frontInsetOverride >= 0.0 ? MathHelper.clamp(frontInsetOverride, 0.0, 0.49) * data.length() : insetDist;
        double actualRearInset = rearInsetOverride >= 0.0 ? MathHelper.clamp(rearInsetOverride, 0.0, 0.49) * data.length() : insetDist;

        double frontDist_forward = currentOffset + actualFrontInset;
        double rearDist_forward = currentOffset + data.length() - actualRearInset;
        double frontModelDist_reversed = currentOffset + data.length() - actualFrontInset;
        double rearModelDist_reversed = currentOffset + actualRearInset;

        if (train.isReversed()) {
            renderSingleBogie(frontModel, train, trainPathDistance, frontModelDist_reversed, carriageCenter, carriageYaw, cameraPos, matrices, vertexConsumers, carriageIndex, true, frontWheels);
            renderSingleBogie(rearModel, train, trainPathDistance, rearModelDist_reversed, carriageCenter, carriageYaw, cameraPos, matrices, vertexConsumers, carriageIndex, false, rearWheels);
        } else {
            renderSingleBogie(frontModel, train, trainPathDistance, frontDist_forward, carriageCenter, carriageYaw, cameraPos, matrices, vertexConsumers, carriageIndex, true, frontWheels);
            renderSingleBogie(rearModel, train, trainPathDistance, rearDist_forward, carriageCenter, carriageYaw, cameraPos, matrices, vertexConsumers, carriageIndex, false, rearWheels);
        }
    }

    private static void renderSingleBogie(String modelPath, TrainClient train, double trainPathDistance, double offset, Vec3d carriageCenter, double carriageYaw, Vec3d cameraPos, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int carriageIndex, boolean isFront, List<String> wheels) {
        if (modelPath == null || modelPath.isEmpty()) return;

        try {
            ObjModel model = getOrLoadModel(modelPath, false);
            if (model == null) return;

            matrices.push();

            Vec3d pos = train.getPositionAlongContinuousPath(trainPathDistance - offset);
            double yaw = train.getRotationAlongContinuousPath(trainPathDistance - offset);

            double renderYOffset = 0.8;
            matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y + renderYOffset, pos.z - cameraPos.z);
            float yawDeg = (float)Math.toDegrees(yaw) + 180f;
            if (train.isReversed()) yawDeg += 180f;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));

            World world = MinecraftClient.getInstance().world;
            if (world != null) {
                BlockPos blockPos = BlockPos.ofFloored(pos.x, pos.y, pos.z);
                int block = world.getLightLevel(LightType.BLOCK, blockPos);
                int sky = world.getDimension().hasSkyLight() ? world.getLightLevel(LightType.SKY, blockPos) : 0;
                int packedLight = (sky << 20) | (block << 4);
                int packedOverlay = OverlayTexture.DEFAULT_UV;

                Matrix4f transform = matrices.peek().getPositionMatrix();

                if (wheels != null && !wheels.isEmpty()) {
                    renderBogieWithWheels(model, train, trainPathDistance, offset, matrices, vertexConsumers, packedLight, packedOverlay, wheels);
                } else {
                    Renderix.enqueueInstance(model, transform, packedLight, packedOverlay, 0xFFFFFFFF, false, "interior");
                }
            }

            matrices.pop();
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to render bogie: {}", modelPath, e);
        }
    }

    private static void renderBogieWithWheels(ObjModel model, TrainClient train, double trainPathDistance, double offset, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int packedLight, int packedOverlay, List<String> wheelNames) {
        try {
            double wheelRotation = 0.0;
            
            double wheelCircumference = 0.5;
            double distanceTraveled = trainPathDistance - offset;
            wheelRotation = (distanceTraveled / wheelCircumference) * 360.0;

            Map<String, ObjModel.RenderData> meshes = model.getMeshes();
            
            for (Map.Entry<String, ObjModel.RenderData> entry : meshes.entrySet()) {
                String meshName = entry.getKey();
                ObjModel.RenderData meshData = entry.getValue();
                
                boolean isWheel = false;
                for (String wheelName : wheelNames) {
                    if (meshName.toLowerCase().contains(wheelName.toLowerCase())) {
                        isWheel = true;
                        break;
                    }
                }
                
                if (isWheel) {
                    matrices.push();
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)wheelRotation));
                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    ObjModel.Material material = model.getMaterialForMesh(meshName);
                    Identifier texture = (material != null && material.texture != null) ? material.texture : Identifier.of("minecraft", "textures/block/stone.png");
                    
                    VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
                    Renderix.renderMesh(meshData, vertexConsumer, matrix, packedLight, packedOverlay);
                    matrices.pop();
                } else {
                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    ObjModel.Material material = model.getMaterialForMesh(meshName);
                    Identifier texture = (material != null && material.texture != null) ? material.texture : Identifier.of("minecraft", "textures/block/stone.png");

                    VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
                    Renderix.renderMesh(meshData, vertexConsumer, matrix, packedLight, packedOverlay);
                }
            }
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to render bogie with wheels", e);
            Matrix4f transform = matrices.peek().getPositionMatrix();
            Renderix.enqueueInstance(model, transform, packedLight, packedOverlay, 0xFFFFFFFF, false, "interior");
        }
    }

    private static ObjModel getOrLoadModel(String modelPath, boolean flipV) {
        return modelCache.computeIfAbsent(modelPath, path -> {
            try {
                String namespace = TrainModClient.MOD_ID;
                String modelPathOnly = path;

                if (path.contains(":")) {
                    String[] parts = path.split(":", 2);
                    namespace = parts[0];
                    modelPathOnly = parts[1];
                }

                return Renderix.loadModel(namespace, modelPathOnly, flipV);
            } catch (Exception e) {
                TrainModClient.LOGGER.error("Failed to load train model: {}", path, e);
                return null;
            }
        });
    }

    private static TrackSegment findTrackSegmentForTrain(TrainClient train) {
        String trackSegmentKey = train.getTrackSegmentKey();
        if (trackSegmentKey == null) {
            return null;
        }

        try {
            String[] parts = trackSegmentKey.split("->");
            if (parts.length != 2) {
                return null;
            }

            String[] startParts = parts[0].split(",");
            String[] endParts = parts[1].split(",");

            if (startParts.length != 3 || endParts.length != 3) {
                return null;
            }

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

            Map<String, TrackSegment> allTracks = TrackManagerClient.getTracksFor(MinecraftClient.getInstance().world);
            for (TrackSegment segment : allTracks.values()) {
                if (segment.start().equals(start) && segment.end().equals(end)) {
                    return segment;
                }
            }
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to parse track segment key: {}", trackSegmentKey, e);
        }

        return null;
    }

    public static void clearCaches() {
        modelCache.clear();
    }
}
