package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestBoardingPayload(String trainId, int carriageIndex, double relativeX, double relativeY, double relativeZ) implements CustomPayload {
    public static final CustomPayload.Id<RequestBoardingPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "request_boarding"));

    public static final PacketCodec<PacketByteBuf, RequestBoardingPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.trainId());
            buf.writeInt(payload.carriageIndex());
            buf.writeDouble(payload.relativeX());
            buf.writeDouble(payload.relativeY());
            buf.writeDouble(payload.relativeZ());
        },
        buf -> {
            String trainId = buf.readString();
            int carriageIndex = buf.readInt();
            double relativeX = buf.readDouble();
            double relativeY = buf.readDouble();
            double relativeZ = buf.readDouble();
            return new RequestBoardingPayload(trainId, carriageIndex, relativeX, relativeY, relativeZ);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
