package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PauseMinigamePayload() implements CustomPacketPayload {
    public static final PauseMinigamePayload INSTANCE = new PauseMinigamePayload();
    public static final Type<PauseMinigamePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "pause_minigame"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PauseMinigamePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<PauseMinigamePayload> type() {
        return TYPE;
    }
}
