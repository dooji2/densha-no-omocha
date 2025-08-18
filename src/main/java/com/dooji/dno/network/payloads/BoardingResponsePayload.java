package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BoardingResponsePayload(String trainId, int carriageIndex, boolean approved, double relativeX, double relativeY, double relativeZ) implements CustomPayload {
    public static final CustomPayload.Id<BoardingResponsePayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "boarding_response"));

    public static final PacketCodec<PacketByteBuf, BoardingResponsePayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.trainId());
            buf.writeInt(payload.carriageIndex());
            buf.writeBoolean(payload.approved());
            buf.writeDouble(payload.relativeX());
            buf.writeDouble(payload.relativeY());
            buf.writeDouble(payload.relativeZ());
        },
        buf -> {
            String trainId = buf.readString();
            int carriageIndex = buf.readInt();
            boolean approved = buf.readBoolean();
            double relativeX = buf.readDouble();
            double relativeY = buf.readDouble();
            double relativeZ = buf.readDouble();
            return new BoardingResponsePayload(trainId, carriageIndex, approved, relativeX, relativeY, relativeZ);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
