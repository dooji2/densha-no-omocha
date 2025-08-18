package com.dooji.dno.network.payloads;

import com.dooji.dno.TrainMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record BoardingSyncPayload(Map<String, List<BoardingData>> trainBoardingData) implements CustomPayload {
    public static final CustomPayload.Id<BoardingSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(TrainMod.MOD_ID, "boarding_sync"));

    public record BoardingData(String playerId, int carriageIndex, double relativeX, double relativeY, double relativeZ) {}

    public static final PacketCodec<PacketByteBuf, BoardingSyncPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeInt(payload.trainBoardingData().size());
            for (Map.Entry<String, List<BoardingData>> entry : payload.trainBoardingData().entrySet()) {
                buf.writeString(entry.getKey());
                List<BoardingData> boardingList = entry.getValue();
                buf.writeInt(boardingList.size());
                for (BoardingData data : boardingList) {
                    buf.writeString(data.playerId());
                    buf.writeInt(data.carriageIndex());
                    buf.writeDouble(data.relativeX());
                    buf.writeDouble(data.relativeY());
                    buf.writeDouble(data.relativeZ());
                }
            }
        },
        buf -> {
            Map<String, List<BoardingData>> trainBoardingData = new HashMap<>();
            int trainCount = buf.readInt();
            for (int i = 0; i < trainCount; i++) {
                String trainId = buf.readString();
                int boardingCount = buf.readInt();
                List<BoardingData> boardingList = new ArrayList<>();
                for (int j = 0; j < boardingCount; j++) {
                    String playerId = buf.readString();
                    int carriageIndex = buf.readInt();
                    double relativeX = buf.readDouble();
                    double relativeY = buf.readDouble();
                    double relativeZ = buf.readDouble();
                    boardingList.add(new BoardingData(playerId, carriageIndex, relativeX, relativeY, relativeZ));
                }
                trainBoardingData.put(trainId, boardingList);
            }
            return new BoardingSyncPayload(trainBoardingData);
        }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
