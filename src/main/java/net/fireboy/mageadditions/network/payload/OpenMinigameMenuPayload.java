package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMinigameMenuPayload() implements CustomPacketPayload {
    public static final OpenMinigameMenuPayload INSTANCE = new OpenMinigameMenuPayload();
    public static final Type<OpenMinigameMenuPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "open_minigame_menu")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMinigameMenuPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenMinigameMenuPayload> type() {
        return TYPE;
    }
}
