package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record AssignRoutePayload(BlockPos start, BlockPos end, String routeId) implements CustomPayload {
    public static final CustomPayload.Id<AssignRoutePayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "assign_route"));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static final PacketCodec<PacketByteBuf, AssignRoutePayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeBlockPos(payload.start());
            buf.writeBlockPos(payload.end());
            buf.writeString(payload.routeId());
        },
        buf -> {
            BlockPos start = buf.readBlockPos();
            BlockPos end = buf.readBlockPos();
            String routeId = buf.readString();
            return new AssignRoutePayload(start, end, routeId);
        }
    );
}
