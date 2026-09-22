package net.fireboy.mageadditions.network.payload;

import java.util.ArrayList;
import java.util.List;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EquipmentPresetListPayload(List<String> names) implements CustomPacketPayload {
    public static final Type<EquipmentPresetListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "equipment_preset_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentPresetListPayload> STREAM_CODEC = StreamCodec.of(EquipmentPresetListPayload::encode, EquipmentPresetListPayload::decode);
    public EquipmentPresetListPayload { names = List.copyOf(names); }
    private static void encode(RegistryFriendlyByteBuf buf, EquipmentPresetListPayload payload) {
        buf.writeVarInt(payload.names().size());
        for (String name : payload.names()) buf.writeUtf(name, 32);
    }
    private static EquipmentPresetListPayload decode(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 128);
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) names.add(buf.readUtf(32));
        return new EquipmentPresetListPayload(names);
    }
    @Override public Type<EquipmentPresetListPayload> type() { return TYPE; }
}
