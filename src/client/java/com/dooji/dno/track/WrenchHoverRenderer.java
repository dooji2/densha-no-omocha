package com.dooji.dno.track;

import com.dooji.dno.gui.TrackConfigScreen;
import com.dooji.dno.registry.TrainModItems;
import com.dooji.dno.train.TrainDoorDetector;
import com.dooji.dno.train.TrainManagerClient;
import com.dooji.dno.train.TrainClient;
import com.dooji.dno.train.TrainConfigLoader;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WrenchHoverRenderer {
    private static TrackSegment hoveredSegment = null;
    private static boolean wasRightClickPressed = false;
    private static int pulseTicks = 0;

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WrenchHoverRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(WrenchHoverRenderer::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !isHoldingVisualizer(player)) {
            hoveredSegment = null;
            return;
        }

        updateHoverDetection(client, player);
        handleRightClick(client, player);
        pulseTicks++;
    }

    private static void updateHoverDetection(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d cameraPos = player.getCameraPosVec(0);
        Vec3d lookVec = player.getRotationVec(0);
        double maxDistance = 10.0;
        Vec3d endPos = cameraPos.add(lookVec.multiply(maxDistance));

        TrackSegment closestSegment = null;
        double closestDistance = Double.MAX_VALUE;

        World world = player.getWorld();
        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(world);
        if (tracks != null) {
            for (TrackSegment segment : tracks.values()) {
                double distance = getDistanceToWideSegment(cameraPos, endPos, segment);
                if (distance < closestDistance && distance < 3.0) {
                    closestDistance = distance;
                    closestSegment = segment;
                }
            }
        }

        hoveredSegment = closestSegment;
    }

    private static double getDistanceToWideSegment(Vec3d start, Vec3d end, TrackSegment segment) {
        List<Box> collisionBoxes = createSegmentCollisionBoxes(segment);
        double minDistance = Double.MAX_VALUE;
        for (Box box : collisionBoxes) {
            double distance = getDistanceToBox(start, end, box);
            if (distance < minDistance) {
                minDistance = distance;
            }
        }

        return minDistance;
    }

    private static List<Box> createSegmentCollisionBoxes(TrackSegment segment) {
        List<Box> boxes = new ArrayList<>();
        List<Vec3d> points = getSegmentPoints(segment);
        double segmentWidth = 1.5;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d current = points.get(i);
            Vec3d next = points.get(i + 1);
            Vec3d direction = next.subtract(current).normalize();
            Vec3d perpLeft = new Vec3d(-direction.z, 0, direction.x).normalize();
            Vec3d perpRight = new Vec3d(direction.z, 0, -direction.x).normalize();

            Vec3d leftCurrent = current.add(perpLeft.multiply(segmentWidth));
            Vec3d rightCurrent = current.add(perpRight.multiply(segmentWidth));
            Vec3d leftNext = next.add(perpLeft.multiply(segmentWidth));
            Vec3d rightNext = next.add(perpRight.multiply(segmentWidth));

            double minX = Math.min(Math.min(leftCurrent.x, rightCurrent.x), Math.min(leftNext.x, rightNext.x));
            double maxX = Math.max(Math.max(leftCurrent.x, rightCurrent.x), Math.max(leftNext.x, rightNext.x));
            double minY = Math.min(current.y, next.y);
            double maxY = Math.max(current.y, next.y) + 0.5;
            double minZ = Math.min(Math.min(leftCurrent.z, rightCurrent.z), Math.min(leftNext.z, rightNext.z));
            double maxZ = Math.max(Math.max(leftCurrent.z, rightCurrent.z), Math.max(leftNext.z, rightNext.z));

            boxes.add(new Box(minX, minY, minZ, maxX, maxY, maxZ));
        }

        return boxes;
    }

    private static double getDistanceToBox(Vec3d start, Vec3d end, Box box) {
        Vec3d rayDir = end.subtract(start).normalize();
        double tMin = (box.minX - start.x) / rayDir.x;
        double tMax = (box.maxX - start.x) / rayDir.x;
        if (tMin > tMax) {
            double temp = tMin;
            tMin = tMax;
            tMax = temp;
        }

        double tyMin = (box.minY - start.y) / rayDir.y;
        double tyMax = (box.maxY - start.y) / rayDir.y;
        if (tyMin > tyMax) {
            double temp = tyMin;
            tyMin = tyMax;
            tyMax = temp;
        }

        if (tMin > tyMax || tyMin > tMax) {
            return Double.MAX_VALUE;
        }

        if (tyMin > tMin) tMin = tyMin;
        if (tyMax < tMax) tMax = tyMax;

        double tzMin = (box.minZ - start.z) / rayDir.z;
        double tzMax = (box.maxZ - start.z) / rayDir.z;
        if (tzMin > tzMax) {
            double temp = tzMin;
            tzMin = tzMax;
            tzMax = temp;
        }

        if (tMin > tzMax || tzMin > tMax) {
            return Double.MAX_VALUE;
        }

        if (tzMin > tMin) tMin = tzMin;
        if (tzMax < tMax) tMax = tzMax;

        double rayLength = start.distanceTo(end);
        if (tMin > rayLength || tMax < 0) {
            return Double.MAX_VALUE;
        }

        return Math.max(0, tMin);
    }

    private static List<Vec3d> getSegmentPoints(TrackSegment segment) {
        double startFacingAngle = Math.atan2(segment.startDirection().getOffsetZ(), segment.startDirection().getOffsetX());
        double endFacingAngle = Math.atan2(segment.endDirection().getOffsetZ(), segment.endDirection().getOffsetX());
        double angleDifference = endFacingAngle - startFacingAngle;
        while (angleDifference > Math.PI) angleDifference -= 2 * Math.PI;
        while (angleDifference < -Math.PI) angleDifference += 2 * Math.PI;

        boolean tangentsAreColinear = segment.startDirection() == segment.endDirection() || segment.startDirection() == segment.endDirection().getOpposite();
        boolean pathIsAxisAligned;
        if (segment.startDirection().getAxis() == Direction.Axis.X) {
            pathIsAxisAligned = Math.abs((segment.end().getZ() + 0.5) - (segment.start().getZ() + 0.5)) < 1e-6;
        } else {
            pathIsAxisAligned = Math.abs((segment.end().getX() + 0.5) - (segment.start().getX() + 0.5)) < 1e-6;
        }

        boolean pathIsStraight = tangentsAreColinear && pathIsAxisAligned;

        if (pathIsStraight) {
            double startCenterX = segment.start().getX() + 0.5;
            double startCenterZ = segment.start().getZ() + 0.5;
            double endCenterX = segment.end().getX() + 0.5;
            double endCenterZ = segment.end().getZ() + 0.5;
            double startY = segment.start().getY();
            double endY = segment.end().getY();

            int sampleCount = 200;
            List<Vec3d> points = new ArrayList<>(sampleCount + 1);
            for (int i = 0; i <= sampleCount; i++) {
                double t = (double) i / sampleCount;
                double easedYt = applyVerticalEase(t, segment.getSlopeCurvature());
                double x = startCenterX + (endCenterX - startCenterX) * t;
                double z = startCenterZ + (endCenterZ - startCenterZ) * t;
                double y = startY + (endY - startY) * easedYt;
                points.add(new Vec3d(x, y, z));
            }

            return points;
        } else {
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
            List<Vec3d> points = new ArrayList<>(sampleCount + 1);
            points.add(new Vec3d(startCenterX, startY, startCenterZ));
            for (int i = 1; i <= sampleCount; i++) {
                double t = (double) i / sampleCount;
                double omt = 1.0 - t;
                double x = omt * omt * omt * startCenterX + 3 * omt * omt * t * control1X + 3 * omt * t * t * control2X + t * t * t * endCenterX;
                double z = omt * omt * omt * startCenterZ + 3 * omt * omt * t * control1Z + 3 * omt * t * t * control2Z + t * t * t * endCenterZ;
                double easedYt = applyVerticalEase(t, segment.getSlopeCurvature());
                double y = startY + (endY - startY) * easedYt;
                points.add(new Vec3d(x, y, z));
            }

            return points;
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

    private static void handleRightClick(MinecraftClient client, ClientPlayerEntity player) {
        boolean isRightClickPressed = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (isRightClickPressed && !wasRightClickPressed && hoveredSegment != null) {
            TrackConfigScreen configScreen = new TrackConfigScreen(hoveredSegment);
            client.setScreen(configScreen);
        }

        wasRightClickPressed = isRightClickPressed;
    }

    private static void render(WorldRenderContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !isHoldingVisualizer(player)) {
            return;
        }

        World world = player.getWorld();
        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(world);
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        List<TrackSegment> trackList = new ArrayList<>(tracks.values());
        renderTrackShapes(context, trackList);

        if (hoveredSegment != null) {
            renderHoverHighlight(context, hoveredSegment);
        }
        
        renderTrainDoorBoundingBoxes(context);
    }

    private static boolean isHoldingVisualizer(ClientPlayerEntity player) {
        return player.getMainHandStack().getItem() == TrainModItems.CROWBAR || player.getOffHandStack().getItem() == TrainModItems.CROWBAR;
    }

    private static void renderTrackShapes(WorldRenderContext context, List<TrackSegment> tracks) {
        Vec3d cameraPos = context.camera().getPos();
        for (TrackSegment segment : tracks) {
            renderTrackSegmentShape(context, segment, new Color(100, 100, 255, 128), cameraPos);
        }
    }

    private static void renderTrackSegmentShape(WorldRenderContext context, TrackSegment segment, Color color, Vec3d cameraPos) {
        List<Vec3d> points = getSegmentPoints(segment);
        renderTrackShape(context, points, color, cameraPos);
    }

    @SuppressWarnings("unused")
    private static void renderStraightTrackShape(WorldRenderContext context, TrackSegment segment, Color color, Vec3d cameraPos, double totalLength) {
        List<Vec3d> points = new ArrayList<>();
        double stepSize = 0.5;

        for (double distance = 0; distance <= totalLength; distance += stepSize) {
            double progress = distance / totalLength;
            double startX = segment.start().getX() + 0.5;
            double startZ = segment.start().getZ() + 0.5;
            double endX = segment.end().getX() + 0.5;
            double endZ = segment.end().getZ() + 0.5;

            double x = startX + (endX - startX) * progress;
            double y = segment.start().getY() + (segment.end().getY() - segment.start().getY()) * progress;
            double z = startZ + (endZ - startZ) * progress;

            points.add(new Vec3d(x, y, z));
        }

        renderTrackShape(context, points, color, cameraPos);
    }

    @SuppressWarnings("unused")
    private static void renderCurvedTrackShape(WorldRenderContext context, TrackSegment segment, Color color, Vec3d cameraPos, double startAngle, double endAngle, double angleDiff) {
        double startX = segment.start().getX() + 0.5;
        double startZ = segment.start().getZ() + 0.5;
        double endX = segment.end().getX() + 0.5;
        double endZ = segment.end().getZ() + 0.5;

        double chordLength = Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endZ - startZ, 2));
        double radius = chordLength / (2 * Math.sin(Math.abs(angleDiff) / 2));

        double midX = (startX + endX) / 2;
        double midZ = (startZ + endZ) / 2;
        double chordDirX = (endX - startX) / chordLength;
        double chordDirZ = (endZ - startZ) / chordLength;
        double perpX = chordDirZ;
        double perpZ = -chordDirX;
        double centerDistance = Math.sqrt(radius * radius - (chordLength / 2) * (chordLength / 2));

        Vec3d startDir = new Vec3d(segment.startDirection().getOffsetX(), 0, segment.startDirection().getOffsetZ()).normalize();
        Vec3d endDir = new Vec3d(segment.endDirection().getOffsetX(), 0, segment.endDirection().getOffsetZ()).normalize();
        double crossProduct = startDir.x * endDir.z - startDir.z * endDir.x;

        double centerX, centerZ;
        if (crossProduct > 0) {
            centerX = midX - perpX * centerDistance;
            centerZ = midZ - perpZ * centerDistance;
        } else {
            centerX = midX + perpX * centerDistance;
            centerZ = midZ + perpZ * centerDistance;
        }

        double startArcAngle = Math.atan2(startZ - centerZ, startX - centerX);
        double endArcAngle = Math.atan2(endZ - centerZ, endX - centerX);

        while (endArcAngle < startArcAngle) {
            endArcAngle += 2 * Math.PI;
        }

        if (Math.abs(endArcAngle - startArcAngle) > Math.PI) {
            if (endArcAngle > startArcAngle) {
                endArcAngle -= 2 * Math.PI;
            } else {
                startArcAngle -= 2 * Math.PI;
            }
        }

        double arcLength = radius * Math.abs(endArcAngle - startArcAngle);
        double stepSize = 0.5;

        List<Vec3d> points = new ArrayList<>();
        for (double distance = 0; distance <= arcLength; distance += stepSize) {
            double progress = distance / arcLength;
            double currentAngle = startArcAngle + (endArcAngle - startArcAngle) * progress;
            double x = centerX + radius * Math.cos(currentAngle);
            double y = segment.start().getY() + (segment.end().getY() - segment.start().getY()) * progress;
            double z = centerZ + radius * Math.sin(currentAngle);

            points.add(new Vec3d(x, y, z));
        }

        renderTrackShape(context, points, color, cameraPos);
    }

    private static void renderTrackShape(WorldRenderContext context, List<Vec3d> points, Color color, Vec3d cameraPos) {
        double pathWidth = 0.8;
        List<Vec3d> leftPoints = new ArrayList<>();
        List<Vec3d> rightPoints = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            Vec3d point = points.get(i);
            Vec3d direction;
            if (i < points.size() - 1) {
                direction = points.get(i + 1).subtract(point).normalize();
            } else if (i > 0) {
                direction = point.subtract(points.get(i - 1)).normalize();
            } else {
                direction = new Vec3d(1, 0, 0);
            }

            Vec3d perpLeft = new Vec3d(-direction.z, 0, direction.x).normalize();
            Vec3d perpRight = new Vec3d(direction.z, 0, -direction.x).normalize();

            leftPoints.add(point.add(perpLeft.multiply(pathWidth)));
            rightPoints.add(point.add(perpRight.multiply(pathWidth)));
        }

        renderFilledShape(context, leftPoints, rightPoints, color, cameraPos);
        renderShapeBorders(context, leftPoints, rightPoints, color, cameraPos);
    }

    private static void renderFilledShape(WorldRenderContext context, List<Vec3d> leftPoints, List<Vec3d> rightPoints, Color color, Vec3d cameraPos) {
        for (int i = 0; i < leftPoints.size() - 1; i++) {
            Vec3d left1 = leftPoints.get(i);
            Vec3d right1 = rightPoints.get(i);
            Vec3d left2 = leftPoints.get(i + 1);
            Vec3d right2 = rightPoints.get(i + 1);

            renderTriangle(context, left1, right1, left2, color, cameraPos);
            renderTriangle(context, right1, right2, left2, color, cameraPos);
        }
    }

    private static void renderTriangle(WorldRenderContext context, Vec3d p1, Vec3d p2, Vec3d p3, Color color, Vec3d cameraPos) {
        float x1 = (float) (p1.x - cameraPos.x);
        float y1 = (float) (p1.y - cameraPos.y + 0.5);
        float z1 = (float) (p1.z - cameraPos.z);

        float x2 = (float) (p2.x - cameraPos.x);
        float y2 = (float) (p2.y - cameraPos.y + 0.5);
        float z2 = (float) (p2.z - cameraPos.z);

        float x3 = (float) (p3.x - cameraPos.x);
        float y3 = (float) (p3.y - cameraPos.y + 0.5);
        float z3 = (float) (p3.z - cameraPos.z);

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        float normalX = 0.0f;
        float normalY = 1.0f;
        float normalZ = 0.0f;

        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1)
            .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
            .normal(normalX, normalY, normalZ);
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2)
            .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
            .normal(normalX, normalY, normalZ);

        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2)
            .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
            .normal(normalX, normalY, normalZ);
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x3, y3, z3)
            .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
            .normal(normalX, normalY, normalZ);

        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x3, y3, z3)
            .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
            .normal(normalX, normalY, normalZ);
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1)
            .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
            .normal(normalX, normalY, normalZ);
    }

    private static void renderShapeBorders(WorldRenderContext context, List<Vec3d> leftPoints, List<Vec3d> rightPoints, Color color, Vec3d cameraPos) {
        Color borderColor = new Color(
            Math.min(255, color.getRed() + 50),
            Math.min(255, color.getGreen() + 50),
            Math.min(255, color.getBlue() + 50),
            255
        );

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        for (int i = 0; i < leftPoints.size() - 1; i++) {
            Vec3d current = leftPoints.get(i);
            Vec3d next = leftPoints.get(i + 1);

            float x1 = (float) (current.x - cameraPos.x);
            float y1 = (float) (current.y - cameraPos.y);
            float z1 = (float) (current.z - cameraPos.z);

            float x2 = (float) (next.x - cameraPos.x);
            float y2 = (float) (next.y - cameraPos.y);
            float z2 = (float) (next.z - cameraPos.z);

            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1)
                .color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), borderColor.getAlpha())
                .normal(0.0f, 1.0f, 0.0f);
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2)
                .color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), borderColor.getAlpha())
                .normal(0.0f, 1.0f, 0.0f);
        }

        for (int i = 0; i < rightPoints.size() - 1; i++) {
            Vec3d current = rightPoints.get(i);
            Vec3d next = rightPoints.get(i + 1);

            float x1 = (float) (current.x - cameraPos.x);
            float y1 = (float) (current.y - cameraPos.y + 0.5);
            float z1 = (float) (current.z - cameraPos.z);

            float x2 = (float) (next.x - cameraPos.x);
            float y2 = (float) (next.y - cameraPos.y + 0.5);
            float z2 = (float) (next.z - cameraPos.z);

            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1)
                .color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), borderColor.getAlpha())
                .normal(0.0f, 1.0f, 0.0f);
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2)
                .color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), borderColor.getAlpha())
                .normal(0.0f, 1.0f, 0.0f);
        }
    }

    private static void renderHoverHighlight(WorldRenderContext context, TrackSegment segment) {
        Color trackColor = getTrackTypeColor(segment);
        if (trackColor == null) {
            trackColor = new Color(100, 100, 255);
        }
        
        float pulseIntensity = (float) (Math.sin(pulseTicks * 0.2) * 0.5 + 0.5);
        Color pulseColor = new Color(
            (int) (trackColor.getRed() * pulseIntensity),
            (int) (trackColor.getGreen() * pulseIntensity),
            (int) (trackColor.getBlue() * pulseIntensity),
            200
        );

        Vec3d cameraPos = context.camera().getPos();
        renderTrackSegmentShape(context, segment, pulseColor, cameraPos);
    }

    private static Color getTrackTypeColor(TrackSegment segment) {
        String modelId = segment.getModelId();
        if (modelId == null) return null;
        
        Map<String, TrackConfigLoader.TrackTypeData> trackTypes = TrackConfigLoader.getAllTrackTypes();
        if (trackTypes == null || trackTypes.isEmpty()) {
            TrackConfigLoader.loadTrackTypes(MinecraftClient.getInstance().getResourceManager());
            trackTypes = TrackConfigLoader.getAllTrackTypes();
        }
        
        TrackConfigLoader.TrackTypeData trackType = trackTypes.get(modelId);
        if (trackType == null) return null;
        
        return getColorForTrackType(trackType);
    }

    private static Color getColorForTrackType(TrackConfigLoader.TrackTypeData trackType) {
        String name = trackType.name().toLowerCase();
        if (name.contains("red")) return new Color(255, 0, 0);
        if (name.contains("blue")) return new Color(0, 0, 255);
        if (name.contains("green")) return new Color(0, 255, 0);
        if (name.contains("yellow")) return new Color(255, 255, 0);
        if (name.contains("purple")) return new Color(128, 0, 128);
        if (name.contains("orange")) return new Color(255, 165, 0);
        if (name.contains("pink")) return new Color(255, 192, 203);
        if (name.contains("cyan")) return new Color(0, 255, 255);
        if (name.contains("magenta")) return new Color(255, 0, 255);
        if (name.contains("brown")) return new Color(139, 69, 19);
        if (name.contains("gray") || name.contains("grey")) return new Color(128, 128, 128);
        if (name.contains("black")) return new Color(0, 0, 0);
        if (name.contains("white")) return new Color(255, 255, 255);
        
        return new Color(100, 100, 255);
    }
    
    private static void renderTrainDoorBoundingBoxes(WorldRenderContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        
        World world = player.getWorld();
        Map<String, TrainClient> trains = TrainManagerClient.getTrainsFor(world);
        if (trains == null || trains.isEmpty()) return;
        
        Vec3d cameraPos = context.camera().getPos();
        
        for (TrainClient train : trains.values()) {
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
                
                if (trainData != null && trainData.model() != null && trainData.doors() != null && trainData.doors().hasDoors()) {
                    String modelPath = trainData.model().replace("densha-no-omocha:", "");
                    List<TrainDoorDetector.DoorBoundingBox> doorBoxes = TrainDoorDetector.detectDoors(modelPath, trainData.doors().doorParts());
                    
                    if (!doorBoxes.isEmpty()) {
                        double insetDist = 0.0;
                        double inset = trainData.bogieInset();
                        insetDist = Math.max(0.0, Math.min(0.49, inset)) * carriageLength;
                        
                        double frontOffset = currentOffset + insetDist;
                        double rearOffset = currentOffset + carriageLength - insetDist;
                        
                        Vec3d frontPos = train.getPositionAlongContinuousPath(trainPathDistance - frontOffset);
                        Vec3d rearPos = train.getPositionAlongContinuousPath(trainPathDistance - rearOffset);
                        
                        if (frontPos != null && rearPos != null) {
                            List<Vec3d[]> doorWorldCorners = TrainDoorDetector.createDoorBoundingBoxes(doorBoxes, frontPos, rearPos, carriageWidth, carriageHeight, train.isReversed(), trainData.heightOffset(), trainData.isReversed());
                            
                            for (Vec3d[] corners : doorWorldCorners) {
                                renderOrientedBox(context, corners, cameraPos);
                            }
                        }
                    }
                }
                
                currentOffset += carriageLength + 1.0;
            }
        }
    }
    
    private static void renderOrientedBox(WorldRenderContext context, Vec3d[] corners, Vec3d cameraPos) {
        if (corners == null || corners.length != 8) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int[] edge : edges) {
            Vec3d p1 = corners[edge[0]];
            Vec3d p2 = corners[edge[1]];

            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)(p1.x - cameraPos.x), (float)(p1.y - cameraPos.y), (float)(p1.z - cameraPos.z))
                .color(255, 100, 100, 255)
                .normal(0, 1, 0);
            
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)(p2.x - cameraPos.x), (float)(p2.y - cameraPos.y), (float)(p2.z - cameraPos.z))
                .color(255, 100, 100, 255)
                .normal(0, 1, 0);
        }
    }
}
