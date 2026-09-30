package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tells the local client whether its player is currently stored by Capture. */
public record CaptureStatePayload(boolean captured) implements CustomPacketPayload {
    public static final Type<CaptureStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "capture_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureStatePayload> STREAM_CODEC = StreamCodec.of(
            CaptureStatePayload::encode,
            CaptureStatePayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buffer, CaptureStatePayload payload) {
        buffer.writeBoolean(payload.captured());
    }

    private static CaptureStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new CaptureStatePayload(buffer.readBoolean());
    }

    @Override
    public Type<CaptureStatePayload> type() {
        return TYPE;
    }
}
