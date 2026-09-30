package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.spells.ArrowVolleyEntity;
import io.redspace.ironsspellbooks.entity.spells.small_magic_arrow.SmallMagicArrow;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.fireboy.mageadditions.rework.ArrowVolleyRework;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reworks Iron's Arrow Volley emitter into a single forward-facing cone burst.
 *
 * Iron's normal ArrowVolleyEntity sits near the target and emits one horizontal
 * row every five ticks. When the Arrow Volley rework is enabled, this mixin
 * instead uses the same rows * arrowsPerRow count, launches the entire volley
 * from the caster on the emitter's first server tick, and applies the configured
 * projectile speed / small per-level damage growth.
 */
@Mixin(value = ArrowVolleyEntity.class, remap = false)
public abstract class ArrowVolleyEntityMixin {
    private static final double GOLDEN_ANGLE_RADIANS = Math.PI * (3.0D - Math.sqrt(5.0D));
    private static final double MUZZLE_DISTANCE = 0.70D;

    @Shadow(remap = false)
    int rows;

    @Shadow(remap = false)
    int arrowsPerRow;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageadditions$fireForwardCone(CallbackInfo ci) {
        if (!ArrowVolleyRework.enabled()) {
            return;
        }

        ArrowVolleyEntity volley = (ArrowVolleyEntity) (Object) this;
        Level level = volley.level();

        // The server owns projectile creation. The temporary controller will be
        // removed by the server immediately after the burst and disappear client-side.
        if (level.isClientSide) {
            return;
        }

        Entity caster = volley.getOwner();
        if (caster == null) {
            // Do not eat malformed/addon-created volleys. Let Iron's original tick
            // path handle anything without a valid owner.
            return;
        }

        int projectileCount = Math.max(1, rows * arrowsPerRow);

        // Iron's spell stores rows = 4 + spellLevel in this controller.
        int spellLevel = Math.max(1, rows - 4);
        double perArrowDamage = volley.getDamage()
                + ArrowVolleyRework.damagePerLevel() * Math.max(0, spellLevel - 1);

        Vec3 forward = caster.getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));

        // Looking almost perfectly vertical makes forward x world-up degenerate.
        // Fall back to a horizontal right vector derived from the caster's yaw.
        if (right.lengthSqr() < 1.0E-8D) {
            double yaw = Math.toRadians(caster.getYRot());
            right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        } else {
            right = right.normalize();
        }

        Vec3 up = right.cross(forward).normalize();
        Vec3 spawn = caster.getEyePosition().add(forward.scale(MUZZLE_DISTANCE));
        double maxSlope = Math.tan(Math.toRadians(ArrowVolleyRework.coneAngleDegrees() * 0.5D));
        ArrowVolleyRework.VolleyCast cast = ArrowVolleyRework.beginCast(spawn);

        for (int index = 0; index < projectileCount; index++) {
            // Keep one projectile exactly on the crosshair, then fill the rest of
            // the cone evenly using a golden-angle disc. sqrt(radius) keeps the
            // projectile density approximately uniform rather than crowding the axis.
            double radialSlope;
            double theta;
            if (index == 0 || projectileCount == 1) {
                radialSlope = 0.0D;
                theta = 0.0D;
            } else {
                double normalizedRadius = Math.sqrt((index - 0.5D) / (projectileCount - 1.0D));
                radialSlope = normalizedRadius * maxSlope;
                theta = index * GOLDEN_ANGLE_RADIANS;
            }

            Vec3 direction = forward
                    .add(right.scale(Math.cos(theta) * radialSlope))
                    .add(up.scale(Math.sin(theta) * radialSlope))
                    .normalize();

            SmallMagicArrow arrow = new SmallMagicArrow(level, caster);
            arrow.setDamage((float) perArrowDamage);
            arrow.setPos(spawn.x, spawn.y, spawn.z);
            arrow.shoot(direction.scale(ArrowVolleyRework.projectileSpeed()));
            arrow.setOwner(caster);
            ArrowVolleyRework.registerProjectile(arrow, cast);
            level.addFreshEntity(arrow);
        }

        // One combined muzzle effect instead of Iron's sound/particles once per row.
        MagicManager.spawnParticles(
                level,
                ParticleTypes.FIREWORK,
                spawn.x,
                spawn.y,
                spawn.z,
                Math.min(16, Math.max(6, projectileCount / 4)),
                0.20D,
                0.20D,
                0.20D,
                0.08D,
                false
        );
        level.playSound(
                null,
                spawn.x,
                spawn.y,
                spawn.z,
                SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.NEUTRAL,
                2.5F,
                1.15F
        );
        level.playSound(
                null,
                spawn.x,
                spawn.y,
                spawn.z,
                SoundRegistry.BOW_SHOOT.get(),
                SoundSource.NEUTRAL,
                2.0F,
                1.8F
        );

        volley.discard();
        ci.cancel();
    }
}
