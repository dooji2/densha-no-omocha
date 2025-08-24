package com.dooji.dno.train;


import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec2f;

import java.util.*;

public class TrainBoardingRenderer {
    private static final Map<String, Double> trainPathPrev = new HashMap<>();

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TrainBoardingRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        updateBoardedPlayerPosition();
    }
    
    private static void updateBoardedPlayerPosition() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        
        String boardedTrainId = TrainBoardingManager.getCurrentBoardedTrainId();
        String boardedCarriageId = TrainBoardingManager.getCurrentBoardedCarriageId();
        Vec3d relativePosition = TrainBoardingManager.getCurrentRelativePosition();
        
        if (boardedTrainId == null || boardedCarriageId == null || relativePosition == null) {
            return;
        }
        
        World world = player.getWorld();
        Map<String, TrainClient> trains = TrainManagerClient.getTrainsFor(world);
        if (trains == null) return;
        
        TrainClient train = trains.get(boardedTrainId);
        if (train == null) return;
        
        List<String> carriageIds = train.getCarriageIds();
        if (carriageIds.isEmpty()) return;
        
        List<String> orderedCarriageIds = new ArrayList<>(carriageIds);
        if (train.isReversed()) {
            Collections.reverse(orderedCarriageIds);
        }
        
        double currentOffset = 0.0;
        
        for (int i = 0; i < orderedCarriageIds.size(); i++) {
            String carriageId = orderedCarriageIds.get(i);
            if (carriageId.equals(boardedCarriageId)) {
                TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(carriageId);
                
                if (trainData == null) continue;
                
                double carriageLength = trainData.length();
                double inset = trainData.bogieInset();
                double insetDist = MathHelper.clamp(inset, 0.0, 0.49) * carriageLength;
                
                double frontOffset = currentOffset + insetDist;
                double rearOffset = currentOffset + carriageLength - insetDist;
                
                double trainPathDistance = train.getCurrentPathDistance();
                
                Vec3d frontPos = train.getPositionAlongContinuousPath(trainPathDistance - frontOffset);
                Vec3d rearPos = train.getPositionAlongContinuousPath(trainPathDistance - rearOffset);
                
                if (frontPos != null && rearPos != null) {
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
                    
                    Vec3d rotatedRelativePos = new Vec3d(relativePosition.x, relativePosition.y, relativePosition.z);

                    rotatedRelativePos = rotatedRelativePos.rotateX((float) -carriagePitch);
                    rotatedRelativePos = rotatedRelativePos.rotateY((float) carriageYaw);

                    Vec3d playerWorldPos = carriageCenter
                        .add(rotatedRelativePos)
                        .add(0, 0.5 + trainData.heightOffset(), 0);
                    
                    player.updatePosition(playerWorldPos.getX(), playerWorldPos.getY(), playerWorldPos.getZ());
                    
                    showDismountMessage();
                    handlePlayerMovementInsideTrain(train, trainData, relativePosition);
                    
                    TrainBoardingManager.sendPositionUpdateToServer();
                }
                break;
            }
            
            TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(carriageId);
            if (trainData != null) {
                currentOffset += trainData.length() + 1.0;
            }
        }
    }

    private static void handlePlayerMovementInsideTrain(TrainClient train, TrainConfigLoader.TrainTypeData trainData, Vec3d relativePosition) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        
        player.setVelocity(0, 0, 0);
        player.setMovementSpeed(0);
        
        Vec2f input = player.input.getMovementInput();
        if (input.lengthSquared() > 0) {
            double moveSpeed = 0.1;
            double trainPathDistance = train.getCurrentPathDistance();

            List<String> carriageIds = train.getCarriageIds();
            List<String> orderedCarriageIds = new ArrayList<>(carriageIds);
            if (train.isReversed()) {
                Collections.reverse(orderedCarriageIds);
            }

            double currentOffset = 0.0;
            for (int i = 0; i < orderedCarriageIds.size(); i++) {
                String carriageId = orderedCarriageIds.get(i);
                if (carriageId.equals(TrainBoardingManager.getCurrentBoardedCarriageId())) {
                    TrainConfigLoader.TrainTypeData currentTrainData = TrainConfigLoader.getTrainType(carriageId);

                    if (currentTrainData != null) {
                        double carriageLength = currentTrainData.length();
                        double inset = currentTrainData.bogieInset();
                        double insetDist = MathHelper.clamp(inset, 0.0, 0.49) * carriageLength;

                        double frontOffset = currentOffset + insetDist;
                        double rearOffset = currentOffset + carriageLength - insetDist;

                        Vec3d frontPos = train.getPositionAlongContinuousPath(trainPathDistance - frontOffset);
                        Vec3d rearPos = train.getPositionAlongContinuousPath(trainPathDistance - rearOffset);

                        if (frontPos != null && rearPos != null) {
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

                            float playerYaw = player.getYaw();
                            double playerYawRad = Math.toRadians(playerYaw);

                            double playerForwardX = -Math.sin(playerYawRad);
                            double playerForwardZ = Math.cos(playerYawRad);
                            double playerRightX = -playerForwardZ;
                            double playerRightZ = playerForwardX;

                            double cosCarriageYaw = Math.cos(carriageYaw);
                            double sinCarriageYaw = Math.sin(carriageYaw);

                            double localForwardX = playerForwardX * (-cosCarriageYaw) + playerForwardZ * sinCarriageYaw;
                            double localForwardZ = playerForwardX * (-sinCarriageYaw) + playerForwardZ * (-cosCarriageYaw);

                            double localRightX = playerRightX * (-cosCarriageYaw) + playerRightZ * sinCarriageYaw;
                            double localRightZ = playerRightX * (-sinCarriageYaw) + playerRightZ * (-cosCarriageYaw);

                            double localDX = localRightX * input.x * moveSpeed - localForwardX * input.y * moveSpeed;
                            double localDZ = localRightZ * input.x * moveSpeed - localForwardZ * input.y * moveSpeed;
                            double localDY = 0;

                            Vec3d newRelativePos = relativePosition.add(new Vec3d(localDX, localDY, localDZ));

                            double maxX = currentTrainData.width() / 2.0;
                            double maxZ = currentTrainData.length() / 2.0;
                            double maxY = currentTrainData.height() / 2.0;

                            newRelativePos = new Vec3d(
                                Math.max(-maxX, Math.min(maxX, newRelativePos.x)),
                                Math.max(-maxY, Math.min(maxY, newRelativePos.y)),
                                Math.max(-maxZ, Math.min(maxZ, newRelativePos.z))
                            );

                            TrainBoardingManager.updateRelativePosition(newRelativePos);
                        }
                    }
                    break;
                }

                TrainConfigLoader.TrainTypeData prevTrainData = TrainConfigLoader.getTrainType(carriageId);
                if (prevTrainData != null) {
                    currentOffset += prevTrainData.length() + 1.0;
                }
            }
        }
    }

    public static void clearCaches() {
        trainPathPrev.clear();
    }
    
    private static void showDismountMessage() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("message.dno.press_shift_to_dismount"), true);
        }
    }
}
