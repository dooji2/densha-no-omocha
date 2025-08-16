package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record RefreshTrainPathPayload(String trainId, BlockPos sidingStart, BlockPos sidingEnd) implements CustomPayload {
    public static final CustomPayload.Id<RefreshTrainPathPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "refresh_train_path"));

    public static final PacketCodec<PacketByteBuf, RefreshTrainPathPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.trainId());
            buf.writeBlockPos(payload.sidingStart());
            buf.writeBlockPos(payload.sidingEnd());
        },
        buf -> new RefreshTrainPathPayload(buf.readString(), buf.readBlockPos(), buf.readBlockPos())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
