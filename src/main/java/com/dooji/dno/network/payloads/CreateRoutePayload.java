package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record CreateRoutePayload(String displayName, List<String> stationIds) implements CustomPayload {
    public static final CustomPayload.Id<CreateRoutePayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "create_route"));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static final PacketCodec<PacketByteBuf, CreateRoutePayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.displayName());
            buf.writeInt(payload.stationIds().size());
            for (String stationId : payload.stationIds()) {
                buf.writeString(stationId);
            }
        },
        buf -> {
            String displayName = buf.readString();
            int stationCount = buf.readInt();
            List<String> stationIds = new ArrayList<>();
            for (int i = 0; i < stationCount; i++) {
                stationIds.add(buf.readString());
            }
            return new CreateRoutePayload(displayName, stationIds);
        }
    );
}
