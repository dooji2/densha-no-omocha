package com.dooji.dno.track;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class TrackNodeInfo {
    private final BlockPos position;
    private Direction facing;

    public TrackNodeInfo(BlockPos position, Direction facing) {
        this.position = position;
        this.facing = facing;
    }

    public BlockPos getPosition() {
        return position;
    }

    public Direction getFacing() {
        return facing;
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("x", position.getX());
        nbt.putInt("y", position.getY());
        nbt.putInt("z", position.getZ());
        nbt.putString("facing", facing.name());
        return nbt;
    }

    public static TrackNodeInfo fromNbt(NbtCompound nbt) {
        BlockPos pos = new BlockPos(
            nbt.getInt("x").orElse(0),
            nbt.getInt("y").orElse(0),
            nbt.getInt("z").orElse(0)
        );
        
        Direction dir = Direction.valueOf(nbt.getString("facing").orElse("NORTH"));
        return new TrackNodeInfo(pos, dir);
    }
}
