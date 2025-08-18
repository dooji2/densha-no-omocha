package com.dooji.dno.track;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.World;

public class RouteManager {
    private static final Map<String, Route> routes = new ConcurrentHashMap<>();
    
    public static void addRoute(Route route) {
        if (route != null && route.getRouteId() != null && !route.getRouteId().trim().isEmpty()) {
            routes.put(route.getRouteId(), route);
        }
    }
    
    public static Route getRoute(String routeId) {
        return routes.get(routeId);
    }
    
    public static List<Route> getAllRoutes() {
        return new ArrayList<>(routes.values());
    }
    
    public static void updateRoute(Route route) {
        if (route != null && route.getRouteId() != null && !route.getRouteId().trim().isEmpty()) {
            routes.put(route.getRouteId(), route);
        }
    }
    
    public static List<String> getAllStationIds() {
        List<String> stationIds = new ArrayList<>();
        for (Route route : routes.values()) {
            stationIds.addAll(route.getStationIds());
        }
        
        return stationIds;
    }
    
    public static boolean routeExists(String routeId) {
        return routes.containsKey(routeId);
    }
    
    public static void saveRoutes(World world) {
        RoutePersistenceHandler.saveRoutes(world);
    }
    
    public static void loadRoutes(World world) {
        RoutePersistenceHandler.loadRoutes(world);
    }
}
