package com.dooji.dno.train;

import de.javagl.obj.*;
import com.dooji.renderix.Renderix;
import com.dooji.renderix.ObjModel;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrainDoorDetector {
    private static final Map<String, List<DoorBoundingBox>> doorCache = new HashMap<>();
    
    public static class DoorBoundingBox {
        public final Vec3d position;
        public final double width;
        public final double height;
        public final double thickness;
        
        public DoorBoundingBox(Vec3d position, double width, double height, double thickness) {
            this.position = position;
            this.width = width;
            this.height = height;
            this.thickness = thickness;
        }
        
        public Box createBox() {
            return new Box(
                position.x - width * 0.5,
                position.y - height * 0.5,
                position.z - thickness * 0.5,
                position.x + width * 0.5,
                position.y + height * 0.5,
                position.z + thickness * 0.5
            );
        }
    }
    
    public static List<DoorBoundingBox> detectDoors(String modelPath, List<TrainConfigLoader.DoorPart> doorParts) {
        if (doorParts == null || doorParts.isEmpty()) {
            return new ArrayList<>();
        }
        
        String cacheKey = modelPath + "_" + doorParts.hashCode();
        if (doorCache.containsKey(cacheKey)) {
            return doorCache.get(cacheKey);
        }
        
        List<DoorBoundingBox> doorBoxes = new ArrayList<>();
        
        try {
            String[] parts = modelPath.split(":");
            if (parts.length != 2) return doorBoxes;
            
            String modId = parts[0];
            String path = parts[1];
            
            ObjModel model = Renderix.getLoadedModel(modId, path);
            if (model == null) return doorBoxes;
            
            Obj obj = model.getRawObj();
            if (obj == null) return doorBoxes;
            
            for (TrainConfigLoader.DoorPart doorPart : doorParts) {
                String partName = doorPart.partName();
                ObjGroup group = obj.getGroup(partName);
                if (group != null) {
                    DoorBoundingBox doorBox = createDoorBoundingBox(obj, group);
                    if (doorBox != null) {
                        doorBoxes.add(doorBox);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        doorCache.put(cacheKey, doorBoxes);
        return doorBoxes;
    }
    
    private static DoorBoundingBox createDoorBoundingBox(Obj obj, ObjGroup group) {
        if (group.getNumFaces() == 0) return null;
        
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        for (int i = 0; i < group.getNumFaces(); i++) {
            ObjFace face = group.getFace(i);
            for (int j = 0; j < face.getNumVertices(); j++) {
                int vertexIndex = face.getVertexIndex(j);
                FloatTuple vertex = obj.getVertex(vertexIndex);
                
                minX = Math.min(minX, vertex.getX());
                minY = Math.min(minY, vertex.getY());
                minZ = Math.min(minZ, vertex.getZ());
                maxX = Math.max(maxX, vertex.getX());
                maxY = Math.max(maxY, vertex.getY());
                maxZ = Math.max(maxZ, vertex.getZ());
            }
        }
        
        if (minX == Float.POSITIVE_INFINITY) return null;
        
        double width = maxX - minX;
        double height = maxY - minY;
        double thickness = maxZ - minZ;
        
        Vec3d center = new Vec3d(
            (minX + maxX) * 0.5,
            (minY + maxY) * 0.5,
            (minZ + maxZ) * 0.5
        );
        
        return new DoorBoundingBox(center, width, height, thickness);
    }
    
    public static List<Vec3d[]> createDoorBoundingBoxes(List<DoorBoundingBox> doorBoxes, Vec3d frontPos, Vec3d rearPos, double trainWidth, double trainHeight, boolean isReversed, double heightOffset) {
        List<Vec3d[]> worldCornersList = new ArrayList<>();
        
        if (doorBoxes.isEmpty()) return worldCornersList;
        
        Vec3d center = new Vec3d(
            (frontPos.x + rearPos.x) * 0.5,
            (frontPos.y + rearPos.y) * 0.5,
            (frontPos.z + rearPos.z) * 0.5
        );
        
        double dx = rearPos.x - frontPos.x;
        double dz = rearPos.z - frontPos.z;
        double dy = frontPos.y - rearPos.y;

        double horiz = Math.sqrt(dx * dx + dz * dz);
        double trainYaw = Math.atan2(dx, dz);
        double trainPitch = horiz > 1e-4 ? Math.atan2(dy, horiz) : 0.0;

        trainYaw += Math.PI;

        if (isReversed) {
            trainYaw += Math.PI;
            trainPitch = -trainPitch;
        }
        
        for (DoorBoundingBox doorBox : doorBoxes) {
            Vec3d[] worldCorners = new Vec3d[8];
            Vec3d localCenter = doorBox.position;
            double width = doorBox.width / 2.0;
            double height = doorBox.height / 2.0;
            double thickness = doorBox.thickness / 2.0;

            Vec3d[] localCorners = new Vec3d[]{
                localCenter.add(new Vec3d(-width, -height, -thickness)),
                localCenter.add(new Vec3d( width, -height, -thickness)),
                localCenter.add(new Vec3d( width,  height, -thickness)),
                localCenter.add(new Vec3d(-width,  height, -thickness)),
                localCenter.add(new Vec3d(-width, -height,  thickness)),
                localCenter.add(new Vec3d( width, -height,  thickness)),
                localCenter.add(new Vec3d( width,  height,  thickness)),
                localCenter.add(new Vec3d(-width,  height,  thickness))
            };

            for (int i = 0; i < 8; i++) {
                worldCorners[i] = convertLocalToWorldPosition(localCorners[i], center, trainYaw, trainPitch, heightOffset);
            }
            
            worldCornersList.add(worldCorners);
        }
        
        return worldCornersList;
    }
    
    private static Vec3d convertLocalToWorldPosition(Vec3d localPos, Vec3d trainCenter, double trainYaw, double trainPitch, double heightOffset) {
        Vec3d rotatedPos = new Vec3d(localPos.x, localPos.y, localPos.z);

        rotatedPos = rotatedPos.rotateX((float) trainPitch);
        rotatedPos = rotatedPos.rotateY((float) trainYaw);

        final double renderYOffset = 0.75;
        return trainCenter.add(rotatedPos).add(0, heightOffset + renderYOffset, 0);
    }
}
