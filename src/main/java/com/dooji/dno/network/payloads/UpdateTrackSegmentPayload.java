package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record UpdateTrackSegmentPayload(BlockPos start, BlockPos end, String modelId, String type, int dwellTimeSeconds, double slopeCurvature, String trainId, int maxSpeedKmh, String stationName, String stationId) implements CustomPayload {
    public static final CustomPayload.Id<UpdateTrackSegmentPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "update_track_segment"));

    public static final PacketCodec<PacketByteBuf, UpdateTrackSegmentPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeBlockPos(payload.start());
            buf.writeBlockPos(payload.end());
            buf.writeString(payload.modelId());
            buf.writeString(payload.type());
            buf.writeInt(payload.dwellTimeSeconds());
            buf.writeDouble(payload.slopeCurvature());
            boolean hasTrainId = payload.trainId() != null;
            buf.writeBoolean(hasTrainId);
            
            if (hasTrainId) {
                buf.writeString(payload.trainId());
            }
            
            buf.writeInt(payload.maxSpeedKmh());
            buf.writeString(payload.stationName());
            buf.writeString(payload.stationId());
        },
        buf -> {
            BlockPos start = buf.readBlockPos();
            BlockPos end = buf.readBlockPos();
            String modelId = buf.readString();
            String type = buf.readString();
            int dwellTimeSeconds = buf.readInt();
            double slopeCurvature = buf.readDouble();
            String trainId = null;
            boolean hasTrainId = buf.readBoolean();

            if (hasTrainId) {
                trainId = buf.readString();
            }

            int maxSpeedKmh = buf.readInt();
            String stationName = buf.readString();
            String stationId = buf.readString();
            return new UpdateTrackSegmentPayload(start, end, modelId, type, dwellTimeSeconds, slopeCurvature, trainId, maxSpeedKmh, stationName, stationId);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
