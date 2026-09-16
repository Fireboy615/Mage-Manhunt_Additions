package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.spells.ender.CounterspellSpell;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.config.CounterspellConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
 * Counterspell behaviour replacement.
 *
 * CONE is the v0.2 forward-area version.
 * TARGETED uses Iron's own target-lock casting system (the same family of
 * mechanics used by targeted LONG spells): select a living anti-magic target
 * before casting, keep that target in MagicData, then counterspell it on finish.
 */
public final class CounterspellHandler {
    private static volatile Settings settings = Settings.defaults();

    private CounterspellHandler() {}

    public static void reload(CounterspellConfig raw) {
        if (raw == null) {
            settings = Settings.defaults();
            return;
        }

        Mode mode = Mode.parse(raw.mode);
        if (mode == null) {
            MageAdditions.LOGGER.warn("Unknown counterspell mode '{}'; using 'cone'", raw.mode);
            mode = Mode.CONE;
        }

        double range = finiteClamp(raw.range, 0.0, 64.0, 6.0);
        double aimAssist = finiteClamp(raw.aim_assist, 0.0, 3.0, 0.35);
        double angle = finiteClamp(raw.angle_degrees, 1.0, 180.0, 90.0);
        int castTimeTicks = Math.max(0, Math.min(raw.cast_time_ticks, 72_000));
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
            mode,
            castTimeTicks,
            range,
            aimAssist,
            angle,
            raw.require_line_of_sight,
            targetMode,
            raw.debug_particles
        );

        MageAdditions.LOGGER.info(
            "Counterspell patch: enabled={}, mode={}, castTime={}t, range={}, angle={} degrees, lineOfSight={}, targetMode={}",
            raw.enabled,
            mode.name().toLowerCase(Locale.ROOT),
            castTimeTicks,
            range,
            angle,
            raw.require_line_of_sight,
            targetMode.name().toLowerCase(Locale.ROOT)
        );
    }

    public static boolean isEnabled() {
        return settings.enabled;
    }

    public static boolean isTargetedMode() {
        Settings current = settings;
        return current.enabled && current.mode == Mode.TARGETED;
    }

    public static boolean isTargetedCounterspell(AbstractSpell spell) {
        return isTargetedMode() && spell instanceof CounterspellSpell;
    }

    /**
     * Makes targeted Counterspell a real LONG cast without enabling delayed
     * INSTANT behaviour globally. Generic per-spell cast_time_overrides can still
     * override this value afterward.
     */
    public static int getBaseCastTime(AbstractSpell spell, int originalTicks) {
        Settings current = settings;
        if (current.enabled && current.mode == Mode.TARGETED && spell instanceof CounterspellSpell) {
            return current.castTimeTicks;
        }
        return originalTicks;
    }

    /**
     * Uses Iron's built-in target acquisition and TargetEntityCastData. This is
     * intentionally restricted to LivingEntity targets because that is the
     * target-lock data type used by Iron's targeted spells.
     */
    public static boolean acquireTarget(
        Level level,
        LivingEntity caster,
        MagicData playerMagicData,
        AbstractSpell spell
    ) {
        Settings current = settings;
        if (!current.enabled || current.mode != Mode.TARGETED) {
            return true;
        }

        int range = Math.max(1, (int) Math.round(current.range));
        float aimAssist = (float) current.aimAssist;

        return Utils.preCastTargetHelper(
            level,
            caster,
            playerMagicData,
            spell,
            range,
            aimAssist,
            true,
            target -> target != caster && Utils.validAntiMagicTarget(target)
        );
    }

    public static String describe() {
        Settings current = settings;
        if (!current.enabled) {
            return "disabled";
        }

        if (current.mode == Mode.TARGETED) {
            return String.format(
                Locale.ROOT,
                "targeted / %.1f blocks / %d ticks",
                current.range,
                current.castTimeTicks
            );
        }

        return String.format(
            Locale.ROOT,
            "cone / %.1f blocks / %.1f degrees / %s",
            current.range,
            current.angleDegrees,
            current.targetMode.name().toLowerCase(Locale.ROOT)
        );
    }

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

        if (current.mode == Mode.TARGETED) {
            castTargeted(level, caster, playerMagicData);
        } else {
            castCone(level, caster, playerMagicData, current);
        }

        spell.playSound(spell.getCastFinishSound(), caster);
    }

    private static void castTargeted(Level level, LivingEntity caster, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (playerMagicData.getAdditionalCastData() instanceof TargetEntityCastData targetData) {
            LivingEntity target = targetData.getTarget(serverLevel);
            if (target != null && target != caster && Utils.validAntiMagicTarget(target)) {
                applyCounterspell(caster, target, playerMagicData);
            }
        }
    }

    private static void castCone(
        Level level,
        LivingEntity caster,
        MagicData playerMagicData,
        Settings current
    ) {
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
            if (current.debugParticles) {
                spawnHitTrail(level, origin, candidate.targetPoint);
            }
        }

        if (current.debugParticles) {
            spawnConeOutline(level, origin, look, current.range, current.angleDegrees);
        }
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

    private enum Mode {
        CONE,
        TARGETED;

        static Mode parse(String raw) {
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
        Mode mode,
        int castTimeTicks,
        double range,
        double aimAssist,
        double angleDegrees,
        boolean requireLineOfSight,
        TargetMode targetMode,
        boolean debugParticles
    ) {
        static Settings defaults() {
            return new Settings(false, Mode.CONE, 12, 6.0, 0.35, 90.0, true, TargetMode.ALL, false);
        }
    }
}
