package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record UpdateTrainConfigPayload(String trainId, List<String> carriageIds, List<Double> carriageLengths, List<Double> bogieInsets, String trackSegmentKey, List<Double> boundingBoxWidths, List<Double> boundingBoxLengths, List<Double> boundingBoxHeights) implements CustomPayload {
    public static final CustomPayload.Id<UpdateTrainConfigPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "update_train_config"));

    public static final PacketCodec<PacketByteBuf, UpdateTrainConfigPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.trainId() != null ? payload.trainId() : "");
            buf.writeCollection(payload.carriageIds(), PacketByteBuf::writeString);
            buf.writeCollection(payload.carriageLengths(), PacketByteBuf::writeDouble);
            buf.writeCollection(payload.bogieInsets(), PacketByteBuf::writeDouble);
            buf.writeString(payload.trackSegmentKey() != null ? payload.trackSegmentKey() : "");
            buf.writeCollection(payload.boundingBoxWidths(), PacketByteBuf::writeDouble);
            buf.writeCollection(payload.boundingBoxLengths(), PacketByteBuf::writeDouble);
            buf.writeCollection(payload.boundingBoxHeights(), PacketByteBuf::writeDouble);
        },
        buf -> {
            String trainId = buf.readString();
            List<String> carriageIds = buf.readList(PacketByteBuf::readString);
            List<Double> carriageLengths = buf.readList(PacketByteBuf::readDouble);
            List<Double> bogieInsets = buf.readList(PacketByteBuf::readDouble);
            String trackSegmentKey = buf.readString();
            List<Double> boundingBoxWidths = buf.readList(PacketByteBuf::readDouble);
            List<Double> boundingBoxLengths = buf.readList(PacketByteBuf::readDouble);
            List<Double> boundingBoxHeights = buf.readList(PacketByteBuf::readDouble);
            return new UpdateTrainConfigPayload(trainId, carriageIds, carriageLengths, bogieInsets, trackSegmentKey, boundingBoxWidths, boundingBoxLengths, boundingBoxHeights);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
