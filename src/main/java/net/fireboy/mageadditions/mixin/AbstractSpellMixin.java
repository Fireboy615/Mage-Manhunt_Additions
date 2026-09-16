package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.spell.CounterspellHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Central hooks for Iron's Spells 'n Spellbooks 1.21.1-3.14.8.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class AbstractSpellMixin {

    @Redirect(
        method = "attemptInitiateCast",
        at = @At(
            value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I",
            remap = false
        ),
        remap = false
    )
    private int mageAdditions$overrideEffectiveCastTime(
        AbstractSpell spell,
        int spellLevel,
        LivingEntity caster
    ) {
        int originalEffectiveTicks = spell.getEffectiveCastTime(spellLevel, caster);
        return CastTimeOverrides.resolve(spell, originalEffectiveTicks);
    }

    /**
     * Counterspell does not define checkPreCastConditions itself in Iron's; it
     * inherits this AbstractSpell method. Intercept only that one spell when the
     * targeted Counterspell mode is active and otherwise leave every spell alone.
     */
    @Inject(method = "checkPreCastConditions", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageAdditions$counterspellTargetLock(
        Level level,
        int spellLevel,
        LivingEntity caster,
        MagicData playerMagicData,
        CallbackInfoReturnable<Boolean> cir
    ) {
        AbstractSpell spell = (AbstractSpell) (Object) this;
        if (!CounterspellHandler.isTargetedCounterspell(spell)) {
            return;
        }

        cir.setReturnValue(CounterspellHandler.acquireTarget(
            level,
            caster,
            playerMagicData,
            spell
        ));
    }
}
