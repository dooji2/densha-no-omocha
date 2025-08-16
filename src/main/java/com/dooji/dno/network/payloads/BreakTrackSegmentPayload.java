package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record BreakTrackSegmentPayload(BlockPos position) implements CustomPayload {
    public static final CustomPayload.Id<BreakTrackSegmentPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "break_track_segment"));

    public static final PacketCodec<PacketByteBuf, BreakTrackSegmentPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> buf.writeBlockPos(payload.position()),
        buf -> new BreakTrackSegmentPayload(buf.readBlockPos()) 
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
