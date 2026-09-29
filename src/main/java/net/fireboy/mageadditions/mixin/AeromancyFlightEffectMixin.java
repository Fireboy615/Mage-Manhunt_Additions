package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.rework.FeatherFlightRework;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces Aeromancy's native gravity-manipulation Flight effect while the rework is enabled. */
@Mixin(targets = "com.snackpirate.aeromancy.spells.feather_fall.FlightEffect", remap = false)
public abstract class AeromancyFlightEffectMixin {
    private static final ResourceLocation AEROMANCY_GRAVITY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("aero_additions", "flight.gravity");

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageadditions$replaceFlightTick(
            LivingEntity entity,
            int amplifier,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!FeatherFlightRework.enabled()) return;

        // Remove any native low-gravity modifier left by the original effect so
        // toggling the rework while the effect is active also fixes itself.
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(AEROMANCY_GRAVITY_MODIFIER);
        }

        FeatherFlightRework.applyJumpBoost(entity);
        cir.setReturnValue(true);
    }

    @Inject(method = "onEffectRemoved", at = @At("HEAD"), remap = false)
    private void mageadditions$removeJumpBoost(
            LivingEntity entity,
            int amplifier,
            CallbackInfo ci
    ) {
        FeatherFlightRework.removeJumpBoost(entity);
    }
}
