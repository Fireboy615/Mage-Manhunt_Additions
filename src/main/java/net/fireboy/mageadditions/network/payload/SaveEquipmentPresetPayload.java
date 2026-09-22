package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveEquipmentPresetPayload(String name) implements CustomPacketPayload {
    public static final Type<SaveEquipmentPresetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "save_equipment_preset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveEquipmentPresetPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.name(), 32),
        buf -> new SaveEquipmentPresetPayload(buf.readUtf(32))
    );
    @Override public Type<SaveEquipmentPresetPayload> type() { return TYPE; }
}
