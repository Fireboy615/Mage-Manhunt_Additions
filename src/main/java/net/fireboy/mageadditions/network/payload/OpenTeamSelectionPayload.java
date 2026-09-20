package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenTeamSelectionPayload(
    ResourceLocation gameId,
    boolean teamsEnabled,
    int teamCount,
    boolean canManage,
    MinigameSettings settings
) implements CustomPacketPayload {
    public static final Type<OpenTeamSelectionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "open_team_selection")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTeamSelectionPayload> STREAM_CODEC = StreamCodec.of(
        OpenTeamSelectionPayload::encode,
        OpenTeamSelectionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, OpenTeamSelectionPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buf, payload.gameId());
        buf.writeBoolean(payload.teamsEnabled());
        buf.writeVarInt(payload.teamCount());
        buf.writeBoolean(payload.canManage());
        StartMinigamePayload.writeSettings(buf, payload.settings());
    }

    private static OpenTeamSelectionPayload decode(RegistryFriendlyByteBuf buf) {
        return new OpenTeamSelectionPayload(
            ResourceLocation.STREAM_CODEC.decode(buf),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readBoolean(),
            StartMinigamePayload.readSettings(buf)
        );
    }

    @Override
    public Type<OpenTeamSelectionPayload> type() {
        return TYPE;
    }
}
