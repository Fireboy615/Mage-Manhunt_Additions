package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMinigameMenuRequestPayload() implements CustomPacketPayload {
    public static final OpenMinigameMenuRequestPayload INSTANCE = new OpenMinigameMenuRequestPayload();
    public static final Type<OpenMinigameMenuRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "open_minigame_menu_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMinigameMenuRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenMinigameMenuRequestPayload> type() {
        return TYPE;
    }
}
