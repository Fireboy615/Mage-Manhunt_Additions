package net.fireboy.mageadditions.rework;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Shared helpers/constants for the Aeromancy Feather Flight rework. */
public final class FeatherFlightRework {
    private static final ResourceLocation FLIGHT_EFFECT =
            ResourceLocation.fromNamespaceAndPath("aero_additions", "flight");
    public static final ResourceLocation JUMP_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("mageadditions", "feather_flight_jump");

    /** Adds 0.16 to the normal 0.42 jump strength (~38% higher initial jump velocity). */
    private static final double EXTRA_JUMP_STRENGTH = 0.16D;

    private FeatherFlightRework() {}

    public static boolean enabled() {
        return CastTimeOverrides.spellReworksEnabled();
    }

    public static boolean isActive(LivingEntity entity) {
        if (!enabled() || entity == null) return false;
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
            if (FLIGHT_EFFECT.equals(id)) return true;
        }
        return false;
    }

    public static void applyJumpBoost(LivingEntity entity) {
        AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null) return;
        jump.addOrUpdateTransientModifier(new AttributeModifier(
                JUMP_MODIFIER,
                EXTRA_JUMP_STRENGTH,
                AttributeModifier.Operation.ADD_VALUE
        ));
    }

    public static void removeJumpBoost(LivingEntity entity) {
        AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(JUMP_MODIFIER);
        }
    }
}
