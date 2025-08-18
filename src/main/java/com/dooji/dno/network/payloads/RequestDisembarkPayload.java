package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestDisembarkPayload(String trainId) implements CustomPayload {
    public static final CustomPayload.Id<RequestDisembarkPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "request_disembark"));

    public static final PacketCodec<PacketByteBuf, RequestDisembarkPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.trainId());
        },
        buf -> {
            String trainId = buf.readString();
            return new RequestDisembarkPayload(trainId);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
