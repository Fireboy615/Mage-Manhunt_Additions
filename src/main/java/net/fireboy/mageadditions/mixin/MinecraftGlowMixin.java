package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a private, per-client outline for teammates without globally setting Entity#glowing. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void mageadditions$showTeammateOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
            && entity instanceof Player player
            && !player.isSpectator()
            && ClientMinigameState.shouldHighlight(entity.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
