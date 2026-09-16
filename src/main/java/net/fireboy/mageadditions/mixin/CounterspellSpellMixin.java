package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.spells.ender.CounterspellSpell;
import net.fireboy.mageadditions.spell.CounterspellHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces only CounterspellSpell#onCast when the Mage Additions Counterspell
 * patch is enabled. If disabled, Iron's original implementation runs untouched.
 */
@Mixin(value = CounterspellSpell.class, remap = false)
public abstract class CounterspellSpellMixin {

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
