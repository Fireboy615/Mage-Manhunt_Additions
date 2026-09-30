package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server movement intent for Mirror Image.
 * forward: W=+1, S=-1
 * strafe:  D=+1, A=-1
 */
public record MirrorMovementInputPayload(float forward, float strafe) implements CustomPacketPayload {
    public static final Type<MirrorMovementInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "mirror_movement_input")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MirrorMovementInputPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeFloat(payload.forward());
                        buf.writeFloat(payload.strafe());
                    },
                    buf -> new MirrorMovementInputPayload(buf.readFloat(), buf.readFloat())
            );

    @Override
    public Type<MirrorMovementInputPayload> type() {
        return TYPE;
    }
}
