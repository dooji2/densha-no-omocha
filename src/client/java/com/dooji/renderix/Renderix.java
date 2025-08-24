package com.dooji.renderix;

import de.javagl.obj.*;
import com.dooji.dno.train.TrainConfigLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

/**
 * A tiny OBJ wrapper for rendering OBJ models in-game. (it doesn't have great performance, but it will be improved in the future :<)
 * Uses a modified version of the JavaGL OBJ library by Zbx1425, licensed under the MIT license. For the MIT license, see https://opensource.org/licenses/MIT
 * @author dooji
 */
public class Renderix {
    public static final Logger LOGGER = LoggerFactory.getLogger("renderix");

    private static final Map<String, ObjModel> modelCache = new LinkedHashMap<>(16, 0.75f, true);
    private static final int MAX_CACHE_SIZE = 100;
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Map<String, Float> doorAnimationStates = new HashMap<>();
    private static final Map<String, Long> doorAnimationStartTimes = new HashMap<>();
    private static final Map<String, Float> doorAnimationTargets = new HashMap<>();
    private static final float DOOR_ANIMATION_DURATION = 1000.0f;

    private static final Map<String, Float> doorPopStates = new HashMap<>();
    private static final Map<String, Float> doorSlideStates = new HashMap<>();
    private static final Map<String, Long> doorLastUpdateTimes = new HashMap<>();
    private static final float POP_DURATION_MS = 200.0f;
    private static final float SLIDE_DURATION_MS = 800.0f;

    private static final Map<ObjModel, InstancedModelData> instancedModels = new HashMap<>();
    private static final Map<ObjModel, List<InstanceData>> instanceBatches = new HashMap<>();
    private static final int MAX_INSTANCES_PER_BATCH = 1000;

    private static class InstanceData {
        final Matrix4f transform;
        final int light;
        final int overlay;
        final int color;
        final boolean interiorLit;
        final String interiorPart;

        InstanceData(Matrix4f transform, int light, int overlay, int color, boolean interiorLit, String interiorPart) {
            this.transform = new Matrix4f(transform);
            this.light = light;
            this.overlay = overlay;
            this.color = color;
            this.interiorLit = interiorLit;
            this.interiorPart = interiorPart;
        }
    }

    private static class InstancedModelData {
        final int vertexBufferId;
        final int indexBufferId;
        final int instanceBufferId;
        final int vaoId;
        final Map<String, MeshData> meshes;

        InstancedModelData(int vertexBufferId, int indexBufferId, int instanceBufferId, int vaoId, Map<String, MeshData> meshes) {
            this.vertexBufferId = vertexBufferId;
            this.indexBufferId = indexBufferId;
            this.instanceBufferId = instanceBufferId;
            this.vaoId = vaoId;
            this.meshes = meshes;
        }

        void cleanup() {
            if (vertexBufferId != 0) {
                GL15.glDeleteBuffers(vertexBufferId);
            }
            
            if (indexBufferId != 0) {
                GL15.glDeleteBuffers(indexBufferId);
            }

            if (instanceBufferId != 0) {
                GL15.glDeleteBuffers(instanceBufferId);
            }
            
            if (vaoId != 0) {
                GL30.glDeleteVertexArrays(vaoId);
            }
        }
    }

    private static class MeshData {
        final int indexOffset;
        final int indexCount;

        MeshData(int indexOffset, int indexCount) {
            this.indexOffset = indexOffset;
            this.indexCount = indexCount;
        }
    }

    public static ObjModel loadModel(String modId, String objPath, String mtlPath) {
        return loadModel(modId, objPath, mtlPath, false);
    }

