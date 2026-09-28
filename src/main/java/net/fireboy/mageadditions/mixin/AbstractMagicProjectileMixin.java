package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.spell.GenericSpellOverrideServerEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consumes configured block bounces before a normal projectile gets to run its
 * own impact payload. This is the stable path for Iron projectiles such as Fire
 * Arrow, Magic Missile, Magma Bomb, and other projectiles that use vanilla's
 * Projectile#hitTargetOrDeflectSelf collision pipeline.
 */
@Mixin(Projectile.class)
public abstract class AbstractMagicProjectileMixin {

    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void mageadditions$bounceBeforeAnyImpact(
            HitResult hitResult,
            CallbackInfoReturnable<ProjectileDeflection> cir
    ) {
        if (hitResult instanceof BlockHitResult blockHit
                && GenericSpellOverrideServerEvents.tryHandleIronBlockBounce(
                        (Projectile) (Object) this,
                        blockHit
                )) {
            cir.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}
