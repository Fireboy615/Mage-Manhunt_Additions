package net.fireboy.mageadditions.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Mirror Image attach NeoForge's shared FakePlayer connection to a harmless channel. */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    void mageadditions$setChannel(Channel channel);
}
