package com.dooji.dno.train;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.util.*;

import com.dooji.dno.TrainModClient;

public class TrainCompatibilityLoader {
    private static record CarriageModel(String modelPath, String carriageId, boolean isReversed) {}
    private static final Gson GSON = new Gson();
    private static final Map<String, TrainConfigLoader.TrainTypeData> MTR_TRAINS = new HashMap<>();

    public static void loadMTRTrains(ResourceManager manager) {
        MTR_TRAINS.clear();

        try {
            List<Resource> resources = manager.getAllResources(Identifier.of("mtr", "mtr_custom_resources.json"));
            
            for (Resource resource : resources) {
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    
                    if (root.has("custom_trains")) {
                        JsonObject customTrains = root.getAsJsonObject("custom_trains");
                        
                        for (Map.Entry<String, JsonElement> entry : customTrains.entrySet()) {
                            String trainId = entry.getKey();
                            JsonObject trainData = entry.getValue().getAsJsonObject();
                            
                            try {
                                List<TrainConfigLoader.TrainTypeData> trainTypes = parseMTRTrain(trainId, trainData, manager);
                                for (TrainConfigLoader.TrainTypeData trainType : trainTypes) {
                                    String carriageId = extractCarriageId(trainType.model());
                                    MTR_TRAINS.put("mtr:" + trainId + "_" + carriageId, trainType);
                                }
                            } catch (Exception e) {
                                TrainModClient.LOGGER.error("Failed to parse MTR train: {}", trainId, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    TrainModClient.LOGGER.error("Failed to load MTR resource: {}", resource.getPackId(), e);
                }
            }
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to load MTR trains", e);
        }
    }

    private static List<TrainConfigLoader.TrainTypeData> parseMTRTrain(String trainId, JsonObject trainData, ResourceManager manager) {
        String name = trainData.has("name") ? trainData.get("name").getAsString() : trainId;
        String description = trainData.has("description") ? trainData.get("description").getAsString() : "";

        if (description.length() > 50) {
            description = description.substring(0, 47) + "...";
        }

        double heightOffset = trainData.has("rider_offset") ? trainData.get("rider_offset").getAsDouble() : 0.0;
        String modelString = trainData.has("model") ? trainData.get("model").getAsString() : "";
        String modelPropertiesPath = trainData.has("model_properties") ? trainData.get("model_properties").getAsString() : "";

        if (modelString.isEmpty()) {
            return null;
        }

        List<CarriageModel> carriageModels = parseMTRModelString(modelString);
        if (carriageModels.isEmpty()) {
            return null;
        }

        JsonObject modelProperties = loadModelProperties(manager, modelPropertiesPath);
        
        double length = 25.0;
        double width = 2.0;
        double height = 2.0;
        List<String> doorPartNames = new ArrayList<>();
        String interiorPart = "int";
        String exteriorPart = "ext";

        if (modelProperties != null) {
            length = modelProperties.has("length") ? modelProperties.get("length").getAsDouble() : 25.0;
            width = modelProperties.has("width") ? modelProperties.get("width").getAsDouble() : 2.0;
            
            if (modelProperties.has("parts")) {
                JsonArray parts = modelProperties.getAsJsonArray("parts");
                for (JsonElement part : parts) {
                    JsonObject partObj = part.getAsJsonObject();
                    if (partObj.has("name") && partObj.has("stage") && partObj.has("door_offset")) {
                        String stage = partObj.get("stage").getAsString();
                        String doorOffset = partObj.get("door_offset").getAsString();
                        
                        if ("NONE".equals(doorOffset)) {
                            if ("INTERIOR".equals(stage)) {
                                interiorPart = partObj.get("name").getAsString().toLowerCase();
                            } else if ("EXTERIOR".equals(stage)) {
                                exteriorPart = partObj.get("name").getAsString().toLowerCase();
                            }
                        }
                    }
                    if (partObj.has("name") && partObj.has("door_offset")) {
                        String doorOffset = partObj.get("door_offset").getAsString();
                        if (!"NONE".equals(doorOffset)) {
                            doorPartNames.add(partObj.get("name").getAsString() + ":" + doorOffset);
                        }
                    }
                }
            }
        }

        TrainConfigLoader.DoorConfig doors = createDoorConfigFromParts(doorPartNames);
        TrainConfigLoader.BogieConfig bogies = new TrainConfigLoader.BogieConfig(new ArrayList<>());

        List<TrainConfigLoader.TrainTypeData> trainTypes = new ArrayList<>();
        for (CarriageModel carriage : carriageModels) {
            String carriageName = name + " " + carriage.carriageId();
            trainTypes.add(new TrainConfigLoader.TrainTypeData(carriageName, length, 0.1, "", description, true, carriage.isReversed(), heightOffset, carriage.modelPath(), doors, bogies, width, height, interiorPart, exteriorPart));
        }

        return trainTypes;
    }

    private static String extractCarriageId(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            return "unknown";
        }
        
        String[] pathParts = modelPath.split("/");
        if (pathParts.length > 0) {
            String fileName = pathParts[pathParts.length - 1];
            return fileName.replace(".obj", "");
        }
        
        return "unknown";
    }

    private static List<CarriageModel> parseMTRModelString(String modelString) {
        List<CarriageModel> carriageModels = new ArrayList<>();
        
        String[] parts = modelString.split("\\|");
        for (int i = 0; i < parts.length; i += 2) {
            if (i + 1 < parts.length) {
                String modelPath = parts[i].trim();
                String carriageInfo = parts[i + 1].trim();
                
                if (!modelPath.isEmpty() && !carriageInfo.isEmpty()) {
                    String carriageId = extractCarriageId(modelPath);
                    boolean isReversed = carriageInfo.contains("reversed");
                    carriageModels.add(new CarriageModel(modelPath, carriageId, isReversed));
                }
            }
        }
        
        return carriageModels;
    }

    private static JsonObject loadModelProperties(ResourceManager manager, String modelPropertiesPath) {
        if (modelPropertiesPath.isEmpty()) {
            return null;
        }

        try {
            String[] pathParts = modelPropertiesPath.split(":", 2);
            String namespace = pathParts[0];
            String path = pathParts[1];
            
            List<Resource> resources = manager.getAllResources(Identifier.of(namespace, path));
            if (!resources.isEmpty()) {
                try (InputStreamReader reader = new InputStreamReader(resources.get(0).getInputStream())) {
                    return GSON.fromJson(reader, JsonObject.class);
                }
            }
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to load model properties: {}", modelPropertiesPath, e);
        }
        
        return null;
    }

    private static TrainConfigLoader.DoorConfig createDoorConfigFromParts(List<String> doorPartNames) {
        if (doorPartNames.isEmpty()) {
            return new TrainConfigLoader.DoorConfig(false, "X", new ArrayList<>());
        }

        List<TrainConfigLoader.DoorPart> doorParts = new ArrayList<>();
        
        for (String partInfo : doorPartNames) {
            String[] parts = partInfo.split(":", 2);
            if (parts.length == 2) {
                String partName = parts[0];
                String doorOffset = parts[1];
                
                String side = "LEFT";
                String direction = "NEGATIVE";
                
                if (doorOffset.contains("RIGHT")) {
                    side = "RIGHT";
                } else if (doorOffset.contains("LEFT")) {
                    side = "LEFT";
                }
                
                if (doorOffset.contains("POSITIVE")) {
                    direction = "POSITIVE";
                } else if (doorOffset.contains("NEGATIVE")) {
                    direction = "NEGATIVE";
                }
                
                doorParts.add(new TrainConfigLoader.DoorPart(partName, side, direction, "POP_SLIDE"));
            }
        }

        return new TrainConfigLoader.DoorConfig(true, "Z", doorParts);
    }

    public static Map<String, TrainConfigLoader.TrainTypeData> getMTRTrains() {
        return MTR_TRAINS;
    }

    public static void clearCaches() {
        MTR_TRAINS.clear();
    }
}
