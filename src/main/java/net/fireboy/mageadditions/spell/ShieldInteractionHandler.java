package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Resolves Mage Additions' per-spell shield-disable behavior. */
public final class ShieldInteractionHandler {
    private ShieldInteractionHandler() {}

    public static CastTimeOverrides.ShieldInteraction mode(DamageSource source) {
        if (!(source instanceof SpellDamageSource spellDamageSource)) {
            return CastTimeOverrides.ShieldInteraction.VANILLA;
        }
        return CastTimeOverrides.behavior(spellDamageSource.spell()).shieldInteraction();
    }

    public static boolean isHoldingAxe(LivingEntity attacker) {
        return attacker != null && attacker.getMainHandItem().is(ItemTags.AXES);
    }
}
