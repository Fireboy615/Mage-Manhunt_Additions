package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sets the local client's temporary Capture target outline.
 * entityId < 0 clears the current selection.
 */
public record CaptureTargetPayload(int entityId) implements CustomPacketPayload {
    public static final Type<CaptureTargetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "capture_target")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureTargetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.entityId()),
            buf -> new CaptureTargetPayload(buf.readVarInt())
    );

    @Override
    public Type<CaptureTargetPayload> type() {
        return TYPE;
    }
}
