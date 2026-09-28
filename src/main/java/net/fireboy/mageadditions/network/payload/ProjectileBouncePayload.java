package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.spell.GenericSpellOverrideServerEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-authoritative projectile bounce state.
 *
 * <p>The initial packet pre-arms the client before its first block collision;
 * bounce packets then reconcile the exact server position/velocity. This stops
 * Iron projectile subclasses from disappearing locally when the server has
 * consumed the impact as a bounce.</p>
 */
public record ProjectileBouncePayload(
        int entityId,
        int remainingBounces,
        boolean bounced,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz
) implements CustomPacketPayload {
    public static final Type<ProjectileBouncePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "projectile_bounce_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileBouncePayload> STREAM_CODEC = StreamCodec.of(
            ProjectileBouncePayload::encode,
            ProjectileBouncePayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buffer, ProjectileBouncePayload value) {
        buffer.writeVarInt(value.entityId());
        buffer.writeVarInt(value.remainingBounces());
        buffer.writeBoolean(value.bounced());
        buffer.writeDouble(value.x());
        buffer.writeDouble(value.y());
        buffer.writeDouble(value.z());
        buffer.writeDouble(value.vx());
        buffer.writeDouble(value.vy());
        buffer.writeDouble(value.vz());
    }

    private static ProjectileBouncePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ProjectileBouncePayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    public static void handle(ProjectileBouncePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GenericSpellOverrideServerEvents.acceptClientBounceState(
                    payload.entityId(),
                    payload.remainingBounces()
            );

            if (!payload.bounced()) return;
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (!(entity instanceof Projectile projectile)) return;

            projectile.setPos(payload.x(), payload.y(), payload.z());
            projectile.setDeltaMovement(payload.vx(), payload.vy(), payload.vz());
            projectile.hasImpulse = true;
        });
    }

    @Override
    public Type<ProjectileBouncePayload> type() {
        return TYPE;
    }
}
