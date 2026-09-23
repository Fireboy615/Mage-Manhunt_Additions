package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes an explicitly delayed INSTANT spell participate in Iron's normal
 * long-cast presentation/state machinery without permanently changing the
 * spell's declared CastType.
 *
 * The condition deliberately uses the already-resolved castDuration. Native
 * INSTANT spells always enter MagicData with duration 0, so duration > 0 is an
 * authoritative, addon-safe signal that something (Mage Additions' explicit
 * override in our case) intentionally delayed this cast.
 */
@Mixin(value = MagicData.class, remap = false)
public abstract class MagicDataMixin {
    @Shadow
    private CastType castType;

    @Inject(method = "initiateCast", at = @At("TAIL"), remap = false)
    private void mageAdditions$promoteDelayedInstantCast(
            AbstractSpell spell,
            int spellLevel,
            int castDuration,
            CastSource castSource,
            String castingEquipmentSlot,
            CallbackInfo ci
    ) {
        if (spell != null
                && spell.getCastType() == CastType.INSTANT
                && castDuration > 0) {
            this.castType = CastType.LONG;
        }
    }
}
