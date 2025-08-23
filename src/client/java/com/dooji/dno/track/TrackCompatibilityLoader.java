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
import java.util.Map;

import com.dooji.dno.TrainModClient;

public class TrackCompatibilityLoader {
    private static final Map<String, TrackConfigLoader.TrackTypeData> MTR_TRACKS = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void loadMTRTracks(ResourceManager resourceManager) {
        MTR_TRACKS.clear();

        try {
            for (Map.Entry<Identifier, Resource> entry : resourceManager.findResources("rails", path -> path.getPath().endsWith(".json")).entrySet()) {
                Identifier identifier = entry.getKey();
                
                if ("mtrsteamloco".equals(identifier.getNamespace())) {
                    try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream())) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);

                        for (Map.Entry<String, JsonElement> trackEntry : json.entrySet()) {
                            String trackId = trackEntry.getKey();
                            JsonObject trackData = trackEntry.getValue().getAsJsonObject();

                            try {
                                String name = trackData.has("name") ? trackData.get("name").getAsString() : trackId;
                                String model = trackData.has("model") ? trackData.get("model").getAsString() : "";
                                double repeatInterval = trackData.has("repeatInterval") ? trackData.get("repeatInterval").getAsDouble() : 1.0;
                                boolean flipV = trackData.has("flipV") && trackData.get("flipV").getAsBoolean();

                                if (!model.isEmpty()) {
                                    TrackConfigLoader.TrackTypeData trackTypeData = new TrackConfigLoader.TrackTypeData(name, model, repeatInterval, flipV, null, null);
                                    MTR_TRACKS.put(identifier.getNamespace() + ":" + trackId, trackTypeData);
                                }
                            } catch (Exception e) {
                                TrainModClient.LOGGER.error("Failed to parse track data for: {}", trackId, e);
                            }
                        }
                    } catch (IOException e) {
                        TrainModClient.LOGGER.error("Failed to read track resource: {}", identifier, e);
                    }
                }
            }
        } catch (Exception e) {
            TrainModClient.LOGGER.error("Failed to load MTR tracks", e);
        }
    }

    public static Map<String, TrackConfigLoader.TrackTypeData> getMTRTracks() {
        return new HashMap<>(MTR_TRACKS);
    }

    public static void clearCaches() {
        MTR_TRACKS.clear();
    }
}
