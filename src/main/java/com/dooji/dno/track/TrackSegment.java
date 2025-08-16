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
    private int dwellTimeSeconds;
    private double slopeCurvature;

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
        this.dwellTimeSeconds = 15;
        this.slopeCurvature = 0.0;
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

        nbt.putInt("dwellTimeSeconds", dwellTimeSeconds);
        nbt.putDouble("slopeCurvature", slopeCurvature);
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

        if (nbt.contains("dwellTimeSeconds")) {
            segment.setDwellTimeSeconds(nbt.getInt("dwellTimeSeconds").orElse(15));
        }

        if (nbt.contains("slopeCurvature")) {
            segment.setSlopeCurvature(nbt.getDouble("slopeCurvature").orElse(0.0));
        }

        return segment;
    }
}
