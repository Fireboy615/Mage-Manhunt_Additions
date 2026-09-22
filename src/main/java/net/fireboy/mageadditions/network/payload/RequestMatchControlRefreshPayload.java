package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class RequestMatchControlRefreshPayload implements CustomPacketPayload {
    public static final RequestMatchControlRefreshPayload INSTANCE = new RequestMatchControlRefreshPayload();
    public static final Type<RequestMatchControlRefreshPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "request_match_control_refresh"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMatchControlRefreshPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    private RequestMatchControlRefreshPayload() {}
    @Override public Type<RequestMatchControlRefreshPayload> type() { return TYPE; }
}
