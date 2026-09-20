package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ContinueMinigamePayload() implements CustomPacketPayload {
    public static final ContinueMinigamePayload INSTANCE = new ContinueMinigamePayload();
    public static final Type<ContinueMinigamePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "continue_minigame"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContinueMinigamePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ContinueMinigamePayload> type() {
        return TYPE;
    }
}
