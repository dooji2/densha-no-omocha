package com.dooji.dno.network.payloads;

import java.util.Map;

import com.dooji.dno.TrainMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record SyncTracksPayload(Map<BlockPos, NbtCompound> tracks) implements CustomPayload {
    public static final CustomPayload.Id<SyncTracksPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "sync_tracks"));

    public static final PacketCodec<PacketByteBuf, SyncTracksPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeMap(payload.tracks(), (b, pos) -> b.writeBlockPos(pos), (b, nbt) -> b.writeNbt(nbt));
        },
        buf -> {
            Map<BlockPos, NbtCompound> tracks = buf.readMap(b -> b.readBlockPos(), b -> b.readNbt());
            return new SyncTracksPayload(tracks);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
