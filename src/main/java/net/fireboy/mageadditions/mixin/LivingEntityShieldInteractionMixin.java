package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.spell.ShieldInteractionHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Applies per-spell shield-disable rules at vanilla's actual shield reaction.
 *
 * <p>Only SpellDamageSource hits are changed. Ordinary melee and every other
 * vanilla/modded damage source continue through the original path untouched.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldInteractionMixin {

    @Redirect(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;blockUsingShield(Lnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private void mageadditions$applySpellShieldInteraction(
            LivingEntity defender,
            LivingEntity attacker,
            DamageSource source,
            float amount
    ) {
        CastTimeOverrides.ShieldInteraction mode = ShieldInteractionHandler.mode(source);

        boolean allowDisable = switch (mode) {
            case VANILLA -> true;
            case AXE_ONLY -> ShieldInteractionHandler.isHoldingAxe(attacker);
            case CANNOT_DISABLE -> false;
        };

        if (allowDisable) {
            // Full vanilla path. For a Player defender this includes
            // Player#blockUsingShield and its canDisableShield check.
            ((LivingEntityShieldInvoker) defender).mageadditions$invokeBlockUsingShield(attacker);
        } else {
            // Preserve the normal blocked-hit reaction without entering the
            // Player override that applies the shield cooldown.
            ((LivingEntityShieldInvoker) attacker).mageadditions$invokeBlockedByShield(defender);
        }
    }
}
