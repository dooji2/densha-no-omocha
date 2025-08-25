package com.dooji.dno.track;

import com.dooji.dno.TrainMod;
import com.dooji.dno.TrainModClient;
import com.dooji.renderix.ObjModel;
import com.dooji.renderix.Renderix;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

public class TrackRenderer {
    private static final Map<String, TrackConfigLoader.TrackTypeData> trackTypes = new HashMap<>();
    private static final Map<String, ObjModel> modelCache = new HashMap<>();

    public static void init() {
        WorldRenderEvents.BEFORE_ENTITIES.register(TrackRenderer::renderTracks);
    }

    public static void clearCaches() {
        trackTypes.clear();
        modelCache.clear();
    }

    private static void renderTracks(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();
        MinecraftClient client = MinecraftClient.getInstance();

        World world = client.world;
        if (world == null || client.player == null) return;
        Vec3d cameraPosition = client.gameRenderer.getCamera().getPos();

        if (trackTypes.isEmpty()) loadTrackTypes(client.getResourceManager());
        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(world);

        if (tracks.isEmpty()) return;
        Renderix.clearInstances();

        List<TrackSegment> segments = new ArrayList<>(tracks.values());
        for (TrackSegment segment : segments) renderTrackSegment(segment, matrices, vertexConsumers, cameraPosition);

        Renderix.flushInstances(vertexConsumers);
    }

    private static void loadTrackTypes(ResourceManager resourceManager) {
        trackTypes.clear();
        TrackConfigLoader.loadTrackTypes(resourceManager);
        trackTypes.putAll(TrackConfigLoader.getAllTrackTypes());
    }

    private static void renderTrackSegment(TrackSegment segment, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Vec3d cameraPosition) {
        TrackConfigLoader.TrackTypeData trackType = trackTypes.get(segment.getModelId());
        if (trackType == null) return;

        if (trackType.model() == null || trackType.model().isEmpty()) return;
        ObjModel model = getOrLoadModel(trackType.model(), trackType.flipV());
        if (model == null) return;

        renderSimpleTrack(segment, model, matrices, vertexConsumers, cameraPosition, trackType);
    }

