package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LaunchMinigamePayload() implements CustomPacketPayload {
    public static final LaunchMinigamePayload INSTANCE = new LaunchMinigamePayload();
    public static final Type<LaunchMinigamePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "launch_minigame")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchMinigamePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<LaunchMinigamePayload> type() {
        return TYPE;
    }
}