    public static ObjModel loadModel(String modId, String objPath, String mtlPath, boolean flipV) {
        String key = modId + ":" + objPath + ":" + (mtlPath != null ? mtlPath : "");
        ObjModel cached = modelCache.get(key);
        if (cached != null) return cached;

        try {
            ResourceManager resources = client.getResourceManager();
            Identifier objId = Identifier.of(modId, objPath);
            InputStream objStream = resources.getResource(objId).orElseThrow(() -> new IOException("OBJ not found: " + objId)).getInputStream();

            Obj obj = ObjReader.read(objStream);
            objStream.close();

            Map<String, Mtl> mtlMap = new HashMap<>();
            if (mtlPath != null) {
                try {
                    Identifier mtlId = Identifier.of(modId, mtlPath);
                    InputStream mtlStream = resources.getResource(mtlId).orElseThrow(() -> new IOException("MTL not found: " + mtlId)).getInputStream();
                    for (Mtl mtl : MtlReader.read(mtlStream)) {
                        mtlMap.put(mtl.getName(), mtl);
                    }

                    mtlStream.close();
                } catch (IOException e) {
                    LOGGER.error("Warning: {}", e.getMessage());
                }
            } else {
                List<String> mtlFiles = obj.getMtlFileNames();
                if (!mtlFiles.isEmpty()) {
                    String objDir = "";
                    int lastSlash = objPath.lastIndexOf('/');

                    if (lastSlash != -1) objDir = objPath.substring(0, lastSlash + 1);

                    for (String fileName : mtlFiles) {
                        try {
                            Identifier mtlId = Identifier.of(modId, objDir + fileName);
                            InputStream mtlStream = resources.getResource(mtlId).orElseThrow(() -> new IOException("MTL not found: " + mtlId)).getInputStream();
                            for (Mtl mtl : MtlReader.read(mtlStream)) {
                                mtlMap.put(mtl.getName(), mtl);
                            }

                            mtlStream.close();
                        } catch (IOException e) {
                            LOGGER.error("Warning: {}", e.getMessage());
                        }
                    }
                }
            }

            ObjModel model = new ObjModel(obj, mtlMap, modId, objPath, flipV);
            addToCache(key, model);
            prepareInstancedRendering(model);
            return model;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load OBJ: " + objPath, e);
        }
    }

    public static ObjModel loadModel(String modId, String objPath) {
        return loadModel(modId, objPath, null, false);
    }

        public static ObjModel loadModel(String modId, String objPath, boolean flipV) {
        return loadModel(modId, objPath, null, flipV);
    }
    
    public static ObjModel getLoadedModel(String modId, String objPath) {
        String key = modId + ":" + objPath + ":";
        return modelCache.get(key);
    }
    
