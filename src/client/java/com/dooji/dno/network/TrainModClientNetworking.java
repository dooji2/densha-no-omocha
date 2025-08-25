package com.dooji.dno.network;

import com.dooji.dno.network.payloads.*;
import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.track.TrackSegment;
import com.dooji.dno.track.Route;
import com.dooji.dno.track.RouteManagerClient;
import com.dooji.dno.train.TrainManagerClient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

public class TrainModClientNetworking {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SyncTracksPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                Map<String, TrackSegment> tracks = new HashMap<>();
                payload.tracks().forEach((pos, nbt) -> {
                    TrackSegment segment = TrackSegment.fromNbt(nbt);
                    String trackKey = segment.start().getX() + "," + segment.start().getY() + "," + segment.start().getZ() + "->" + segment.end().getX() + "," + segment.end().getY() + "," + segment.end().getZ();
                    tracks.put(trackKey, segment);
                });

                TrackManagerClient.sync(tracks);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PlaceTrackSegmentPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                TrackSegment segment = new TrackSegment(payload.start(), payload.end(), payload.startDirection(), payload.endDirection());
                TrackManagerClient.syncAdd(payload.start(), segment);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BreakTrackSegmentPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                TrackManagerClient.syncRemove(payload.position());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UpdateTrackSegmentPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                TrackManagerClient.syncUpdate(payload.start(), payload.end(), payload.modelId(), payload.type(), payload.dwellTimeSeconds(), payload.slopeCurvature(), payload.trainId(), payload.routeId(), payload.maxSpeedKmh(), payload.stationName(), payload.stationId(), payload.openDoorsLeft(), payload.openDoorsRight(), payload.scaling());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TrainSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                TrainManagerClient.handleTrainSync(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncRoutesPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                Map<String, Route> routes = new HashMap<>();
                payload.routes().forEach((routeId, nbt) -> {
                    Route route = Route.fromNbt(nbt);
                    routes.put(routeId, route);
                });
                
                RouteManagerClient.syncRoutes(routes.values().stream().toList());
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TrackManagerClient.removeAll();
            TrainManagerClient.clearAllTrains();
            RouteManagerClient.clearAllRoutes();
        });
    }

    public static void sendToServer(UpdateTrackSegmentPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(CreateRoutePayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(AssignRoutePayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(UpdateTrainConfigPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(RefreshTrainPathPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(GeneratePathPayload payload) {
        ClientPlayNetworking.send(payload);
    }
    
    public static void sendToServer(SyncRoutesPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(RequestBoardingPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(RequestDisembarkPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(PlayerPositionUpdatePayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
