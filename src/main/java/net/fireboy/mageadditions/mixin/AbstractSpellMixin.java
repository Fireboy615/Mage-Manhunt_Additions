package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.spell.CounterspellHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Central hooks for Iron's Spells 'n Spellbooks 1.21.1-3.14.x. */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class AbstractSpellMixin {

    @Redirect(
            method = "attemptInitiateCast",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I",
                    remap = false
            ),
            remap = false
    )
    private int mageAdditions$overrideEffectiveCastTime(
            AbstractSpell spell,
            int spellLevel,
            LivingEntity caster
    ) {
        int originalEffectiveTicks = spell.getEffectiveCastTime(spellLevel, caster);
        return CastTimeOverrides.resolve(spell, originalEffectiveTicks);
    }

    /**
     * Wrap Iron's virtual pre-cast check from the common player-cast path. Using
     * a redirect here rather than injecting into AbstractSpell.checkPreCastConditions
     * means addon/vanilla Iron's spells that override that method are covered too.
     */
    @Redirect(
            method = "attemptInitiateCast",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;checkPreCastConditions(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/magic/MagicData;)Z",
                    remap = false
            ),
            remap = false
    )
    private boolean mageAdditions$checkPreCastConditions(
            AbstractSpell spell,
            Level level,
            int spellLevel,
            LivingEntity caster,
            MagicData playerMagicData
    ) {
        CastTimeOverrides.BehaviorSettings behavior = CastTimeOverrides.behavior(spell);

        Double maxHeight = behavior.maxHeightAboveGround();
        if (maxHeight != null && heightAboveGround(level, caster, maxHeight) > maxHeight + 1.0E-3) {
            sendFailure(caster, "You are too far above the ground to cast this spell.");
            return false;
        }

        final boolean ironAllowsCast;
        if (CounterspellHandler.isTargetedCounterspell(spell)) {
            // Counterspell inherits AbstractSpell's always-true pre-cast check, so
            // its targeted rework supplies the real target acquisition here.
            ironAllowsCast = CounterspellHandler.acquireTarget(level, caster, playerMagicData, spell);
        } else {
            ironAllowsCast = spell.checkPreCastConditions(level, spellLevel, caster, playerMagicData);
        }

        if (!ironAllowsCast) {
            return false;
        }

        // The Utils target-helper mixin applies these restrictions before Iron's
        // sends its green target-lock packet. Keep this post-check as a safety net
        // for addon spells that build TargetEntityCastData without that helper.
        return passesTargetRestrictions(level, caster, playerMagicData, behavior);
    }

    private static double heightAboveGround(Level level, LivingEntity caster, double maxHeight) {
        if (caster.onGround()) {
            return 0.0;
        }

        Vec3 start = new Vec3(
                caster.getX(),
                caster.getBoundingBox().minY + 0.05,
                caster.getZ()
        );
        double traceDistance = Math.max(2.0, Math.min(1_000_002.0, maxHeight + 2.0));
        Vec3 end = start.add(0.0, -traceDistance, 0.0);
        HitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, start.y - hit.getLocation().y);
    }

    private static boolean passesTargetRestrictions(
            Level level,
            LivingEntity caster,
            MagicData playerMagicData,
            CastTimeOverrides.BehaviorSettings behavior
    ) {
        if (behavior.lineOfSightOverride() == null
                && behavior.minCastDistance() == null) {
            return true;
        }

        if (!(level instanceof ServerLevel serverLevel)
                || !(playerMagicData.getAdditionalCastData() instanceof TargetEntityCastData targetData)) {
            return true;
        }

        LivingEntity target = targetData.getTarget(serverLevel);
        if (target == null) {
            return true;
        }

        double distance = caster.distanceTo(target);
        Double minimum = behavior.minCastDistance();
        if (minimum != null && distance < minimum) {
            sendFailure(caster, "Target is too close for this spell.");
            return false;
        }

        if (Boolean.TRUE.equals(behavior.lineOfSightOverride()) && !caster.hasLineOfSight(target)) {
            sendFailure(caster, "This spell requires line of sight.");
            return false;
        }

        return true;
    }

    private static void sendFailure(LivingEntity caster, String message) {
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
        }
    }
}
