package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.projectile.PiercingProjectileContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void mageadditions$preventMirrorImagePlayerPush(Entity other, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof Player first) || !(other instanceof Player second)) {
            return;
        }
        if (first.getUUID().equals(second.getUUID())) {
            return;
        }

        // Mirror Image clones deliberately reuse the caster's profile name while
        // having a different UUID. On clients they become ordinary RemotePlayers,
        // so server-side FakePlayer#isPushable alone cannot stop local collision
        // prediction. Suppress only this duplicate-profile player pair.
        if (first.getGameProfile().getName().equals(second.getGameProfile().getName())) {
            ci.cancel();
        }
    }

}

