package com.dooji.dno.track;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Route {
    private String routeId;
    private String displayName;
    private List<String> stationIds;
    private long createdAt;
    
    public Route(String displayName, List<String> stationIds) {
        this.routeId = "route_" + UUID.randomUUID().toString().substring(0, 8);
        this.displayName = displayName != null && !displayName.trim().isEmpty() ? displayName : "Route";
        this.stationIds = new ArrayList<>(stationIds);
        this.createdAt = System.currentTimeMillis();
    }
    
    public Route() {
        this.routeId = "";
        this.displayName = "Route";
        this.stationIds = new ArrayList<>();
        this.createdAt = 0;
    }
    
    public String getRouteId() {
        return routeId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName != null && !displayName.trim().isEmpty() ? displayName : "Route";
    }
    
    public List<String> getStationIds() {
        return new ArrayList<>(stationIds);
    }
    
    public void setStationIds(List<String> stationIds) {
        this.stationIds = new ArrayList<>(stationIds);
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void addStation(String stationId) {
        if (stationId != null && !stationId.trim().isEmpty() && !stationIds.contains(stationId)) {
            stationIds.add(stationId);
        }
    }
    
    public void removeStation(String stationId) {
        stationIds.remove(stationId);
    }
    
    public void moveStation(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < stationIds.size() && toIndex >= 0 && toIndex < stationIds.size()) {
            String station = stationIds.remove(fromIndex);
            stationIds.add(toIndex, station);
        }
    }
    
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("routeId", routeId);
        nbt.putString("displayName", displayName);
        nbt.putLong("createdAt", createdAt);
        
        NbtList stationList = new NbtList();
        for (String stationId : stationIds) {
            stationList.add(NbtString.of(stationId));
        }
        nbt.put("stationIds", stationList);
        
        return nbt;
    }
    
    public static Route fromNbt(NbtCompound nbt) {
        Route route = new Route();
        route.routeId = nbt.getString("routeId").orElse("");
        route.displayName = nbt.getString("displayName").orElse("Route");
        route.createdAt = nbt.getLong("createdAt").orElse(0L);
        
        NbtList stationList = nbt.getListOrEmpty("stationIds");
        for (int i = 0; i < stationList.size(); i++) {
            route.stationIds.add(stationList.getString(i).orElse(""));
        }
        
        return route;
    }
}
