package net.fireboy.mageadditions.rework;

import net.fireboy.mageadditions.config.FeatherFlightConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Runtime settings/helpers for the Aeromancy Feather Flight rework. */
public final class FeatherFlightRework {
    private static final ResourceLocation FLIGHT_EFFECT =
            ResourceLocation.fromNamespaceAndPath("aero_additions", "flight");
    public static final ResourceLocation JUMP_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("mageadditions", "feather_flight_jump");

    private static volatile Settings settings = Settings.defaults();

    private FeatherFlightRework() {}

    public static void reload(FeatherFlightConfig config) {
        FeatherFlightConfig source = config != null ? config : new FeatherFlightConfig();
        settings = new Settings(
                source.enabled,
                Mth.clamp(source.slow_fall_speed, 0.01D, 2.0D),
                Mth.clamp(source.air_acceleration, 0.0D, 0.25D),
                Mth.clamp(source.max_horizontal_speed, 0.01D, 3.0D),
                Mth.clamp(source.extra_jump_strength, 0.0D, 2.0D),
                source.fall_damage_immunity
        );
    }

    public static boolean enabled() { return settings.enabled(); }
    public static double slowFallSpeed() { return settings.slowFallSpeed(); }
    public static double airAcceleration() { return settings.airAcceleration(); }
    public static double maxHorizontalSpeed() { return settings.maxHorizontalSpeed(); }
    public static double extraJumpStrength() { return settings.extraJumpStrength(); }
    public static boolean fallDamageImmunity() { return settings.fallDamageImmunity(); }

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
                extraJumpStrength(),
                AttributeModifier.Operation.ADD_VALUE
        ));
    }

    public static void removeJumpBoost(LivingEntity entity) {
        AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(JUMP_MODIFIER);
        }
    }

    private record Settings(
            boolean enabled,
            double slowFallSpeed,
            double airAcceleration,
            double maxHorizontalSpeed,
            double extraJumpStrength,
            boolean fallDamageImmunity
    ) {
        static Settings defaults() {
            FeatherFlightConfig defaults = new FeatherFlightConfig();
            return new Settings(
                    defaults.enabled,
                    defaults.slow_fall_speed,
                    defaults.air_acceleration,
                    defaults.max_horizontal_speed,
                    defaults.extra_jump_strength,
                    defaults.fall_damage_immunity
            );
        }
    }
}
