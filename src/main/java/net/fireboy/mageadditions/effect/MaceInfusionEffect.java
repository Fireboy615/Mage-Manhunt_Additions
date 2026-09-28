package net.fireboy.mageadditions.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marks a player so their non-mace main-hand weapon gains vanilla-style mace
 * smash attacks while the effect is active.
 */
public final class MaceInfusionEffect extends MagicMobEffect {
    public MaceInfusionEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD6B56C);
    }
}
