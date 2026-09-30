package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.client.state.ClientCaptureState;
import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds private client-only outlines for Mage Additions features without setting
 * Entity#glowing globally on the server.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void mageadditions$showPrivateOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }

        if (ClientCaptureState.isSelectedTarget(entity.getId())) {
            cir.setReturnValue(true);
            return;
        }

        if (entity instanceof Player player
                && !player.isSpectator()
                && ClientMinigameState.shouldHighlight(entity.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
