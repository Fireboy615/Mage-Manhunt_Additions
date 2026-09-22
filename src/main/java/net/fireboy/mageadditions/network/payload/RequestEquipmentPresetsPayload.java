package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class RequestEquipmentPresetsPayload implements CustomPacketPayload {
    public static final RequestEquipmentPresetsPayload INSTANCE = new RequestEquipmentPresetsPayload();
    public static final Type<RequestEquipmentPresetsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "request_equipment_presets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEquipmentPresetsPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    private RequestEquipmentPresetsPayload() {}
    @Override public Type<RequestEquipmentPresetsPayload> type() { return TYPE; }
}
