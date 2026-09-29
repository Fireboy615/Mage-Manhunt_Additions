package net.fireboy.mageadditions.network.payload;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public final class CaptureStrugglePayload implements CustomPacketPayload {
 public static final CaptureStrugglePayload INSTANCE=new CaptureStrugglePayload();
 public static final Type<CaptureStrugglePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID,"capture_struggle"));
 public static final StreamCodec<RegistryFriendlyByteBuf,CaptureStrugglePayload> STREAM_CODEC=StreamCodec.unit(INSTANCE);
 private CaptureStrugglePayload(){} @Override public Type<CaptureStrugglePayload> type(){return TYPE;}
}
