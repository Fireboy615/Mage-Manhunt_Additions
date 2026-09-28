package net.fireboy.mageadditions.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marks a living entity so projectiles it owns can continue through entity hits.
 * The actual projectile behavior is implemented by the projectile mixins.
 */
public final class PiercingEffect extends MagicMobEffect {
    public PiercingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB8E8FF);
    }
}
