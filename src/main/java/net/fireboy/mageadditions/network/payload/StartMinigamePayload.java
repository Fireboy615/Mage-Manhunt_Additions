package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Host request to create a pre-game lobby for a chosen ruleset and per-match settings. */
public record StartMinigamePayload(
    ResourceLocation gameId,
    boolean teamsEnabled,
    int teamCount,
    MinigameSettings settings
) implements CustomPacketPayload {
    public static final Type<StartMinigamePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "start_minigame")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StartMinigamePayload> STREAM_CODEC = StreamCodec.of(
        StartMinigamePayload::encode,
        StartMinigamePayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, StartMinigamePayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buf, payload.gameId());
        buf.writeBoolean(payload.teamsEnabled());
        buf.writeVarInt(payload.teamCount());
        writeSettings(buf, payload.settings());
    }

    private static StartMinigamePayload decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation gameId = ResourceLocation.STREAM_CODEC.decode(buf);
        boolean teamsEnabled = buf.readBoolean();
        int teamCount = buf.readVarInt();
        return new StartMinigamePayload(gameId, teamsEnabled, teamCount, readSettings(buf));
    }

    public static void writeSettings(RegistryFriendlyByteBuf buf, MinigameSettings settings) {
        buf.writeVarInt(settings.durationSeconds());
        buf.writeDouble(settings.initialBorderSize());
        buf.writeDouble(settings.finalBorderSize());
        buf.writeBoolean(settings.randomTeleport());
        buf.writeVarInt(settings.kitPreset().ordinal());
    }

    public static MinigameSettings readSettings(RegistryFriendlyByteBuf buf) {
        return new MinigameSettings(
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readBoolean(),
            MinigameSettings.KitPreset.fromOrdinal(buf.readVarInt())
        );
    }

    @Override
    public Type<StartMinigamePayload> type() {
        return TYPE;
    }
}
