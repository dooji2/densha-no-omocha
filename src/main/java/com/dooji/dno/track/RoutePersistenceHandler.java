package com.dooji.dno.track;

import com.dooji.dno.TrainMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class RoutePersistenceHandler {
    
    public static void saveRoutes(World world) {
        if (world == null || world.isClient() || !(world instanceof ServerWorld)) {
            return;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        Path saveDir = serverWorld.getServer().getSavePath(WorldSavePath.ROOT).resolve("data");
        String fileName = getSaveFileName(world);
        File saveFile = saveDir.resolve(fileName).toFile();

        saveDir.toFile().mkdirs();

        NbtCompound rootTag = new NbtCompound();
        List<Route> routes = RouteManager.getAllRoutes();
        
        if (routes != null && !routes.isEmpty()) {
            NbtList routeList = new NbtList();
            for (Route route : routes) {
                routeList.add(route.toNbt());
            }

            rootTag.put("routes", routeList);
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                NbtIo.writeCompressed(rootTag, fos);
                break;
            } catch (IOException e) {
                TrainMod.LOGGER.error("Failed to save routes (Attempt {})", attempt + 1, e);
                try {
                    Thread.sleep(100 * (attempt + 1));
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    public static void loadRoutes(World world) {
        if (world == null || world.isClient() || !(world instanceof ServerWorld)) {
            return;
        }

        try {
            ServerWorld serverWorld = (ServerWorld) world;
            Path saveDir = serverWorld.getServer().getSavePath(WorldSavePath.ROOT).resolve("data");
            String fileName = getSaveFileName(world);
            File saveFile = saveDir.resolve(fileName).toFile();

            if (!saveFile.exists()) {
                return;
            }

            try (FileInputStream fis = new FileInputStream(saveFile)) {
                NbtCompound rootTag = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());

                if (rootTag.contains("routes")) {
                    NbtList routeList = rootTag.getListOrEmpty("routes");
                    for (int i = 0; i < routeList.size(); i++) {
                        try {
                            NbtCompound routeTag = routeList.getCompoundOrEmpty(i);
                            Route route = Route.fromNbt(routeTag);
                            if (route != null) {
                                RouteManager.addRoute(route);
                            }
                        } catch (Exception e) {
                            TrainMod.LOGGER.error("Failed to load route at index {}", i, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            TrainMod.LOGGER.error("Failed to load routes", e);
        }
    }

    private static String getSaveFileName(World world) {
        return "routes.dat";
    }
}
