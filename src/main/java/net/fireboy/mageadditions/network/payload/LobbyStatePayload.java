package net.fireboy.mageadditions.network.payload;

import java.util.ArrayList;
import java.util.List;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Live roster snapshot broadcast whenever a player changes team or joins/leaves. */
public record LobbyStatePayload(
    ResourceLocation gameId,
    int onlinePlayers,
    boolean allReady,
    List<RosterEntry> roster
) implements CustomPacketPayload {
    public static final Type<LobbyStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "lobby_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyStatePayload> STREAM_CODEC = StreamCodec.of(
        LobbyStatePayload::encode,
        LobbyStatePayload::decode
    );

    public LobbyStatePayload {
        roster = List.copyOf(roster);
    }

    private static void encode(RegistryFriendlyByteBuf buf, LobbyStatePayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buf, payload.gameId());
        buf.writeVarInt(payload.onlinePlayers());
        buf.writeBoolean(payload.allReady());
        buf.writeVarInt(payload.roster().size());
        for (RosterEntry entry : payload.roster()) {
            buf.writeUtf(entry.playerName(), 64);
            ResourceLocation.STREAM_CODEC.encode(buf, entry.teamId());
        }
    }

    private static LobbyStatePayload decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation gameId = ResourceLocation.STREAM_CODEC.decode(buf);
        int onlinePlayers = buf.readVarInt();
        boolean allReady = buf.readBoolean();
        int size = Math.min(buf.readVarInt(), 256);
        List<RosterEntry> roster = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            roster.add(new RosterEntry(buf.readUtf(64), ResourceLocation.STREAM_CODEC.decode(buf)));
        }
        return new LobbyStatePayload(gameId, onlinePlayers, allReady, roster);
    }

    @Override
    public Type<LobbyStatePayload> type() {
        return TYPE;
    }

    public record RosterEntry(String playerName, ResourceLocation teamId) {}
}
