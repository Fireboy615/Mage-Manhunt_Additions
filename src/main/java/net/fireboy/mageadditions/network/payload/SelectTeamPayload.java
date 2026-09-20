package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectTeamPayload(ResourceLocation gameId, ResourceLocation teamId) implements CustomPacketPayload {
    public static final Type<SelectTeamPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "select_team")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTeamPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC,
        SelectTeamPayload::gameId,
        ResourceLocation.STREAM_CODEC,
        SelectTeamPayload::teamId,
        SelectTeamPayload::new
    );

    @Override
    public Type<SelectTeamPayload> type() {
        return TYPE;
    }
}
