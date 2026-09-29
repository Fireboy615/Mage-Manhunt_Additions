package net.fireboy.mageadditions.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Hidden marker used while Mirror Image is active.
 *
 * <p>The normal invisibility effect handles gameplay visibility. This marker lets
 * the physical client suppress the caster's whole player render as well, so
 * armor and held items do not remain floating in mid-air.</p>
 */
public final class MirrorCloakEffect extends MagicMobEffect {
    public MirrorCloakEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8E7CFF);
    }
}
