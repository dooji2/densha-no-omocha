package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record PlaceTrackSegmentPayload(BlockPos start, BlockPos end, Direction startDirection, Direction endDirection) implements CustomPayload {
    public static final CustomPayload.Id<PlaceTrackSegmentPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "place_track_segment"));

    public static final PacketCodec<PacketByteBuf, PlaceTrackSegmentPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeBlockPos(payload.start());
            buf.writeBlockPos(payload.end());
            buf.writeEnumConstant(payload.startDirection());
            buf.writeEnumConstant(payload.endDirection());
        },
        buf -> {
            BlockPos start = buf.readBlockPos();
            BlockPos end = buf.readBlockPos();
            Direction startDir = buf.readEnumConstant(Direction.class);
            Direction endDir = buf.readEnumConstant(Direction.class);
            return new PlaceTrackSegmentPayload(start, end, startDir, endDir);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
