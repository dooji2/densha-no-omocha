package com.dooji.dno.train;

import com.dooji.dno.TrainMod;
import com.dooji.dno.TrainModClient;
import com.dooji.renderix.ObjModel;
import com.dooji.renderix.Renderix;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class TrainConfigLoader {
    private static final Map<String, TrainTypeData> TRAIN_TYPES = new HashMap<>();
    private static final Gson GSON = new Gson();

    public record TrainTypeData(
        String name,
        double length,
        double bogieInset,
        String icon,
        String description,
        boolean flipV,
        boolean isReversed,
        double heightOffset,
        String model,
        DoorConfig doors,
        BogieConfig bogies,
        double width,
        double height
    ) {}

    public record DoorPart(
        String partName, // obj part name (e.g., "d1l", "D_L_F")
        String side, // "LEFT" or "RIGHT"
        String direction, // "POSITIVE" or "NEGATIVE"
        String type // "SLIDE", "POP_SLIDE", "SWING", "POCKET"
    ) {}

    public record DoorConfig(
        boolean hasDoors,
        String slideDirection, // "X" for side-to-side, "Z" for front-back
        List<DoorPart> doorParts
    ) {
        public double calculateDoorWidth(String modelPath) {
            if (!hasDoors || doorParts.isEmpty() || modelPath == null) {
                return 0.0;
            }

            double maxWidth = 0.0;

            try {
                String modId = TrainMod.MOD_ID;
                String objPath = modelPath;

                if (modelPath.contains(":")) {
                    String[] parts = modelPath.split(":", 2);
                    modId = parts[0];
                    objPath = parts[1];
                }

                ObjModel model = Renderix.loadModel(modId, objPath);
                if (model != null) {
                    Map<String, ObjModel.RenderData> meshes = model.getMeshes();

                    for (DoorPart doorPart : doorParts) {
                        String partName = doorPart.partName();

                        for (String meshKey : meshes.keySet()) {
                            if (meshKey.contains(partName)) {
                                ObjModel.RenderData meshData = meshes.get(meshKey);
                                List<Vector3f> vertices = meshData.vertices;

                                if (!vertices.isEmpty()) {
                                    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
                                    float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

                                    for (Vector3f vertex : vertices) {
                                        minX = Math.min(minX, vertex.x);
                                        minY = Math.min(minY, vertex.y);
                                        minZ = Math.min(minZ, vertex.z);
                                        maxX = Math.max(maxX, vertex.x);
                                        maxY = Math.max(maxY, vertex.y);
                                        maxZ = Math.max(maxZ, vertex.z);
                                    }

                                    if ("Z".equals(slideDirection)) {
                                        double width = Math.abs(maxZ - minZ);
                                        maxWidth = Math.max(maxWidth, width);
                                    } else {
                                        double width = Math.abs(maxX - minX);
                                        maxWidth = Math.max(maxWidth, width);
                                    }
                                }

                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                TrainModClient.LOGGER.error("Failed to calculate door width for model: {}", modelPath, e);
            }

            // in case calc failed
            return maxWidth > 0.0 ? maxWidth : 0.5;
        }
    }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(TrainMod.MOD_ID, "train_types");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        loadTrainTypes(manager);
                    }
                });
    }

    private static void loadTrainTypes(ResourceManager manager) {
        TRAIN_TYPES.clear();

        try {
            List<Resource> resources = manager.getAllResources(Identifier.of(TrainMod.MOD_ID, "trains.json"));
            Map<String, JsonObject> combinedTrains = new HashMap<>();

            for (Resource resource : resources) {
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);

                    for (Map.Entry<String, com.google.gson.JsonElement> trainEntry : root.entrySet()) {
                        String trainId = trainEntry.getKey();
                        JsonObject trainData = trainEntry.getValue().getAsJsonObject();
                        String fullTrainId = TrainMod.MOD_ID + ":" + trainId;

                        if (!combinedTrains.containsKey(fullTrainId)) combinedTrains.put(fullTrainId, trainData);
                    }
                } catch (Exception e) {
                    TrainModClient.LOGGER.error("Failed to load train resource: {}", resource.getPackId(), e);
                }
            }

            for (Map.Entry<String, JsonObject> entry : combinedTrains.entrySet()) {
                String trainId = entry.getKey();
                JsonObject trainData = entry.getValue();

                try {
                    String name = trainData.has("name") ? trainData.get("name").getAsString() : trainId;
                    double length = trainData.has("length") ? trainData.get("length").getAsDouble() : 25.0;
                    String icon = trainData.has("icon") ? trainData.get("icon").getAsString() : "";
                    double bogieInset = trainData.has("bogieInset") ? Math.max(0.0, Math.min(0.49, trainData.get("bogieInset").getAsDouble())) : 0.1;
                    String description = trainData.has("description") ? trainData.get("description").getAsString() : "";
                    boolean flipV = trainData.has("flipv") ? trainData.get("flipv").getAsBoolean() : false;
                    boolean isReversed = trainData.has("isReversed") ? trainData.get("isReversed").getAsBoolean() : false;
                    double heightOffset = trainData.has("heightOffset") ? trainData.get("heightOffset").getAsDouble() : 0.0;
                    String model = trainData.has("model") ? trainData.get("model").getAsString() : "";
                    
                    DoorConfig doors = parseDoorConfig(trainData);
                    BogieConfig bogies = parseBogieConfig(trainData);
                    double width = trainData.has("width") ? trainData.get("width").getAsDouble() : 1.0;
                    double height = trainData.has("height") ? trainData.get("height").getAsDouble() : 1.0;
                    TrainTypeData trainTypeData = new TrainTypeData(name, length, bogieInset, icon, description, flipV, isReversed, heightOffset, model, doors, bogies, width, height);
                    
                    TRAIN_TYPES.put(trainId, trainTypeData);
                } catch (Exception e) {
                    TrainModClient.LOGGER.error("Failed to parse train data for: {}", trainId, e);
                }
            }
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to load train types from resources", e);
        }
    }

    public static Map<String, TrainTypeData> getTrainTypes() {
        return TRAIN_TYPES;
    }

    public static TrainTypeData getTrainType(String trainId) {
        return TRAIN_TYPES.get(trainId);
    }

    public static void clearCaches() {
        TRAIN_TYPES.clear();
    }

    private static DoorConfig parseDoorConfig(JsonObject trainData) {
        if (!trainData.has("doors")) {
            return new DoorConfig(false, "X", new ArrayList<>());
        }

        JsonObject doorsData = trainData.getAsJsonObject("doors");

        boolean hasDoors = doorsData.has("enabled") ? doorsData.get("enabled").getAsBoolean() : false;
        if (!hasDoors) {
            return new DoorConfig(false, "X", new ArrayList<>());
        }

        String slideDirection = doorsData.has("slideDirection") ? doorsData.get("slideDirection").getAsString() : "X";

        List<DoorPart> doorParts = new ArrayList<>();
        if (doorsData.has("doorParts")) {
            JsonArray doorPartsArray = doorsData.getAsJsonArray("doorParts");
            for (JsonElement element : doorPartsArray) {
                JsonObject doorPartObj = element.getAsJsonObject();
                String partName = doorPartObj.has("partName") ? doorPartObj.get("partName").getAsString() : "";
                String side = doorPartObj.has("side") ? doorPartObj.get("side").getAsString() : "LEFT";
                String direction = doorPartObj.has("direction") ? doorPartObj.get("direction").getAsString() : "NEGATIVE";
                String type = doorPartObj.has("type") ? doorPartObj.get("type").getAsString() : "SLIDE";

                doorParts.add(new DoorPart(partName, side, direction, type));
            }
        }

        return new DoorConfig(true, slideDirection, doorParts);
    }

    public record BogiePart(
        String model,
        String position,
        List<String> wheels,
        double inset
    ) {}

    public record BogieConfig(
        List<BogiePart> bogieParts
    ) {}

    private static BogieConfig parseBogieConfig(JsonObject trainData) {
        if (!trainData.has("bogies")) {
            return new BogieConfig(new ArrayList<>());
        }

        List<BogiePart> parts = new ArrayList<>();
        JsonArray arr = trainData.getAsJsonArray("bogies");
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String model = o.has("model") ? o.get("model").getAsString() : "";
            String position = o.has("position") ? o.get("position").getAsString() : "";
            
            List<String> wheels = new ArrayList<>();
            if (o.has("wheels")) {
                JsonArray wheelsArray = o.getAsJsonArray("wheels");
                for (JsonElement wheelElement : wheelsArray) {
                    wheels.add(wheelElement.getAsString());
                }
            }
            double inset = o.has("inset") ? o.get("inset").getAsDouble() : -1.0;
            parts.add(new BogiePart(model, position, wheels, inset));
        }

        return new BogieConfig(parts);
    }
}
