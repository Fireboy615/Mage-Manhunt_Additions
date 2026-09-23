package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only presentation for INSTANT spells that have been given a real delay. */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class AbstractSpellClientMixin {
    /**
     * OnCastFinished resets ClientMagicData before asking the spell for its finish
     * animation. Remember starts here so a server-authoritative live edit still
     * gets the matching finish animation even when the client config is stale.
     */
    private static final Set<String> MAGE_ADDITIONS$DELAYED_INSTANT_ANIMATIONS =
            ConcurrentHashMap.newKeySet();


    @Inject(method = "getCastStartAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageAdditions$delayedInstantStartAnimation(
            CallbackInfoReturnable<AnimationHolder> cir
    ) {
        AbstractSpell spell = (AbstractSpell) (Object) this;
        if (mageAdditions$isDelayedInstant(spell)) {
            MAGE_ADDITIONS$DELAYED_INSTANT_ANIMATIONS.add(spell.getSpellId());
            cir.setReturnValue(SpellAnimations.ANIMATION_LONG_CAST);
        }
    }

    @Inject(method = "getCastFinishAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageAdditions$delayedInstantFinishAnimation(
            CallbackInfoReturnable<AnimationHolder> cir
    ) {
        AbstractSpell spell = (AbstractSpell) (Object) this;
        if (MAGE_ADDITIONS$DELAYED_INSTANT_ANIMATIONS.remove(spell.getSpellId())
                || CastTimeOverrides.usesDelayedInstantPresentation(spell)) {
            cir.setReturnValue(SpellAnimations.ANIMATION_LONG_CAST_FINISH);
        }
    }

    private static boolean mageAdditions$isDelayedInstant(AbstractSpell spell) {
        if (spell == null || spell.getCastType() != CastType.INSTANT) {
            return false;
        }

        // The local player's UpdateCastingStatePacket is sent immediately before
        // OnCastStartedPacket. MagicDataMixin converts that active state to LONG,
        // so this path works even when a dedicated server changed the config live
        // and the client's on-disk config is different.
        if (ClientMagicData.isCasting()
                && spell.getSpellId().equals(ClientMagicData.getCastingSpellId())
                && ClientMagicData.getCastType() == CastType.LONG) {
            return true;
        }

        // Also covers third-person/remote-player presentation when client and
        // server share the same Mage Additions config (the normal modpack case).
        return CastTimeOverrides.usesDelayedInstantPresentation(spell);
    }
}
