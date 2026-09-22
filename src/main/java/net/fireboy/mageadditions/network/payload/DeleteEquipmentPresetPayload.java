package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteEquipmentPresetPayload(String name) implements CustomPacketPayload {
    public static final Type<DeleteEquipmentPresetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "delete_equipment_preset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteEquipmentPresetPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.name(), 32),
        buf -> new DeleteEquipmentPresetPayload(buf.readUtf(32))
    );
    @Override public Type<DeleteEquipmentPresetPayload> type() { return TYPE; }
}
