package net.fireboy.mageadditions.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Personalized teammate list. The server only sends a player their own teammates. */
public record TeamOutlinePayload(boolean enabled, List<UUID> teammates) implements CustomPacketPayload {
    public static final Type<TeamOutlinePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "team_outlines")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamOutlinePayload> STREAM_CODEC = StreamCodec.of(
        TeamOutlinePayload::encode,
        TeamOutlinePayload::decode
    );

    public TeamOutlinePayload {
        teammates = List.copyOf(teammates);
    }

    private static void encode(RegistryFriendlyByteBuf buf, TeamOutlinePayload payload) {
        buf.writeBoolean(payload.enabled());
        buf.writeVarInt(payload.teammates().size());
        for (UUID uuid : payload.teammates()) {
            buf.writeUUID(uuid);
        }
    }

    private static TeamOutlinePayload decode(RegistryFriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), 256);
        List<UUID> teammates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            teammates.add(buf.readUUID());
        }
        return new TeamOutlinePayload(enabled, teammates);
    }

    @Override
    public Type<TeamOutlinePayload> type() {
        return TYPE;
    }
}