    private static void renderSimpleTrack(TrackSegment segment, ObjModel model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Vec3d cameraPosition, TrackConfigLoader.TrackTypeData trackType) {
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;

        if (world == null) return;
        
        double startAngle = Math.atan2(segment.startDirection().getOffsetZ(), segment.startDirection().getOffsetX());
        double endAngle = Math.atan2(segment.endDirection().getOffsetZ(), segment.endDirection().getOffsetX());
        double angleDiff = endAngle - startAngle;

        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;

        boolean tangentsColinear = segment.startDirection() == segment.endDirection() || segment.startDirection() == segment.endDirection().getOpposite();
        boolean axisAligned;
        if (segment.startDirection().getAxis() == Direction.Axis.X) {
            axisAligned = Math.abs((segment.end().getZ() + 0.5) - (segment.start().getZ() + 0.5)) < 1e-6;
        } else {
            axisAligned = Math.abs((segment.end().getX() + 0.5) - (segment.start().getX() + 0.5)) < 1e-6;
        }
        
        boolean isStraight = tangentsColinear && axisAligned;
        if (isStraight) {
            double startCenterX = segment.start().getX() + 0.5;
            double startCenterZ = segment.start().getZ() + 0.5;
            double endCenterX = segment.end().getX() + 0.5;
            double endCenterZ = segment.end().getZ() + 0.5;
            double startY = segment.start().getY();
            double endY = segment.end().getY();

            int baseSampleCount = 200;
            double scaling = segment.getScaling();
            int sampleCount = (int) Math.max(baseSampleCount, baseSampleCount / Math.max(0.1, scaling));
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
                double verticalT = applyVerticalEase(parameterT, segment.getSlopeCurvature());

                double interpolatedX = startCenterX + (endCenterX - startCenterX) * parameterT;
                double interpolatedZ = startCenterZ + (endCenterZ - startCenterZ) * parameterT;
                double interpolatedY = startY + (endY - startY) * verticalT;

                sampledX[i] = interpolatedX;
                sampledZ[i] = interpolatedZ;
                sampledY[i] = interpolatedY;
                double segmentLength = Math.sqrt((sampledX[i] - sampledX[i - 1]) * (sampledX[i] - sampledX[i - 1]) + (sampledZ[i] - sampledZ[i - 1]) * (sampledZ[i] - sampledZ[i - 1]) + (sampledY[i] - sampledY[i - 1]) * (sampledY[i] - sampledY[i - 1]));
                cumulativeDistances[i] = cumulativeDistances[i - 1] + segmentLength;
            }

            double repeatInterval = trackType.repeatInterval();
            double scaledRepeatInterval = repeatInterval * scaling;
            double minSpacing = 0.1;
            double finalRepeatInterval = Math.max(scaledRepeatInterval, minSpacing);
            for (double distanceAlongPath = 0.0; distanceAlongPath <= cumulativeDistances[sampleCount] + 1e-6; distanceAlongPath += finalRepeatInterval) {
                int i = 1;
                while (i <= sampleCount && cumulativeDistances[i] < distanceAlongPath) i++;
                if (i > sampleCount) i = sampleCount;

                int i0 = i - 1;
                double segmentDistance = cumulativeDistances[i] - cumulativeDistances[i0];
                double alpha = segmentDistance > 1e-6 ? (distanceAlongPath - cumulativeDistances[i0]) / segmentDistance : 0.0;

                double x = sampledX[i0] + (sampledX[i] - sampledX[i0]) * alpha;
                double y = sampledY[i0] + (sampledY[i] - sampledY[i0]) * alpha;
                double z = sampledZ[i0] + (sampledZ[i] - sampledZ[i0]) * alpha;

                double tangentX = sampledX[i] - sampledX[i0];
                double tangentY = sampledY[i] - sampledY[i0];
                double tangentZ = sampledZ[i] - sampledZ[i0];

                float yawDeg = (float) Math.toDegrees(Math.atan2(tangentX, tangentZ));
                double horizontalLen = Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);
                float pitchDeg = horizontalLen > 1e-6 ? (float) (-Math.toDegrees(Math.atan2(tangentY, horizontalLen))) : 0.0f;

                BlockPos blockPos = BlockPos.ofFloored(x, y, z);
                int block = world.getLightLevel(LightType.BLOCK, blockPos);
                int sky = world.getDimension().hasSkyLight() ? world.getLightLevel(LightType.SKY, blockPos) : 0;
                int packedLight = (sky << 20) | (block << 4);
                int packedOverlay = OverlayTexture.DEFAULT_UV;

                matrices.push();
                matrices.translate(x - cameraPosition.x, y - cameraPosition.y, z - cameraPosition.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));
                
                if (scaling != 1.0) {
                    matrices.scale((float)scaling, (float)scaling, (float)scaling);
                }
                
