package net.fireboy.mageadditions.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Current match state shown only to an authorized operator. Border values are radii. */
public record OpenMatchControlPayload(
        ResourceLocation gameId,
        boolean paused,
        boolean teamsEnabled,
        int teamCount,
        int secondsRemaining,
        double currentBorderRadius,
        double startBorderRadius,
        double endBorderRadius,
        int onlineParticipants,
        int totalParticipants,
        List<DeadPlayer> deadPlayers,
        List<PlayerTeamEntry> players
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
        players = List.copyOf(players);
    }

    private static void encode(RegistryFriendlyByteBuf buf, OpenMatchControlPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buf, payload.gameId());
        buf.writeBoolean(payload.paused());
        buf.writeBoolean(payload.teamsEnabled());
        buf.writeVarInt(payload.teamCount());
        buf.writeVarInt(payload.secondsRemaining());
        buf.writeDouble(payload.currentBorderRadius());
        buf.writeDouble(payload.startBorderRadius());
        buf.writeDouble(payload.endBorderRadius());
        buf.writeVarInt(payload.onlineParticipants());
        buf.writeVarInt(payload.totalParticipants());
        buf.writeVarInt(payload.deadPlayers().size());
        for (DeadPlayer deadPlayer : payload.deadPlayers()) {
            buf.writeUUID(deadPlayer.uuid());
            buf.writeUtf(deadPlayer.name(), 64);
        }
        buf.writeVarInt(payload.players().size());
        for (PlayerTeamEntry entry : payload.players()) {
            buf.writeUUID(entry.uuid());
            buf.writeUtf(entry.name(), 64);
            ResourceLocation.STREAM_CODEC.encode(buf, entry.teamId());
            buf.writeBoolean(entry.dead());
        }
    }

    private static OpenMatchControlPayload decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation gameId = ResourceLocation.STREAM_CODEC.decode(buf);
        boolean paused = buf.readBoolean();
        boolean teamsEnabled = buf.readBoolean();
        int teamCount = buf.readVarInt();
        int secondsRemaining = buf.readVarInt();
        double currentBorderRadius = buf.readDouble();
        double startBorderRadius = buf.readDouble();
        double endBorderRadius = buf.readDouble();
        int onlineParticipants = buf.readVarInt();
        int totalParticipants = buf.readVarInt();
        int deadCount = Math.min(buf.readVarInt(), 256);
        List<DeadPlayer> deadPlayers = new ArrayList<>(deadCount);
        for (int i = 0; i < deadCount; i++) {
            deadPlayers.add(new DeadPlayer(buf.readUUID(), buf.readUtf(64)));
        }
        int playerCount = Math.min(buf.readVarInt(), 256);
        List<PlayerTeamEntry> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerTeamEntry(
                buf.readUUID(),
                buf.readUtf(64),
                ResourceLocation.STREAM_CODEC.decode(buf),
                buf.readBoolean()
            ));
        }
        return new OpenMatchControlPayload(
            gameId, paused, teamsEnabled, teamCount, secondsRemaining, currentBorderRadius,
            startBorderRadius, endBorderRadius, onlineParticipants, totalParticipants, deadPlayers, players
        );
    }

    @Override
    public Type<OpenMatchControlPayload> type() {
        return TYPE;
    }

    public record DeadPlayer(UUID uuid, String name) {}
    public record PlayerTeamEntry(UUID uuid, String name, ResourceLocation teamId, boolean dead) {}
}
