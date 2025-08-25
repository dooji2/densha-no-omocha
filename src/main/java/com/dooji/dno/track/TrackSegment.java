package com.dooji.dno.track;

import com.dooji.dno.TrainMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class TrackSegment {
    private final BlockPos start;
    private final BlockPos end;
    private final Direction startDirection;
    private final Direction endDirection;
    private final String modelId;
    private final String type;
    private String trainId;
    private String routeId;
    private int dwellTimeSeconds;
    private double slopeCurvature;
    private int maxSpeedKmh;
    private String stationName;
    private String stationId;
    private boolean openDoorsLeft;
    private boolean openDoorsRight;
    private double scaling;

    public TrackSegment(BlockPos start, BlockPos end, Direction startDirection, Direction endDirection) {
        this(start, end, startDirection, endDirection, TrainMod.MOD_ID + ":default", "normal");
    }

    public TrackSegment(BlockPos start, BlockPos end, Direction startDirection, Direction endDirection, String modelId, String type) {
        this.start = start;
        this.end = end;
        this.startDirection = startDirection;
        this.endDirection = endDirection;
        this.modelId = modelId;
        this.type = type;
        this.trainId = null;
        this.routeId = null;
        this.dwellTimeSeconds = 15;
        this.slopeCurvature = 0.0;
        this.maxSpeedKmh = 80;
        this.stationName = "";
        this.stationId = "";
        this.openDoorsLeft = false;
        this.openDoorsRight = false;
        this.scaling = 1.0;
    }

    public BlockPos start() {
        return start;
    }

    public BlockPos end() {
        return end;
    }

    public Direction startDirection() {
        return startDirection;
    }

    public Direction endDirection() {
        return endDirection;
    }

    public String getModelId() {
        return modelId;
    }

    public String getType() {
        return type;
    }

    public boolean isSiding() {
        return "siding".equals(type);
    }

    public boolean isPlatform() {
        return "platform".equals(type);
    }

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public int getDwellTimeSeconds() {
        return dwellTimeSeconds;
    }

    public void setDwellTimeSeconds(int dwellTimeSeconds) {
        this.dwellTimeSeconds = dwellTimeSeconds;
    }

    public double getSlopeCurvature() {
        return slopeCurvature;
    }

    public void setSlopeCurvature(double slopeCurvature) {
        this.slopeCurvature = slopeCurvature;
    }

    public int getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public void setMaxSpeedKmh(int maxSpeedKmh) {
        this.maxSpeedKmh = maxSpeedKmh;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public boolean getOpenDoorsLeft() {
        return openDoorsLeft;
    }

    public void setOpenDoorsLeft(boolean openDoorsLeft) {
        this.openDoorsLeft = openDoorsLeft;
    }

    public boolean getOpenDoorsRight() {
        return openDoorsRight;
    }

    public void setOpenDoorsRight(boolean openDoorsRight) {
        this.openDoorsRight = openDoorsRight;
    }

    public double getScaling() {
        return scaling;
    }

    public void setScaling(double scaling) {
        this.scaling = scaling;
    }

    public boolean shouldOpenDoors() {
        return openDoorsLeft || openDoorsRight;
    }

    public boolean contains(BlockPos pos) {
        return pos.equals(start()) || pos.equals(end());
    }

    public BlockPos getOtherEnd(BlockPos pos) {
        if (pos.equals(start())) return end();
        if (pos.equals(end())) return start();
        return null;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("startX", start.getX());
        nbt.putInt("startY", start.getY());
        nbt.putInt("startZ", start.getZ());
        nbt.putInt("endX", end.getX());
        nbt.putInt("endY", end.getY());
        nbt.putInt("endZ", end.getZ());
        nbt.putString("startDirection", startDirection.name());
        nbt.putString("endDirection", endDirection.name());
        nbt.putString("modelId", modelId);
        nbt.putString("type", type);

        if (trainId != null) {
            nbt.putString("trainId", trainId);
        }

        if (routeId != null) {
            nbt.putString("routeId", routeId);
        }

        nbt.putInt("dwellTimeSeconds", dwellTimeSeconds);
        nbt.putDouble("slopeCurvature", slopeCurvature);
        nbt.putInt("maxSpeedKmh", maxSpeedKmh);
        
        if (stationName != null) {
            nbt.putString("stationName", stationName);
        }
        
        if (stationId != null) {
            nbt.putString("stationId", stationId);
        }
        
        nbt.putBoolean("openDoorsLeft", openDoorsLeft);
        nbt.putBoolean("openDoorsRight", openDoorsRight);
        nbt.putDouble("scaling", scaling);
        
        return nbt;
    }

    public static TrackSegment fromNbt(NbtCompound nbt) {
        if (!nbt.contains("startX") || !nbt.contains("startY") || !nbt.contains("startZ") ||
            !nbt.contains("endX") || !nbt.contains("endY") || !nbt.contains("endZ") ||
            !nbt.contains("startDirection") || !nbt.contains("endDirection") ||
            !nbt.contains("modelId") || !nbt.contains("type")) {
            return null;
        }

        BlockPos start = new BlockPos(
            nbt.getInt("startX").orElse(0),
            nbt.getInt("startY").orElse(0),
            nbt.getInt("startZ").orElse(0)
        );

        BlockPos end = new BlockPos(
            nbt.getInt("endX").orElse(0),
            nbt.getInt("endY").orElse(0),
            nbt.getInt("endZ").orElse(0)
        );

        Direction startDirection = Direction.valueOf(nbt.getString("startDirection").orElse("NORTH"));
        Direction endDirection = Direction.valueOf(nbt.getString("endDirection").orElse("NORTH"));
        String modelId = nbt.getString("modelId").orElse("densha-no-omocha:default");
        String type = nbt.getString("type").orElse("normal");

        TrackSegment segment = new TrackSegment(start, end, startDirection, endDirection, modelId, type);
        if (nbt.contains("trainId")) {
            segment.setTrainId(nbt.getString("trainId").orElse(null));
        }

        if (nbt.contains("routeId")) {
            segment.setRouteId(nbt.getString("routeId").orElse(null));
        }

        if (nbt.contains("dwellTimeSeconds")) {
            segment.setDwellTimeSeconds(nbt.getInt("dwellTimeSeconds").orElse(15));
        }

        if (nbt.contains("slopeCurvature")) {
            segment.setSlopeCurvature(nbt.getDouble("slopeCurvature").orElse(0.0));
        }

        if (nbt.contains("maxSpeedKmh")) {
            segment.setMaxSpeedKmh(nbt.getInt("maxSpeedKmh").orElse(80));
        }

        if (nbt.contains("stationName")) {
            segment.setStationName(nbt.getString("stationName").orElse(""));
        }

        if (nbt.contains("stationId")) {
            segment.setStationId(nbt.getString("stationId").orElse(""));
        }

        if (nbt.contains("openDoorsLeft")) {
            segment.setOpenDoorsLeft(nbt.getBoolean("openDoorsLeft").orElse(false));
        }

        if (nbt.contains("openDoorsRight")) {
            segment.setOpenDoorsRight(nbt.getBoolean("openDoorsRight").orElse(false));
        }

        if (nbt.contains("scaling")) {
            segment.setScaling(nbt.getDouble("scaling").orElse(1.0));
        }

        return segment;
    }
}