                Matrix4f xf = matrices.peek().getPositionMatrix();
                Renderix.enqueueInstance(model, xf, packedLight, packedOverlay);
                matrices.pop();
            }
        } else {
            renderCurvedTrack(segment, model, matrices, vertexConsumers, cameraPosition, trackType, startAngle, endAngle, angleDiff);
        }
    }

    private static void renderCurvedTrack(TrackSegment segment, ObjModel model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Vec3d cameraPosition, TrackConfigLoader.TrackTypeData trackType, double startAngle, double endAngle, double angleDiff) {
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) return;

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

        // magic number :]
        double circleApproxFactor = 0.55228477;
        double startControlDist = startAxisDistance * circleApproxFactor;
        double endControlDist = endAxisDistance * circleApproxFactor;

        double control1X = startCenterX + startDirectionVec.x * startControlDist;
        double control1Z = startCenterZ + startDirectionVec.z * startControlDist;
        double control2X = endCenterX - endDirectionVec.x * endControlDist;
        double control2Z = endCenterZ - endDirectionVec.z * endControlDist;

        int baseSampleCount = 200;
        double scaling = segment.getScaling();
        int sampleCount = (int) Math.max(baseSampleCount, baseSampleCount / Math.max(0.1, scaling));
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
            double segmentLength = Math.sqrt((sampledX[i] - sampledX[i - 1]) * (sampledX[i] - sampledX[i - 1]) + (sampledZ[i] - sampledZ[i - 1]) * (sampledZ[i] - sampledZ[i - 1]) + (sampledY[i] - sampledY[i - 1]) * (sampledY[i] - sampledY[i - 1]));
            cumulativeDistances[i] = cumulativeDistances[i - 1] + segmentLength;
        }

        double repeatInterval = trackType.repeatInterval();
        double scaledRepeatInterval = repeatInterval * segment.getScaling();
        double minSpacing = 0.1;
        double finalRepeatInterval = Math.max(scaledRepeatInterval, minSpacing);
        for (double distanceAlongPath = 0.0; distanceAlongPath <= cumulativeDistances[sampleCount] + 1e-6; distanceAlongPath += finalRepeatInterval) {
            int i = 1;
            while (i <= sampleCount && cumulativeDistances[i] < distanceAlongPath) i++;
            if (i > sampleCount) i = sampleCount;

            int i0 = i - 1;
            double segmentDistance = cumulativeDistances[i] - cumulativeDistances[i0];
            double alpha = segmentDistance > 1e-6 ? (distanceAlongPath - cumulativeDistances[i0]) / segmentDistance : 0.0;
            double x = sampledX[i0] + (sampledX[i] - sampledX[i0]) * alpha;
            double y = sampledY[i0] + (sampledY[i] - sampledY[i0]) * alpha;
            double z = sampledZ[i0] + (sampledZ[i] - sampledZ[i0]) * alpha;

            double tangentX = sampledX[i] - sampledX[i0];
            double tangentY = sampledY[i] - sampledY[i0];
            double tangentZ = sampledZ[i] - sampledZ[i0];
            float yawDeg = (float) Math.toDegrees(Math.atan2(tangentX, tangentZ));
            double horizontalLen = Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);
            float pitchDeg = horizontalLen > 1e-6 ? (float) (-Math.toDegrees(Math.atan2(tangentY, horizontalLen))) : 0.0f;

            BlockPos blockPos = BlockPos.ofFloored(x, y, z);
            int block = world.getLightLevel(LightType.BLOCK, blockPos);
            int sky = world.getDimension().hasSkyLight() ? world.getLightLevel(LightType.SKY, blockPos) : 0;
            int packedLight = (sky << 20) | (block << 4);
            int packedOverlay = OverlayTexture.DEFAULT_UV;

            matrices.push();
            matrices.translate(x - cameraPosition.x, y - cameraPosition.y, z - cameraPosition.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));

            if (scaling != 1.0) {
                matrices.scale((float)scaling, (float)scaling, (float)scaling);
            }

            Matrix4f xf = matrices.peek().getPositionMatrix();
            Renderix.enqueueInstance(model, xf, packedLight, packedOverlay);
            matrices.pop();
        }
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

    private static ObjModel getOrLoadModel(String modelPath, boolean flipV) {
        String cacheKey = modelPath + "|flip:" + (flipV ? 1 : 0);
        if (modelCache.containsKey(cacheKey)) return modelCache.get(cacheKey);
        
        try {
            String namespace = TrainMod.MOD_ID;
            String path = modelPath;
            if (modelPath.contains(":")) {
                String[] parts = modelPath.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            }
            
            ObjModel model = Renderix.loadModel(namespace, path, flipV);

            modelCache.put(cacheKey, model);
            return model;
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to load track model: {} - {}", modelPath, e.getMessage());
            return null;
        }
    }

    public static Map<String, TrackConfigLoader.TrackTypeData> getTrackTypes() {
        return trackTypes;
    }
}
