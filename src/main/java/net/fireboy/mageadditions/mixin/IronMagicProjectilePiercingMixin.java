package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.fireboy.mageadditions.registry.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Iron's native piercing state to magic projectiles while their owner
 * has Mage Additions' Piercing effect.
 *
 * Iron's Spellbooks 1.21.1-3.14.8 exposes setPierceLevel(int), but does not yet
 * expose the later setInfinitePiercing() convenience method. A pierce level of
 * -1 is Iron's infinite-piercing state.
 */
@Mixin(value = AbstractMagicProjectile.class, remap = false)
public abstract class IronMagicProjectilePiercingMixin {

    @Inject(method = "handleHitDetection", at = @At("HEAD"), remap = false)
    private void mageadditions$applyPiercingEffect(CallbackInfo ci) {
        AbstractMagicProjectile projectile = (AbstractMagicProjectile) (Object) this;
        Entity owner = projectile.getOwner();

        if (owner instanceof LivingEntity living && living.hasEffect(ModEffects.PIERCING)) {
            projectile.setPierceLevel(-1);
        }
    }
}
