package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.projectile.PiercingProjectileContext;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void mageadditions$keepPiercingProjectileAlive(CallbackInfo ci) {
        if (PiercingProjectileContext.shouldPreventDiscard(this)) {
            ci.cancel();
        }
    }
}
