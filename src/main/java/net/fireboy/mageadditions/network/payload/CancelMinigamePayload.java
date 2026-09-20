package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CancelMinigamePayload() implements CustomPacketPayload {
    public static final CancelMinigamePayload INSTANCE = new CancelMinigamePayload();
    public static final Type<CancelMinigamePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "cancel_minigame")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelMinigamePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<CancelMinigamePayload> type() {
        return TYPE;
    }
}
