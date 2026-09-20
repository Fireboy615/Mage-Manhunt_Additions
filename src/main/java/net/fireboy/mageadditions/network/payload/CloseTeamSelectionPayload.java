package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloseTeamSelectionPayload() implements CustomPacketPayload {
    public static final CloseTeamSelectionPayload INSTANCE = new CloseTeamSelectionPayload();
    public static final Type<CloseTeamSelectionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "close_team_selection")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CloseTeamSelectionPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<CloseTeamSelectionPayload> type() {
        return TYPE;
    }
}
