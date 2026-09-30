package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.rework.FeatherFlightRework;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feather Flight rework grants complete fall-damage immunity. */
@Mixin(LivingEntity.class)
public abstract class FeatherFlightFallDamageMixin {
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void mageadditions$cancelFeatherFlightFallDamage(
            float fallDistance,
            float damageMultiplier,
            DamageSource source,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (FeatherFlightRework.fallDamageImmunity() && FeatherFlightRework.isActive(self)) {
            self.resetFallDistance();
            cir.setReturnValue(false);
        }
    }
}
