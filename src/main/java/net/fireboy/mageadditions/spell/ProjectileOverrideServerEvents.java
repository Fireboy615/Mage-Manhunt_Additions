package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Runtime hook for capability-driven generic projectile overrides. */
public final class ProjectileOverrideServerEvents {
    private ProjectileOverrideServerEvents() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }

        Entity owner = projectile.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }

        MagicData magic = MagicData.getPlayerMagicData(player);
        String spellId = magic.getCastingSpellId();
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null || spell == SpellRegistry.none()) {
            return;
        }
        if (!SpellCapabilities.detect(spell).projectileSpeed()) {
            return;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        double nativeSpeed = velocity.length();
        if (nativeSpeed <= 1.0E-9) {
            return;
        }

        double resolved = CastTimeOverrides.resolveProjectileSpeed(spell, nativeSpeed);
        if (Math.abs(resolved - nativeSpeed) <= 1.0E-9) {
            return;
        }
        projectile.setDeltaMovement(velocity.scale(resolved / nativeSpeed));
        projectile.hasImpulse = true;
    }
}
