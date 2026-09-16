package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.config.CounterspellConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Replaces Counterspell's single long raycast with a configurable forward cone.
 *
 * Target selection is ours, but the anti-magic behaviour is intentionally kept
 * equivalent to Iron's Counterspell implementation: CounterSpellEvent is posted,
 * AntiMagicSusceptible is invoked, player/mob casts are cancelled, recasts are
 * cleared, and MagicMobEffects are removed.
 */
public final class CounterspellHandler {
    private static volatile Settings settings = Settings.defaults();

    private CounterspellHandler() {}

    public static void reload(CounterspellConfig raw) {
        if (raw == null) {
            settings = Settings.defaults();
            return;
        }

        double range = finiteClamp(raw.range, 0.0, 64.0, 6.0);
        double angle = finiteClamp(raw.angle_degrees, 1.0, 180.0, 90.0);
        TargetMode targetMode = TargetMode.parse(raw.target_mode);

        if (targetMode == null) {
            MageAdditions.LOGGER.warn(
                "Unknown counterspell target_mode '{}'; using 'all'",
                raw.target_mode
            );
            targetMode = TargetMode.ALL;
        }

        settings = new Settings(
            raw.enabled,
            range,
            angle,
            raw.require_line_of_sight,
            targetMode
        );

        MageAdditions.LOGGER.info(
            "Counterspell patch: enabled={}, range={}, angle={} degrees, lineOfSight={}, targetMode={}",
            raw.enabled,
            range,
            angle,
            raw.require_line_of_sight,
            targetMode.name().toLowerCase(Locale.ROOT)
        );
    }

    public static boolean isEnabled() {
        return settings.enabled;
    }

