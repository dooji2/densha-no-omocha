package com.dooji.dno.track;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.dooji.dno.TrainMod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TrackPersistenceHandler {
    private static final int MAX_TRACK_SAVE_ATTEMPTS = 3;

    private static String getSaveFileName(World world) {
        String dimensionId = world.getRegistryKey().getValue().toString();
        if ("minecraft:overworld".equals(dimensionId)) {
            return "tracks.dat";
        }

        return "tracks_" + dimensionId.replace(':', '_') + ".dat";
    }

    public static void saveTracks(World world, Map<String, TrackSegment> tracks) {
        if (world == null || tracks == null) {
            return;
        }

        if (world.isClient() || !(world instanceof ServerWorld)) {
            return;
        }

        for (int attempt = 0; attempt < MAX_TRACK_SAVE_ATTEMPTS; attempt++) {
            try {
                NbtCompound rootTag = new NbtCompound();
                NbtList trackList = new NbtList();

                for (Map.Entry<String, TrackSegment> entry : tracks.entrySet()) {
                    TrackSegment segment = entry.getValue();
                    trackList.add(segment.toNbt());
                }

                rootTag.put("tracks", trackList);

                Map<BlockPos, TrackNodeInfo> nodes = TrackManager.getNodesFor(world);
                if (nodes != null && !nodes.isEmpty()) {
                    NbtList nodeList = new NbtList();
                    for (TrackNodeInfo node : nodes.values()) {
                        nodeList.add(node.toNbt());
                    }

                    rootTag.put("nodes", nodeList);
                }

                ServerWorld serverWorld = (ServerWorld) world;
                Path saveDir = serverWorld.getServer().getSavePath(WorldSavePath.ROOT).resolve("data");
                String fileName = getSaveFileName(world);
                File saveFile = saveDir.resolve(fileName).toFile();

                saveDir.toFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    NbtIo.writeCompressed(rootTag, fos);
                    break;
                }
            } catch (IOException e) {
                TrainMod.LOGGER.error("Failed to save tracks (Attempt {})", attempt + 1, e);
                try {
                    Thread.sleep(100 * (attempt + 1));
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    public static Map<String, TrackSegment> loadTracks(World world) {
        Map<String, TrackSegment> tracks = new HashMap<>();

        if (world == null) {
            return tracks;
        }

        if (world.isClient() || !(world instanceof ServerWorld)) {
            return tracks;
        }

        try {
            ServerWorld serverWorld = (ServerWorld) world;

            Path saveDir = serverWorld.getServer().getSavePath(WorldSavePath.ROOT).resolve("data");
            String fileName = getSaveFileName(world);
            File saveFile = saveDir.resolve(fileName).toFile();

            if (!saveFile.exists()) {
                return tracks;
            }

            try (FileInputStream fis = new FileInputStream(saveFile)) {
                NbtCompound rootTag = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());

                if (rootTag.contains("tracks")) {
                    NbtList trackList = rootTag.getListOrEmpty("tracks");

                    for (int i = 0; i < trackList.size(); i++) {
                        try {
                            NbtCompound trackTag = trackList.getCompoundOrEmpty(i);
                            TrackSegment segment = TrackSegment.fromNbt(trackTag);
                            
                            if (segment != null) {
                                String trackKey = segment.start().getX() + "," + segment.start().getY() + "," + segment.start().getZ() + "->" + segment.end().getX() + "," + segment.end().getY() + "," + segment.end().getZ();
                                tracks.put(trackKey, segment);
                            }
                        } catch (Exception e) {
                            TrainMod.LOGGER.error("Failed to load track segment at index {}", i, e);
                        }
                    }
                }

                if (rootTag.contains("nodes")) {
                    NbtList nodeList = rootTag.getListOrEmpty("nodes");
                    Map<BlockPos, TrackNodeInfo> nodes = new HashMap<>();

                    for (int i = 0; i < nodeList.size(); i++) {
                        try {
                            NbtCompound nodeTag = nodeList.getCompoundOrEmpty(i);
                            TrackNodeInfo info = TrackNodeInfo.fromNbt(nodeTag);
                            nodes.put(info.getPosition(), info);
                        } catch (Exception e) {
                            TrainMod.LOGGER.error("Failed to load node at index {}", i, e);
                        }
                    }
                    
                    TrackManager.setNodesForDimension(world, nodes);
                }
            }
        } catch (IOException e) {
            TrainMod.LOGGER.error("Failed to load tracks", e);
        }

        return tracks;
    }
}