    private static void prepareInstancedRendering(ObjModel model) {
        try {
            Map<String, MeshData> meshes = new HashMap<>();
            List<Float> allVertices = new ArrayList<>();
            List<Float> allNormals = new ArrayList<>();
            List<Float> allTexCoords = new ArrayList<>();
            List<Integer> allIndices = new ArrayList<>();

            int currentIndexOffset = 0;
            int currentVertexOffset = 0;

            for (Map.Entry<String, ObjModel.RenderData> entry : model.getMeshes().entrySet()) {
                String meshKey = entry.getKey();
                ObjModel.RenderData data = entry.getValue();

                int meshIndexOffset = currentIndexOffset;
                int meshIndexCount = data.vertices.size();

                for (int i = 0; i < data.vertices.size(); i++) {
                    allVertices.add(data.vertices.get(i).x);
                    allVertices.add(data.vertices.get(i).y);
                    allVertices.add(data.vertices.get(i).z);

                    allNormals.add(data.normals.get(i).x);
                    allNormals.add(data.normals.get(i).y);
                    allNormals.add(data.normals.get(i).z);

                    allTexCoords.add(data.texCoords.get(i * 2));
                    allTexCoords.add(data.texCoords.get(i * 2 + 1));

                    allIndices.add(currentVertexOffset + i);
                }

                meshes.put(meshKey, new MeshData(meshIndexOffset, meshIndexCount));
                currentIndexOffset += meshIndexCount;
                currentVertexOffset += data.vertices.size();
            }

            if (allVertices.isEmpty()) return;

            int vertexBufferId = GL15.glGenBuffers();
            int indexBufferId = GL15.glGenBuffers();
            int instanceBufferId = GL15.glGenBuffers();
            int vaoId = GL30.glGenVertexArrays();

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferId);
            ByteBuffer vertexData = ByteBuffer.allocateDirect(allVertices.size() * 4 + allNormals.size() * 4 + allTexCoords.size() * 4);
            vertexData.order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuffer = vertexData.asFloatBuffer();

            for (int i = 0; i < allVertices.size(); i += 3) {
                vertexBuffer.put(allVertices.get(i));
                vertexBuffer.put(allVertices.get(i + 1));
                vertexBuffer.put(allVertices.get(i + 2));
                vertexBuffer.put(allNormals.get(i));
                vertexBuffer.put(allNormals.get(i + 1));
                vertexBuffer.put(allNormals.get(i + 2));
                vertexBuffer.put(allTexCoords.get(i * 2));
                vertexBuffer.put(allTexCoords.get(i * 2 + 1));
            }

            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

            ByteBuffer indexData = ByteBuffer.allocateDirect(allIndices.size() * 4);
            indexData.order(ByteOrder.nativeOrder());

            IntBuffer indexBuffer = indexData.asIntBuffer();
            for (Integer index : allIndices) {
                indexBuffer.put(index);
            }

            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, GL15.GL_STATIC_DRAW);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferId);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
            
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 32, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 32, 12);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 32, 24);
            
            GL30.glBindVertexArray(0);

            instancedModels.put(model, new InstancedModelData(vertexBufferId, indexBufferId, instanceBufferId, vaoId, meshes));
        } catch (Exception e) {
            LOGGER.error("Failed to prepare instanced rendering for model: {}", e.getMessage());
        }
    }

    public static void clearInstances() {
        instanceBatches.clear();
    }

    public static void enqueueInstance(ObjModel model, Matrix4f transform, int light, int overlay) {
        enqueueInstance(model, transform, light, overlay, 0xFFFFFFFF);
    }

    public static void enqueueInstance(ObjModel model, Matrix4f transform, int light, int overlay, int color) {
        enqueueInstance(model, transform, light, overlay, color, false);
    }

    public static void enqueueInstance(ObjModel model, Matrix4f transform, int light, int overlay, int color, boolean interiorLit) {
        enqueueInstance(model, transform, light, overlay, color, interiorLit, "interior");
    }

    public static void enqueueInstance(ObjModel model, Matrix4f transform, int light, int overlay, int color, boolean interiorLit, String interiorPart) {
        if (model == null || transform == null) return;
        instanceBatches.computeIfAbsent(model, k -> new ArrayList<>()).add(new InstanceData(transform, light, overlay, color, interiorLit, interiorPart));
    }

    public static void flushInstances(VertexConsumerProvider consumers) {
        if (instanceBatches.isEmpty() || consumers == null) return;

        for (Map.Entry<ObjModel, List<InstanceData>> entry : instanceBatches.entrySet()) {
            ObjModel model = entry.getKey();
            List<InstanceData> instances = entry.getValue();
            if (model == null || instances == null || instances.isEmpty()) continue;

            InstancedModelData instancedData = instancedModels.get(model);
            if (instancedData == null) {
                renderInstancesLegacy(model, instances, consumers);
                continue;
            }

            renderInstancedBatch(model, instances, instancedData, consumers);
        }

        instanceBatches.clear();
    }

    private static void renderInstancedBatch(ObjModel model, List<InstanceData> instances, InstancedModelData instancedData, VertexConsumerProvider consumers) {
        if (instances.size() > MAX_INSTANCES_PER_BATCH) {
            List<List<InstanceData>> batches = new ArrayList<>();
            for (int i = 0; i < instances.size(); i += MAX_INSTANCES_PER_BATCH) {
                batches.add(instances.subList(i, Math.min(i + MAX_INSTANCES_PER_BATCH, instances.size())));
            }

            for (List<InstanceData> batch : batches) {
                renderInstancedBatch(model, batch, instancedData, consumers);
            }

            return;
        }

        GL30.glBindVertexArray(instancedData.vaoId);

        ByteBuffer instanceData = ByteBuffer.allocateDirect(instances.size() * 80);
        instanceData.order(ByteOrder.nativeOrder());
        FloatBuffer instanceBuffer = instanceData.asFloatBuffer();

        for (InstanceData instance : instances) {
            if (isOutsideRenderDistance(instance.transform)) continue;

            instance.transform.get(instanceBuffer);
            
            int meshLight = instance.light;
            if (instance.interiorLit) {
                meshLight = 0x00F000F0;
            }
            
            instanceBuffer.put(meshLight & 0xFFFF);
            instanceBuffer.put((meshLight >> 16) & 0xFFFF);
            instanceBuffer.put(instance.overlay & 0xFFFF);
            instanceBuffer.put((instance.overlay >> 16) & 0xFFFF);
            instanceBuffer.put(Float.intBitsToFloat(instance.color));
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instancedData.instanceBufferId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceData, GL15.GL_DYNAMIC_DRAW);

        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, 80, 0);
        GL20.glEnableVertexAttribArray(4);
        GL20.glVertexAttribPointer(4, 4, GL11.GL_FLOAT, false, 80, 16);
        GL20.glEnableVertexAttribArray(5);
        GL20.glVertexAttribPointer(5, 4, GL11.GL_FLOAT, false, 80, 32);
        GL20.glEnableVertexAttribArray(6);
        GL20.glVertexAttribPointer(6, 4, GL11.GL_FLOAT, false, 80, 48);
        GL20.glEnableVertexAttribArray(7);
        GL20.glVertexAttribPointer(7, 4, GL11.GL_FLOAT, false, 80, 64);

        GL33.glVertexAttribDivisor(3, 1);
        GL33.glVertexAttribDivisor(4, 1);
        GL33.glVertexAttribDivisor(5, 1);
        GL33.glVertexAttribDivisor(6, 1);
        GL33.glVertexAttribDivisor(7, 1);

        for (Map.Entry<String, MeshData> meshEntry : instancedData.meshes.entrySet()) {
            String meshKey = meshEntry.getKey();
            MeshData meshData = meshEntry.getValue();
            ObjModel.Material material = model.getMaterialForMesh(meshKey);

            if (material != null && material.texture != null) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, getTextureId(material.texture));
            }

            GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, meshData.indexCount, GL11.GL_UNSIGNED_INT, meshData.indexOffset * 4, instances.size());
        }

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void renderInstancesLegacy(ObjModel model, List<InstanceData> instances, VertexConsumerProvider consumers) {
        for (InstanceData instance : instances) {
            if (isOutsideRenderDistance(instance.transform)) continue;

            for (String meshKey : model.getMeshes().keySet()) {
                ObjModel.Material mat = model.getMaterialForMesh(meshKey);
                Identifier texture = (mat != null && mat.texture != null) ? mat.texture : Identifier.of("minecraft", "textures/block/stone.png");
                boolean translucent = mat != null && mat.opacity < 0.999f;
                RenderLayer layer = translucent ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutout(texture);
                VertexConsumer vc = consumers.getBuffer(layer);

                int r = (instance.color >> 16) & 0xFF;
                int g = (instance.color >> 8) & 0xFF;
                int b = instance.color & 0xFF;
                int a = (instance.color >> 24) & 0xFF;

                if (mat != null && mat.diffuseColor != null) {
                    r = (int)Math.max(0, Math.min(255, Math.round(mat.diffuseColor.x * r)));
                    g = (int)Math.max(0, Math.min(255, Math.round(mat.diffuseColor.y * g)));
                    b = (int)Math.max(0, Math.min(255, Math.round(mat.diffuseColor.z * b)));
                }

                if (mat != null) {
                    a = (int)Math.max(0, Math.min(255, Math.round(mat.opacity * a)));
                }

                int meshLight = instance.light;
                if (instance.interiorLit && meshKey.toLowerCase().contains(instance.interiorPart.toLowerCase())) {
                    meshLight = 0x00F000F0;
                }
                
                renderMeshWithColor(model.getMeshes().get(meshKey), vc, instance.transform, meshLight, instance.overlay, r, g, b, a);
            }
        }
    }

    public static void renderModel(ObjModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (model == null) return;

        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        if (isOutsideRenderDistance(matrix)) {
            matrices.pop();
            return;
        }
        for (String meshKey : model.getMeshes().keySet()) {
            ObjModel.Material material = model.getMaterialForMesh(meshKey);

            Identifier texture = (material != null && material.texture != null) ? material.texture : Identifier.of("minecraft", "textures/block/stone.png");
            boolean translucent = material != null && material.opacity < 0.999f;
            RenderLayer layer = translucent ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutout(texture);
            VertexConsumer vc = consumers.getBuffer(layer);

            int r = 255;
            int g = 255;
            int b = 255;
            int a = 255;

            if (material != null && material.diffuseColor != null) {
                r = (int)Math.max(0, Math.min(255, Math.round(material.diffuseColor.x * 255f)));
                g = (int)Math.max(0, Math.min(255, Math.round(material.diffuseColor.y * 255f)));
                b = (int)Math.max(0, Math.min(255, Math.round(material.diffuseColor.z * 255f)));
            }

            if (material != null) {
                a = (int)Math.max(0, Math.min(255, Math.round(material.opacity * 255f)));
            }

            renderMeshWithColor(model.getMeshes().get(meshKey), vc, matrix, light, overlay, r, g, b, a);
        }

        matrices.pop();
    }

    public static void renderModelWithInteriorLighting(ObjModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, boolean interiorLit) {
        renderModelWithInteriorLighting(model, matrices, consumers, light, overlay, interiorLit, "interior");
    }

    public static void renderModelWithInteriorLighting(ObjModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, boolean interiorLit, String interiorPart) {
        if (model == null) return;

        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        if (isOutsideRenderDistance(matrix)) {
            matrices.pop();
            return;
        }
        
        for (String meshKey : model.getMeshes().keySet()) {
            ObjModel.Material material = model.getMaterialForMesh(meshKey);

            Identifier texture = (material != null && material.texture != null) ? material.texture : Identifier.of("minecraft", "textures/block/stone.png");
            boolean translucent = material != null && material.opacity < 0.999f;
            RenderLayer layer = translucent ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutout(texture);
            VertexConsumer vc = consumers.getBuffer(layer);
            
            int meshLight = light;

            if (interiorLit && meshKey.toLowerCase().contains(interiorPart.toLowerCase())) {
                meshLight = 0x00F000F0;
            }

            int r = 255;
            int g = 255;
            int b = 255;
            int a = 255;

            if (material != null && material.diffuseColor != null) {
                r = (int)Math.max(0, Math.min(255, Math.round(material.diffuseColor.x * 255f)));
                g = (int)Math.max(0, Math.min(255, Math.round(material.diffuseColor.y * 255f)));
                b = (int)Math.max(0, Math.min(255, Math.round(material.diffuseColor.z * 255f)));
            }

            if (material != null) {
                a = (int)Math.max(0, Math.min(255, Math.round(material.opacity * 255f)));
            }
            
            renderMeshWithColor(model.getMeshes().get(meshKey), vc, matrix, meshLight, overlay, r, g, b, a);
        }

        matrices.pop();
    }

    public static void renderModelWithDoorAnimation(ObjModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, double doorOffset, String slideAxis, List<TrainConfigLoader.DoorPart> parts, String instanceKey, boolean interiorLit) {
        renderModelWithDoorAnimation(model, matrices, consumers, light, overlay, doorOffset, slideAxis, parts, instanceKey, interiorLit, "interior");
    }

    public static void renderModelWithDoorAnimation(ObjModel model, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, double doorOffset, String slideAxis, List<TrainConfigLoader.DoorPart> parts, String instanceKey, boolean interiorLit, String interiorPart) {
        if (model == null) return;

        matrices.push();
        for (String meshKey : model.getMeshes().keySet()) {
            TrainConfigLoader.DoorPart matchingDoorPart = findMatchingDoorPart(meshKey, parts);

            if (matchingDoorPart != null) {
                matrices.push();
                applyDoorAnimation(model, matrices, meshKey, slideAxis, doorOffset, matchingDoorPart, instanceKey);
                
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                int meshLight = light;
                if (interiorLit && meshKey.toLowerCase().contains(interiorPart.toLowerCase())) {
                    meshLight = 0x00F000F0;
                }
                
                renderMaterialGroup(model, meshKey, consumers, matrix, meshLight, overlay);
                matrices.pop();
            } else {
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                int meshLight = light;
                if (interiorLit && meshKey.toLowerCase().contains(interiorPart.toLowerCase())) {
                    meshLight = 0x00F000F0;
                }

                renderMaterialGroup(model, meshKey, consumers, matrix, meshLight, overlay);
            }
        }

        matrices.pop();
    }

    private static TrainConfigLoader.DoorPart findMatchingDoorPart(String meshKey, List<TrainConfigLoader.DoorPart> doorParts) {
        for (TrainConfigLoader.DoorPart doorPart : doorParts) {
            if (meshKey.contains(doorPart.partName())) {
                return doorPart;
            }
        }
        return null;
    }

    private static void applyDoorAnimation(ObjModel model, MatrixStack matrices, String meshKey, String slideAxis, double doorOffset, TrainConfigLoader.DoorPart doorPart, String instanceKey) {
        String doorType = doorPart.type();
        String doorDirection = doorPart.direction();
        boolean isLeftSide = "LEFT".equals(doorPart.side());

        if ("SLIDE".equals(doorType)) {
            handleSlideDoorAnimation(model, matrices, meshKey, slideAxis, doorOffset, doorDirection, isLeftSide, instanceKey);
        } else if ("POP_SLIDE".equals(doorType)) {
            handlePopSlideDoorAnimation(model, matrices, meshKey, slideAxis, doorOffset, doorDirection, isLeftSide, instanceKey);
        }
    }

    private static void handleSlideDoorAnimation(ObjModel model, MatrixStack matrices, String meshKey, String slideAxis, double doorOffset, String doorDirection, boolean isLeftSide, String instanceKey) {
        float targetAnimationValue = doorOffset > 0 ? 1.0f : 0.0f;
        String doorAnimationKey = instanceKey + "|" + meshKey + "_SLIDE_" + doorDirection + "_" + (isLeftSide ? "LEFT" : "RIGHT");
        
        float currentAnimationValue = getCurrentDoorAnimationValue(doorAnimationKey, targetAnimationValue);
        char axis = slideAxis.equals("X") ? 'X' : 'Z';
        double doorWidth = calculateDoorWidth(model, meshKey, axis);
        double slideOffset = currentAnimationValue * doorWidth * (isLeftSide ? -1.0 : 1.0);
        
        if ("NEGATIVE".equals(doorDirection)) {
            slideOffset = -slideOffset;
        }

        if ("X".equals(slideAxis)) {
            matrices.translate(slideOffset, 0, 0);
        } else {
            matrices.translate(0, 0, slideOffset);
        }
    }

    private static void handlePopSlideDoorAnimation(ObjModel model, MatrixStack matrices, String meshKey, String slideAxis, double doorOffset, String doorDirection, boolean isLeftSide, String instanceKey) {
        String doorAnimationKey = instanceKey + "|" + meshKey + "_POP_SLIDE_" + doorDirection + "_" + (isLeftSide ? "LEFT" : "RIGHT");
        float targetOpenValue = doorOffset > 0 ? 1.0f : 0.0f;

        long currentTime = System.currentTimeMillis();
        Long lastUpdateTime = doorLastUpdateTimes.get(doorAnimationKey);
        float deltaTime = lastUpdateTime == null ? 0f : Math.max(0f, Math.min(0.1f, (currentTime - lastUpdateTime) / 1000f));
        doorLastUpdateTimes.put(doorAnimationKey, currentTime);

        float popValue = doorPopStates.getOrDefault(doorAnimationKey, 0f);
        float slideValue = doorSlideStates.getOrDefault(doorAnimationKey, 0f);
        float popSpeed = 1.0f / (POP_DURATION_MS / 1000.0f);
        float slideSpeed = 1.0f / (SLIDE_DURATION_MS / 1000.0f);

        if (targetOpenValue >= 0.5f) {
            popValue = Math.min(1.0f, popValue + deltaTime * popSpeed);
            if (popValue >= 1.0f) {
                slideValue = Math.min(1.0f, slideValue + deltaTime * slideSpeed);
            }
        } else {
            if (slideValue > 0f) {
                slideValue = Math.max(0.0f, slideValue - deltaTime * slideSpeed);
            } else {
                popValue = Math.max(0.0f, popValue - deltaTime * popSpeed);
            }
        }

        doorPopStates.put(doorAnimationKey, popValue);
        doorSlideStates.put(doorAnimationKey, slideValue);

        if (popValue > 0) {
            ObjModel.RenderData renderData = model.getMeshes().get(meshKey);
            double averageX = renderData.vertices.stream().mapToDouble(v -> v.x).average().orElse(0);
            double outwardDirection = Math.signum(averageX);
            if (outwardDirection == 0.0) outwardDirection = isLeftSide ? -1.0 : 1.0;
            double popOffset = popValue * 0.06 * outwardDirection;
            matrices.translate(popOffset, 0, 0);
        }

        if (slideValue > 0) {
            double zWidth = calculateDoorWidth(model, meshKey, 'Z');
            double xWidth = calculateDoorWidth(model, meshKey, 'X');
            boolean shouldUseXAxis = zWidth < xWidth * 0.15;

            double slideOffset = slideValue * (shouldUseXAxis ? xWidth * (isLeftSide ? -1.0 : 1.0) : zWidth);
            if ("NEGATIVE".equals(doorDirection)) slideOffset = -slideOffset;

            if (shouldUseXAxis) {
                matrices.translate(slideOffset, 0, 0);
            } else {
                matrices.translate(0, 0, slideOffset);
            }
        }
    }

    private static float getCurrentDoorAnimationValue(String doorKey, float targetValue) {
        Float currentValue = doorAnimationStates.get(doorKey);
        if (currentValue == null) currentValue = 0.0f;
        
        Float previousTarget = doorAnimationTargets.get(doorKey);
        if (previousTarget == null || Math.abs(previousTarget - targetValue) > 0.0001f) {
            doorAnimationStartTimes.put(doorKey, System.currentTimeMillis());
            doorAnimationTargets.put(doorKey, targetValue);
        }

        Long startTime = doorAnimationStartTimes.get(doorKey);
        if (startTime != null) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            float animationProgress = Math.min(1.0f, elapsedTime / DOOR_ANIMATION_DURATION);
            float smoothProgress = animationProgress * animationProgress * (3.0f - 2.0f * animationProgress);
            
            currentValue = currentValue * (1.0f - smoothProgress) + targetValue * smoothProgress;
            doorAnimationStates.put(doorKey, currentValue);
        }

        return currentValue;
    }

    private static double calculateDoorWidth(ObjModel model, String meshKey, char axis) {
        ObjModel.RenderData renderData = model.getMeshes().get(meshKey);
        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;

        if (axis == 'Z') {
            for (var vertex : renderData.vertices) {
                if (vertex.z < minValue) minValue = vertex.z;
                if (vertex.z > maxValue) maxValue = vertex.z;
            }
        } else {
            for (var vertex : renderData.vertices) {
                if (vertex.x < minValue) minValue = vertex.x;
                if (vertex.x > maxValue) maxValue = vertex.x;
            }
        }

        return Math.abs(maxValue - minValue);
    }

    private static void renderMaterialGroup(ObjModel model, String name, VertexConsumerProvider consumers, Matrix4f matrix, int light, int overlay) {
        ObjModel.Material mat = model.getMaterialForMesh(name);

        Identifier texture = (mat != null && mat.texture != null) ? mat.texture : Identifier.of("minecraft", "textures/block/stone.png");
        boolean translucent = mat != null && mat.opacity < 0.999f;
        RenderLayer layer = translucent ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutout(texture);
        VertexConsumer vc = consumers.getBuffer(layer);

        int r = 255;
        int g = 255;
        int b = 255;
        int a = 255;

        if (mat != null && mat.diffuseColor != null) {
            r = (int)Math.max(0, Math.min(255, Math.round(mat.diffuseColor.x * 255f)));
            g = (int)Math.max(0, Math.min(255, Math.round(mat.diffuseColor.y * 255f)));
            b = (int)Math.max(0, Math.min(255, Math.round(mat.diffuseColor.z * 255f)));
        }

        if (mat != null) {
            a = (int)Math.max(0, Math.min(255, Math.round(mat.opacity * 255f)));
        }

        renderMeshWithColor(model.getMeshes().get(name), vc, matrix, light, overlay, r, g, b, a);
    }

    public static void renderMesh(ObjModel.RenderData data, VertexConsumer vertexConsumer, Matrix4f matrix, int light, int overlay) {
        var vertices = data.vertices;
        var normals = data.normals;
        var uvs = data.texCoords;

        if (isOutsideRenderDistance(matrix)) return;

        for (int i = 0; i < vertices.size(); i++) {
            var pos = vertices.get(i);
            var normal = normals.get(i);
            float u = uvs.get(i * 2);
            float v = uvs.get(i * 2 + 1);
            
            vertexConsumer.vertex(matrix, pos.x, pos.y, pos.z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal.x, normal.y, normal.z);
        }
    }

    private static void renderMeshWithColor(ObjModel.RenderData data, VertexConsumer vertexConsumer, Matrix4f matrix, int light, int overlay, int r, int g, int b, int a) {
        var vertices = data.vertices;
        var normals = data.normals;
        var uvs = data.texCoords;
        if (isOutsideRenderDistance(matrix)) return;
        for (int i = 0; i < vertices.size(); i++) {
            var pos = vertices.get(i);
            var normal = normals.get(i);
            float u = uvs.get(i * 2);
            float v = uvs.get(i * 2 + 1);
            vertexConsumer.vertex(matrix, pos.x, pos.y, pos.z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal.x, normal.y, normal.z);
        }
    }

    private static boolean isOutsideRenderDistance(Matrix4f modelMatrix) {
        try {
            int chunks = client.options.getViewDistance().getValue();
            float max = chunks * 16.0f + 32.0f;
            float x = modelMatrix.m30();
            float z = modelMatrix.m32();
            return x * x + z * z > max * max;
        } catch (Throwable t) {
            LOGGER.warn("Failed to check render distance", t);
            return false;
        }
    }

    private static void addToCache(String key, ObjModel model) {
        if (modelCache.size() >= MAX_CACHE_SIZE) {
            Iterator<Map.Entry<String, ObjModel>> iterator = modelCache.entrySet().iterator();

            iterator.next();
            iterator.remove();
        }

        modelCache.put(key, model);
    }

    public static void clearCache() {
        for (InstancedModelData data : instancedModels.values()) {
            data.cleanup();
        }

        instancedModels.clear();
        modelCache.clear();
    }

    public static void removeFromCache(String modId, String objPath, String mtlPath) {
        modelCache.remove(modId + ":" + objPath + ":" + (mtlPath != null ? mtlPath : ""));
    }

    public static void invalidateCache(String modId, String objPath) {
        String prefix = modId + ":" + objPath + ":";
        modelCache.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    public static int getCacheSize() {
        return modelCache.size();
    }

    public static int getInstanceCount() {
        return instanceBatches.values().stream().mapToInt(List::size).sum();
    }

    public static boolean hasInstancedRendering(ObjModel model) {
        return instancedModels.containsKey(model);
    }

    private static int getTextureId(Identifier texture) {
        try {
            var abstractTexture = client.getTextureManager().getTexture(texture);
            var gpuTexture = abstractTexture.getGlTexture();

            if (gpuTexture instanceof GlTexture glTexture) {
                return glTexture.getGlId();
            }

            return 0;
        } catch (Exception e) {
            LOGGER.warn("Failed to get texture ID for: {}", texture, e);
            return 0;
        }
    }
}
