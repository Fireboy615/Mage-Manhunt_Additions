package net.fireboy.mageadditions.projectile;

import net.minecraft.world.entity.projectile.Projectile;

/**
 * Narrow context used only while a piercing projectile is processing an entity hit.
 * This lets EntityMixin ignore the projectile's normal discard call without
 * interfering with lifetime expiry or block impacts.
 */
public final class PiercingProjectileContext {
    private static final ThreadLocal<Projectile> ACTIVE_PROJECTILE = new ThreadLocal<>();

    private PiercingProjectileContext() {
    }

    public static void begin(Projectile projectile) {
        ACTIVE_PROJECTILE.set(projectile);
    }

    public static void end() {
        ACTIVE_PROJECTILE.remove();
    }

    public static boolean shouldPreventDiscard(Object entity) {
        return ACTIVE_PROJECTILE.get() == entity;
    }
}
