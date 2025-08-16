package com.dooji.dno.track;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class TrackTypeRegistry {
    private static final Map<String, TrackTypeData> TRACK_TYPES = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void loadTrackTypes(ResourceManager resourceManager) {
        TRACK_TYPES.clear();

        try {
            for (Map.Entry<Identifier, Resource> entry : resourceManager.findResources("rails", path -> path.getPath().endsWith(".json")).entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    for (Map.Entry<String, com.google.gson.JsonElement> trackEntry : json.entrySet()) {
                        String trackId = trackEntry.getKey();
                        JsonObject trackData = trackEntry.getValue().getAsJsonObject();

                        String name = trackData.has("name") ? trackData.get("name").getAsString() : trackId;
                        String model = trackData.get("model").getAsString();
                        double repeatInterval = trackData.has("repeatInterval") ? trackData.get("repeatInterval").getAsDouble() : 1.0;
                        boolean flipV = trackData.has("flipV") && trackData.get("flipV").getAsBoolean();

                        TRACK_TYPES.put(trackId, new TrackTypeData(name, model, repeatInterval, flipV));
                    }
                }
            }
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

    public static record TrackTypeData(String name, String model, double repeatInterval, boolean flipV) {}
}
