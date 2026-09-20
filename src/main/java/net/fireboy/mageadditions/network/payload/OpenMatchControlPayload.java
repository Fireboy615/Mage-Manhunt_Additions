package net.fireboy.mageadditions.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Current match state shown only to an authorized operator. */
public record OpenMatchControlPayload(
        ResourceLocation gameId,
        boolean paused,
        int secondsRemaining,
        double currentBorderSize,
        int onlineParticipants,
        int totalParticipants,
        List<DeadPlayer> deadPlayers
) implements CustomPacketPayload {
    public static final Type<OpenMatchControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "open_match_control")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMatchControlPayload> STREAM_CODEC = StreamCodec.of(
            OpenMatchControlPayload::encode,
            OpenMatchControlPayload::decode
    );

    public OpenMatchControlPayload {
        deadPlayers = List.copyOf(deadPlayers);
    }

    private static void encode(RegistryFriendlyByteBuf buf, OpenMatchControlPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buf, payload.gameId());
        buf.writeBoolean(payload.paused());
        buf.writeVarInt(payload.secondsRemaining());
        buf.writeDouble(payload.currentBorderSize());
        buf.writeVarInt(payload.onlineParticipants());
        buf.writeVarInt(payload.totalParticipants());
        buf.writeVarInt(payload.deadPlayers().size());
        for (DeadPlayer deadPlayer : payload.deadPlayers()) {
            buf.writeUUID(deadPlayer.uuid());
            buf.writeUtf(deadPlayer.name(), 64);
        }
    }

    private static OpenMatchControlPayload decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation gameId = ResourceLocation.STREAM_CODEC.decode(buf);
        boolean paused = buf.readBoolean();
        int secondsRemaining = buf.readVarInt();
        double currentBorderSize = buf.readDouble();
        int onlineParticipants = buf.readVarInt();
        int totalParticipants = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), 256);
        List<DeadPlayer> deadPlayers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            deadPlayers.add(new DeadPlayer(buf.readUUID(), buf.readUtf(64)));
        }
        return new OpenMatchControlPayload(gameId, paused, secondsRemaining, currentBorderSize, onlineParticipants, totalParticipants, deadPlayers);
    }

    @Override
    public Type<OpenMatchControlPayload> type() {
        return TYPE;
    }

    public record DeadPlayer(UUID uuid, String name) {}
}
