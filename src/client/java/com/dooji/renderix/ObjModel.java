package com.dooji.renderix;

import de.javagl.obj.*;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjModel {
    public static class RenderData {
        public final List<Vector3f> vertices;
        public final List<Vector3f> normals;
        public final List<Float> texCoords;
        public final String materialName;

        public RenderData(List<Vector3f> vertices, List<Vector3f> normals, List<Float> texCoords, String materialName) {
            this.vertices = vertices;
            this.normals = normals;
            this.texCoords = texCoords;
            this.materialName = materialName;
        }
    }

    public static class Material {
        public final String name;
        public final Identifier texture;
        public final Vector3f diffuseColor;
        public final float opacity;
        
        public Material(String name, Identifier texture, Vector3f diffuseColor, float opacity) {
            this.name = name;
            this.texture = texture;
            this.diffuseColor = diffuseColor != null ? diffuseColor : new Vector3f(1.0f, 1.0f, 1.0f);
            this.opacity = opacity;
        }
    }

    private final Map<String, RenderData> meshes;
    private final Map<String, Material> materials;
    private final Vector3f boundingBoxMin;
    private final Vector3f boundingBoxMax;
    private final boolean flipV;
    private final Obj rawObj;
    
    public ObjModel(Obj obj, Map<String, Mtl> mtlMap, String modId, String objPath, boolean flipV) {
        this.meshes = new HashMap<>();
        this.materials = new HashMap<>();
        this.flipV = flipV;
        this.rawObj = obj;

        String basePath = extractBasePath(objPath);

        for (Map.Entry<String, Mtl> entry : mtlMap.entrySet()) {
            Mtl mtl = entry.getValue();
            Identifier textureId = null;

            String texturePath = mtl.getMapKd();
            if (texturePath != null && !texturePath.isEmpty()) {
                textureId = resolveTexturePath(texturePath, modId, basePath);
            }

            Vector3f color = null;
            if (mtl.getKd() != null) {
                color = new Vector3f(mtl.getKd().getX(), mtl.getKd().getY(), mtl.getKd().getZ());
            }

            float opacity = 1.0f;
            try {
                opacity = mtl.getD();
            } catch (Throwable t) {
                opacity = 1.0f;
            }

            materials.put(mtl.getName(), new Material(mtl.getName(), textureId, color, opacity));
        }

        Obj renderableObj = ObjUtils.convertToRenderable(obj);
        
        Map<String, Obj> objectGroups = ObjSplitting.splitByGroups(renderableObj);
        
        if (!objectGroups.isEmpty()) {
            for (Map.Entry<String, Obj> entry : objectGroups.entrySet()) {
                String objectName = entry.getKey();
                Obj objectObj = entry.getValue();
                
                Map<String, Obj> materialGroups = ObjSplitting.splitByMaterialGroups(objectObj);
                
                for (Map.Entry<String, Obj> materialEntry : materialGroups.entrySet()) {
                    String materialName = materialEntry.getKey();
                    Obj materialObj = materialEntry.getValue();
                    
                    if (materialObj.getNumFaces() == 0) continue;
                    
                    int estimatedVertexCount = Math.max(1, materialObj.getNumFaces()) * 4;
                    List<Vector3f> vertices = new ArrayList<>(estimatedVertexCount);
                    List<Vector3f> normals = new ArrayList<>(estimatedVertexCount);
                    List<Float> texCoords = new ArrayList<>(estimatedVertexCount * 2);
                    
                    for (int i = 0; i < materialObj.getNumFaces(); i++) {
                        ObjFace face = materialObj.getFace(i);
                        int numVertices = face.getNumVertices();
                        if (numVertices < 3) continue;

                        Vector3f fallbackNormal = face.containsNormalIndices() ? null : calculateFaceNormal(materialObj, face);
                        for (int j = 0; j < numVertices; j++) {
                            addDeindexedVertex(materialObj, face, j, vertices, normals, texCoords, fallbackNormal);
                        }

                        if (numVertices == 3) {
                            addDeindexedVertex(materialObj, face, 2, vertices, normals, texCoords, fallbackNormal);
                        }
                    }
                    
                    String meshKey = objectName + "_" + materialName;
                    meshes.put(meshKey, new RenderData(vertices, normals, texCoords, materialName));
                }
            }
        } else {
            Map<String, Obj> materialGroups = ObjSplitting.splitByMaterialGroups(renderableObj);
            
            for (Map.Entry<String, Obj> entry : materialGroups.entrySet()) {
                String materialName = entry.getKey();
                Obj materialObj = entry.getValue();
                
                int estimatedVertexCount = Math.max(1, materialObj.getNumFaces()) * 4;
                List<Vector3f> vertices = new ArrayList<>(estimatedVertexCount);
                List<Vector3f> normals = new ArrayList<>(estimatedVertexCount);
                List<Float> texCoords = new ArrayList<>(estimatedVertexCount * 2);
                
                for (int i = 0; i < materialObj.getNumFaces(); i++) {
                    ObjFace face = materialObj.getFace(i);
                    int numVertices = face.getNumVertices();
                    if (numVertices < 3) continue;

                    Vector3f fallbackNormal = face.containsNormalIndices() ? null : calculateFaceNormal(materialObj, face);
                    for (int j = 0; j < numVertices; j++) {
                        addDeindexedVertex(materialObj, face, j, vertices, normals, texCoords, fallbackNormal);
                    }

                    if (numVertices == 3) {
                        addDeindexedVertex(materialObj, face, 2, vertices, normals, texCoords, fallbackNormal);
                    }
                }
                
                meshes.put(materialName, new RenderData(vertices, normals, texCoords, materialName));
            }
        }

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < obj.getNumVertices(); i++) {
            FloatTuple vertex = obj.getVertex(i);
            minX = Math.min(minX, vertex.getX());
            minY = Math.min(minY, vertex.getY());
            minZ = Math.min(minZ, vertex.getZ());
            maxX = Math.max(maxX, vertex.getX());
            maxY = Math.max(maxY, vertex.getY());
            maxZ = Math.max(maxZ, vertex.getZ());
        }

        this.boundingBoxMin = new Vector3f(minX, minY, minZ);
        this.boundingBoxMax = new Vector3f(maxX, maxY, maxZ);
    }

    private String extractBasePath(String objPath) {
        int lastSlash = objPath.lastIndexOf('/');
        return lastSlash != -1 ? objPath.substring(0, lastSlash + 1) : "";
    }

    private Identifier resolveTexturePath(String texturePath, String modId, String basePath) {
        String path = texturePath.replace('\\', '/');
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');

        boolean hasExtension = lastDot > lastSlash;
        String finalPath = hasExtension ? path : path + ".png";

        if (path.contains("/")) {
            return Identifier.of(modId, finalPath);
        } else {
            return Identifier.of(modId, basePath + finalPath);
        }
    }

    private void addDeindexedVertex(Obj obj, ObjFace face, int vertexIndexInFace, List<Vector3f> vertices, List<Vector3f> normals, List<Float> texCoords, Vector3f fallbackNormal) {
        int posIndex = face.getVertexIndex(vertexIndexInFace);
        int normalIndex = face.containsNormalIndices() ? face.getNormalIndex(vertexIndexInFace) : -1;
        int texCoordIndex = face.containsTexCoordIndices() ? face.getTexCoordIndex(vertexIndexInFace) : -1;

        FloatTuple vertex = obj.getVertex(posIndex);
        vertices.add(new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ()));

        if (normalIndex != -1) {
            FloatTuple normal = obj.getNormal(normalIndex);
            normals.add(new Vector3f(normal.getX(), normal.getY(), normal.getZ()));
        } else {
            normals.add(fallbackNormal != null ? new Vector3f(fallbackNormal) : calculateFaceNormal(obj, face));
        }

        if (texCoordIndex != -1) {
            FloatTuple texCoord = obj.getTexCoord(texCoordIndex);
            texCoords.add(texCoord.getX());
            texCoords.add(flipV ? 1.0f - texCoord.getY() : texCoord.getY());
        } else {
            texCoords.add(0.0f);
            texCoords.add(0.0f);
        }
    }

    private Vector3f calculateFaceNormal(Obj obj, ObjFace face) {
        if (face.getNumVertices() < 3) {
            return new Vector3f(0, 1, 0);
        }

        FloatTuple v0 = obj.getVertex(face.getVertexIndex(0));
        FloatTuple v1 = obj.getVertex(face.getVertexIndex(1));
        FloatTuple v2 = obj.getVertex(face.getVertexIndex(2));

        Vector3f edge1 = new Vector3f(
            v1.getX() - v0.getX(),
            v1.getY() - v0.getY(),
            v1.getZ() - v0.getZ()
        );

        Vector3f edge2 = new Vector3f(
            v2.getX() - v0.getX(),
            v2.getY() - v0.getY(),
            v2.getZ() - v0.getZ()
        );

        Vector3f normal = new Vector3f();
        edge1.cross(edge2, normal);
        normal.normalize();

        return normal;
    }

    public Map<String, RenderData> getMeshes() {
        return meshes;
    }

    public Map<String, Material> getMaterials() {
        return materials;
    }

    public Material getMaterial(String name) {
        if (materials.containsKey(name)) {
            return materials.get(name);
        }

        if (meshes.containsKey(name)) {
            RenderData data = meshes.get(name);
            return materials.get(data.materialName);
        }

        if (name.contains("_")) {
            String materialName = name.substring(name.lastIndexOf("_") + 1);
            Material mat = materials.get(materialName);
            if (mat != null) return mat;
        }

        return null;
    }

    public Material getMaterialForMesh(String meshKey) {
        RenderData data = meshes.get(meshKey);
        if (data == null) return null;
        
        return materials.get(data.materialName);
    }

    public Vector3f getBoundingBoxMin() {
        return boundingBoxMin;
    }

    public Vector3f getBoundingBoxMax() {
        return boundingBoxMax;
    }
    
    public Obj getRawObj() {
        return rawObj;
    }
}
