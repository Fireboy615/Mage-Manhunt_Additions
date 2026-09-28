package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.spell.GenericSpellOverrideServerEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blood Slash bypasses vanilla Projectile#hitTargetOrDeflectSelf and calls its
 * own onHitBlock method directly. Intercept that exact block impact so it uses
 * the same generic bounce state as every other spell projectile.
 *
 * The target is named as a string so this remains tolerant of Iron's versions
 * where the class is not present at compile time.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile", remap = false)
public abstract class BloodSlashProjectileMixin {

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void mageadditions$bounceBeforeBloodSlashDiscard(BlockHitResult hit, CallbackInfo ci) {
        if (GenericSpellOverrideServerEvents.tryHandleIronBlockBounce(
                (Projectile) (Object) this,
                hit
        )) {
            ci.cancel();
        }
    }
}
