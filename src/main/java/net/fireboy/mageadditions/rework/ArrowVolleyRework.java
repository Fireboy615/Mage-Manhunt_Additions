package net.fireboy.mageadditions.rework;

import io.redspace.ironsspellbooks.entity.spells.small_magic_arrow.SmallMagicArrow;
import net.fireboy.mageadditions.config.ArrowVolleyConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runtime settings and per-cast bookkeeping for the Arrow Volley cone rework. */
public final class ArrowVolleyRework {
    private static volatile Settings settings = Settings.defaults();

    /**
     * Weak keys ensure projectile bookkeeping disappears naturally once the arrow
     * entity leaves the world. Every arrow in one burst points at the same cast.
     */
    private static final Map<Entity, VolleyCast> PROJECTILE_CASTS = new WeakHashMap<>();

    private ArrowVolleyRework() {}

    public static void reload(ArrowVolleyConfig config) {
        ArrowVolleyConfig source = config != null ? config : new ArrowVolleyConfig();
        double closeDistance = Mth.clamp(source.close_range_distance, 0.0D, 32.0D);
        double fullDistance = Mth.clamp(source.full_damage_distance, closeDistance + 0.01D, 64.0D);
        settings = new Settings(
                source.enabled,
                Mth.clamp(source.cone_angle_degrees, 1.0D, 120.0D),
                Mth.clamp(source.projectile_speed, 0.05D, 4.0D),
                Mth.clamp(source.damage_per_level, 0.0D, 20.0D),
                Mth.clamp(source.max_hits_per_target, 1, 100),
                closeDistance,
                Mth.clamp(source.close_range_damage_multiplier, 0.0D, 1.0D),
                fullDistance
        );
    }

    public static boolean enabled() { return settings.enabled(); }
    public static double coneAngleDegrees() { return settings.coneAngleDegrees(); }
    public static double projectileSpeed() { return settings.projectileSpeed(); }
    public static double damagePerLevel() { return settings.damagePerLevel(); }
    public static int maxHitsPerTarget() { return settings.maxHitsPerTarget(); }
    public static double closeRangeDistance() { return settings.closeRangeDistance(); }
    public static double closeRangeDamageMultiplier() { return settings.closeRangeDamageMultiplier(); }
    public static double fullDamageDistance() { return settings.fullDamageDistance(); }

    public static VolleyCast beginCast(Vec3 origin) {
        return new VolleyCast(origin);
    }

    public static void registerProjectile(SmallMagicArrow arrow, VolleyCast cast) {
        if (arrow != null && cast != null) {
            PROJECTILE_CASTS.put(arrow, cast);
        }
    }

    /**
     * Applies point-blank protection only to arrows created by Mage Additions'
     * cone rework. Normal SmallMagicArrows from other spells are untouched.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!enabled() || event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }

        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof SmallMagicArrow)) {
            return;
        }

        VolleyCast cast = PROJECTILE_CASTS.get(direct);
        if (cast == null) {
            return;
        }

        UUID targetId = event.getEntity().getUUID();
        int previousHits = cast.hitsByTarget.getOrDefault(targetId, 0);
        if (previousHits >= maxHitsPerTarget()) {
            event.setCanceled(true);
            return;
        }
        cast.hitsByTarget.put(targetId, previousHits + 1);

        double distance = event.getEntity().getBoundingBox().getCenter().distanceTo(cast.origin);
        double multiplier = damageMultiplierForDistance(distance);
        if (multiplier < 0.999999D) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    private static double damageMultiplierForDistance(double distance) {
        Settings current = settings;
        if (distance <= current.closeRangeDistance()) {
            return current.closeRangeDamageMultiplier();
        }
        if (distance >= current.fullDamageDistance()) {
            return 1.0D;
        }

        double span = current.fullDamageDistance() - current.closeRangeDistance();
        if (span <= 1.0E-6D) {
            return 1.0D;
        }

        double progress = (distance - current.closeRangeDistance()) / span;
        return current.closeRangeDamageMultiplier()
                + (1.0D - current.closeRangeDamageMultiplier()) * progress;
    }

    public static final class VolleyCast {
        private final Vec3 origin;
        private final Map<UUID, Integer> hitsByTarget = new HashMap<>();

        private VolleyCast(Vec3 origin) {
            this.origin = origin;
        }
    }

    private record Settings(
            boolean enabled,
            double coneAngleDegrees,
            double projectileSpeed,
            double damagePerLevel,
            int maxHitsPerTarget,
            double closeRangeDistance,
            double closeRangeDamageMultiplier,
            double fullDamageDistance
    ) {
        static Settings defaults() {
            ArrowVolleyConfig defaults = new ArrowVolleyConfig();
            return new Settings(
                    defaults.enabled,
                    defaults.cone_angle_degrees,
                    defaults.projectile_speed,
                    defaults.damage_per_level,
                    defaults.max_hits_per_target,
                    defaults.close_range_distance,
                    defaults.close_range_damage_multiplier,
                    defaults.full_damage_distance
            );
        }
    }
}
