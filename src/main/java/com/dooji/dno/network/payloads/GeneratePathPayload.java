package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record GeneratePathPayload(String trainId, String routeId, BlockPos sidingStart, BlockPos sidingEnd) implements CustomPayload {
    public static final CustomPayload.Id<GeneratePathPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "generate_path"));

    public static final PacketCodec<PacketByteBuf, GeneratePathPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.trainId() != null ? payload.trainId() : "");
            buf.writeString(payload.routeId() != null ? payload.routeId() : "");
            buf.writeBlockPos(payload.sidingStart());
            buf.writeBlockPos(payload.sidingEnd());
        },
        buf -> {
            String trainId = buf.readString();
            String routeId = buf.readString();
            BlockPos sidingStart = buf.readBlockPos();
            BlockPos sidingEnd = buf.readBlockPos();
            return new GeneratePathPayload(trainId, routeId, sidingStart, sidingEnd);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