    public static String describe() {
        Settings current = settings;
        if (!current.enabled) {
            return "disabled";
        }
        return String.format(
            Locale.ROOT,
            "%.1f blocks / %.1f degrees / %s",
            current.range,
            current.angleDegrees,
            current.targetMode.name().toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Executes the patched Counterspell effect. Called server-side from the mixin.
     */
    public static void cast(
        Level level,
        LivingEntity caster,
        MagicData playerMagicData,
        AbstractSpell spell
    ) {
        Settings current = settings;
        if (!current.enabled) {
            return;
        }

        Vec3 origin = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        double minDot = Math.cos(Math.toRadians(current.angleDegrees * 0.5));

        AABB searchBox = caster.getBoundingBox().inflate(current.range);
        List<Candidate> candidates = new ArrayList<>();

        for (Entity target : level.getEntities(caster, searchBox, Utils::validAntiMagicTarget)) {
            Vec3 targetPoint = target.getBoundingBox().getCenter();
            Vec3 offset = targetPoint.subtract(origin);
            double distanceSquared = offset.lengthSqr();

            if (distanceSquared <= 1.0e-8 || distanceSquared > current.range * current.range) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double dot = look.dot(offset.scale(1.0 / distance));
            if (dot < minDot) {
                continue;
            }

            if (current.requireLineOfSight && !caster.hasLineOfSight(target)) {
                continue;
            }

            candidates.add(new Candidate(target, targetPoint, distanceSquared, dot));
        }

        List<Candidate> selected = selectTargets(candidates, current.targetMode);
        for (Candidate candidate : selected) {
            applyCounterspell(caster, candidate.entity, playerMagicData);
            spawnHitTrail(level, origin, candidate.targetPoint);
        }

        // Draw a light cone outline every cast so the test build makes the new
        // targeting geometry visible even when no valid target is inside it.
        spawnConeOutline(level, origin, look, current.range, current.angleDegrees);

        // Counterspell's original onCast ends with super.onCast(...), whose
        // relevant default behaviour is the normal cast-finish sound.
        spell.playSound(spell.getCastFinishSound(), caster);
    }

    private static List<Candidate> selectTargets(List<Candidate> candidates, TargetMode mode) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case ALL -> candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceSquared))
                .toList();
            case NEAREST -> candidates.stream()
                .min(Comparator.comparingDouble(Candidate::distanceSquared))
                .map(List::of)
                .orElseGet(List::of);
            case CROSSHAIR -> candidates.stream()
                .max(Comparator.comparingDouble(Candidate::dot)
                    .thenComparing(Comparator.comparingDouble(Candidate::distanceSquared).reversed()))
                .map(List::of)
                .orElseGet(List::of);
        };
    }

    private static void applyCounterspell(
        LivingEntity caster,
        Entity target,
        MagicData playerMagicData
    ) {
        if (NeoForge.EVENT_BUS.post(new CounterSpellEvent(caster, target)).isCanceled()) {
            return;
        }

        // This block mirrors Iron's own Counterspell anti-magic rules, including
        // its special treatment of the caster's own summons.
        if (target instanceof AntiMagicSusceptible antiMagicSusceptible) {
            if (antiMagicSusceptible instanceof IMagicSummon summon) {
                if (summon.getSummoner() == caster) {
                    if (summon instanceof Mob mob && mob.getTarget() == null) {
                        antiMagicSusceptible.onAntiMagic(playerMagicData);
                    }
                } else {
                    antiMagicSusceptible.onAntiMagic(playerMagicData);
                }
            } else {
                antiMagicSusceptible.onAntiMagic(playerMagicData);
            }
        } else if (target instanceof ServerPlayer serverPlayer) {
            Utils.serverSideCancelCast(serverPlayer, true);
            MagicData.getPlayerMagicData(serverPlayer)
                .getPlayerRecasts()
                .removeAll(RecastResult.COUNTERSPELL);
        } else if (target instanceof IMagicEntity magicEntity) {
            magicEntity.cancelCast();
        }

        if (target instanceof LivingEntity livingEntity) {
            // Copy the keys first to avoid concurrent modification while effects are removed.
            for (Holder<MobEffect> mobEffect : livingEntity.getActiveEffectsMap().keySet().stream().toList()) {
                if (mobEffect.value() instanceof MagicMobEffect) {
                    livingEntity.removeEffect(mobEffect);
                }
            }
        }
    }

    private static void spawnHitTrail(Level level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        if (distance <= 0.001) {
            return;
        }

        Vec3 direction = delta.scale(1.0 / distance);
        for (double d = 0.75; d < distance; d += 0.5) {
            Vec3 pos = start.add(direction.scale(d));
            spawnParticle(level, pos);
        }
    }

    private static void spawnConeOutline(
        Level level,
        Vec3 origin,
        Vec3 look,
        double range,
        double fullAngleDegrees
    ) {
        if (range <= 0.0) {
            return;
        }

        double halfAngle = Math.toRadians(fullAngleDegrees * 0.5);
        double sin = Math.sin(halfAngle);
        double cos = Math.cos(halfAngle);

        // Build an orthonormal basis around the player's look vector.
        Vec3 referenceUp = Math.abs(look.y) > 0.95
            ? new Vec3(1.0, 0.0, 0.0)
            : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = look.cross(referenceUp).normalize();
        Vec3 up = right.cross(look).normalize();

        int ringPoints = 10;
        double step = Math.max(1.25, range / 4.0);

        for (double slantDistance = step; slantDistance <= range + 0.001; slantDistance += step) {
            double forwardDistance = slantDistance * cos;
            double ringRadius = slantDistance * sin;
            Vec3 ringCenter = origin.add(look.scale(forwardDistance));

            for (int i = 0; i < ringPoints; i++) {
                double theta = Math.PI * 2.0 * i / ringPoints;
                Vec3 radial = right.scale(Math.cos(theta) * ringRadius)
                    .add(up.scale(Math.sin(theta) * ringRadius));
                spawnParticle(level, ringCenter.add(radial));
            }
        }
    }

    private static void spawnParticle(Level level, Vec3 pos) {
        MagicManager.spawnParticles(
            level,
            ParticleTypes.ENCHANT,
            pos.x,
            pos.y,
            pos.z,
            1,
            0,
            0,
            0,
            0,
            false
        );
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private enum TargetMode {
        ALL,
        NEAREST,
        CROSSHAIR;

        static TargetMode parse(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record Candidate(
        Entity entity,
        Vec3 targetPoint,
        double distanceSquared,
        double dot
    ) {}

    private record Settings(
        boolean enabled,
        double range,
        double angleDegrees,
        boolean requireLineOfSight,
        TargetMode targetMode
    ) {
        static Settings defaults() {
            return new Settings(false, 6.0, 90.0, true, TargetMode.ALL);
        }
    }
}
