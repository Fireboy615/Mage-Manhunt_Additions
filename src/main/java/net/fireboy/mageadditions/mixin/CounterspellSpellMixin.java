package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.spells.ender.CounterspellSpell;
import net.fireboy.mageadditions.spell.CounterspellHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counterspell-specific hooks.
 *
 * In TARGETED mode Counterspell becomes a LONG cast. Target acquisition itself
 * is hooked in AbstractSpellMixin because Counterspell inherits the default
 * checkPreCastConditions implementation rather than overriding it.
 */
@Mixin(value = CounterspellSpell.class, remap = false)
public abstract class CounterspellSpellMixin {

    @Inject(method = "getCastType", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageAdditions$counterspellCastType(CallbackInfoReturnable<CastType> cir) {
        if (CounterspellHandler.isTargetedMode()) {
            cir.setReturnValue(CastType.LONG);
        }
    }

    @Inject(method = "onCast", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageAdditions$replaceCounterspellTargeting(
        Level level,
        int spellLevel,
        LivingEntity caster,
        CastSource castSource,
        MagicData playerMagicData,
        CallbackInfo ci
    ) {
        if (!CounterspellHandler.isEnabled()) {
            return;
        }

        CounterspellHandler.cast(
            level,
            caster,
            playerMagicData,
            (AbstractSpell) (Object) this
        );
        ci.cancel();
    }
}
