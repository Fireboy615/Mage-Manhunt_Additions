package net.fireboy.mageadditions.network.payload;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class RandomizeTeamsPayload implements CustomPacketPayload {
    public static final RandomizeTeamsPayload INSTANCE = new RandomizeTeamsPayload();
    public static final Type<RandomizeTeamsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "randomize_teams"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RandomizeTeamsPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    private RandomizeTeamsPayload() {}
    @Override public Type<RandomizeTeamsPayload> type() { return TYPE; }
}
