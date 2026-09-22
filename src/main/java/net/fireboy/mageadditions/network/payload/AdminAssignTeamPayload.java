package net.fireboy.mageadditions.network.payload;

import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminAssignTeamPayload(UUID playerId, ResourceLocation teamId) implements CustomPacketPayload {
    public static final Type<AdminAssignTeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "admin_assign_team"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminAssignTeamPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> { buf.writeUUID(payload.playerId()); ResourceLocation.STREAM_CODEC.encode(buf, payload.teamId()); },
        buf -> new AdminAssignTeamPayload(buf.readUUID(), ResourceLocation.STREAM_CODEC.decode(buf))
    );
    @Override public Type<AdminAssignTeamPayload> type() { return TYPE; }
}
