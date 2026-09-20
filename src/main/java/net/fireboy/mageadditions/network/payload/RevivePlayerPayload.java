package net.fireboy.mageadditions.network.payload;

import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RevivePlayerPayload(UUID playerId) implements CustomPacketPayload {
    public static final Type<RevivePlayerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "revive_player"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevivePlayerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUUID(payload.playerId()),
            buf -> new RevivePlayerPayload(buf.readUUID())
    );

    @Override
    public Type<RevivePlayerPayload> type() {
        return TYPE;
    }
}
