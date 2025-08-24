package com.dooji.dno.track;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackConfigLoader {
    private static final Map<String, TrackTypeData> TRACK_TYPES = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void loadTrackTypes(ResourceManager resourceManager) {
        TRACK_TYPES.clear();

        try {
            Map<String, JsonObject> combined = new HashMap<>();
            
            List<Resource> resources = resourceManager.getAllResources(Identifier.of("densha-no-omocha", "rails.json"));
            for (Resource resource : resources) {
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    for (Map.Entry<String, JsonElement> trackEntry : json.entrySet()) {
                        String trackId = trackEntry.getKey();
                        JsonObject trackData = trackEntry.getValue().getAsJsonObject();
                        combined.put(trackId, trackData);
                    }
                }
            }

            for (Map.Entry<String, JsonObject> entry : combined.entrySet()) {
                String trackId = entry.getKey();
                JsonObject trackData = entry.getValue();

                String namespacedId = "densha-no-omocha:" + trackId;
                String name = trackData.has("name") ? trackData.get("name").getAsString() : trackId;
                String model = trackData.has("model") ? trackData.get("model").getAsString() : null;
                double repeatInterval = trackData.has("repeatInterval") ? trackData.get("repeatInterval").getAsDouble() : 1.0;
                boolean flipV = trackData.has("flipV") && trackData.get("flipV").getAsBoolean();
                String icon = trackData.has("icon") ? trackData.get("icon").getAsString() : null;
                String description = trackData.has("description") ? trackData.get("description").getAsString() : null;

                TRACK_TYPES.put(namespacedId, new TrackTypeData(name, model, repeatInterval, flipV, icon, description));
            }

            TrackCompatibilityLoader.loadMTRTracks(resourceManager);
            TRACK_TYPES.putAll(TrackCompatibilityLoader.getMTRTracks());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static TrackTypeData getTrackType(String id) {
        return TRACK_TYPES.get(id);
    }

    public static Map<String, TrackTypeData> getAllTrackTypes() {
        return new HashMap<>(TRACK_TYPES);
    }

    public static void clearCaches() {
        TRACK_TYPES.clear();
        TrackCompatibilityLoader.clearCaches();
    }

    public static record TrackTypeData(String name, String model, double repeatInterval, boolean flipV, String icon, String description) {}
}
