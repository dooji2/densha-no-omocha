package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.HashMap;

public record SyncRoutesPayload(Map<String, NbtCompound> routes) implements CustomPayload {
    public static final CustomPayload.Id<SyncRoutesPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "sync_routes"));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static final PacketCodec<PacketByteBuf, SyncRoutesPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeInt(payload.routes().size());
            for (Map.Entry<String, NbtCompound> entry : payload.routes().entrySet()) {
                buf.writeString(entry.getKey());
                buf.writeNbt(entry.getValue());
            }
        },
        buf -> {
            int routeCount = buf.readInt();
            Map<String, NbtCompound> routes = new HashMap<>();
            for (int i = 0; i < routeCount; i++) {
                String routeId = buf.readString();
                NbtCompound routeNbt = buf.readNbt();
                if (routeNbt != null) {
                    routes.put(routeId, routeNbt);
                }
            }
            return new SyncRoutesPayload(routes);
        }
    );
}
