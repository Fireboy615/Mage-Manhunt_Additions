package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.spells.root.PreventDismount;
import io.redspace.ironsspellbooks.network.casting.SyncTargetingDataPacket;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.spell.SpellTargetingDefaults;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Applies Mage Additions target restrictions before Iron's sends its green
 * target-lock packet. This prevents invalid/out-of-range entities from being
 * highlighted as if the spell can actually cast on them.
 */
@Mixin(value = Utils.class, remap = false)
public abstract class UtilsTargetingMixin {

    @Inject(
            method = "preCastTargetHelper(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/magic/MagicData;Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;IFZLjava/util/function/Predicate;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void mageAdditions$filterTargetBeforeSync(
            Level level,
            LivingEntity caster,
            MagicData playerMagicData,
            AbstractSpell spell,
            int range,
            float aimAssist,
            boolean sendFailureMessage,
            Predicate<LivingEntity> filter,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SpellTargetingDefaults.observeNativeRange(spell, range);

        CastTimeOverrides.BehaviorSettings behavior = CastTimeOverrides.behavior(spell);
        Boolean lineOfSightOverride = behavior.lineOfSightOverride();
        Double minimum = behavior.minCastDistance();
        Double maximum = behavior.maxCastDistance();
        boolean hasRangeOverride = behavior.rangeOverride().enabled();

        // No targeting override: preserve Iron's exact original implementation.
        if (lineOfSightOverride == null && minimum == null && maximum == null && !hasRangeOverride) {
            return;
        }

        boolean requireLineOfSight = lineOfSightOverride == null
                ? SpellTargetingDefaults.DEFAULT_REQUIRE_LINE_OF_SIGHT
                : lineOfSightOverride;

        // Range changes the spell's actual target-acquisition distance. The
        // separate maximum-cast-distance option is a hard cap and never extends
        // range by itself.
        double searchRange = CastTimeOverrides.resolveTargetRange(spell, range);
        if (maximum != null) {
            searchRange = Math.min(searchRange, maximum);
        }
        searchRange = Math.max(0.0, Math.min(1_000_000.0, searchRange));

        HitResult target = Utils.raycastForEntity(
                level,
                caster,
                (float) searchRange,
                requireLineOfSight,
                aimAssist
        );

        LivingEntity livingTarget = null;
        if (target instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof PreventDismount) {
                if (entityHit.getEntity().getFirstPassenger() instanceof LivingEntity livingRooted
                        && filter.test(livingRooted)) {
                    livingTarget = livingRooted;
                }
            } else if (entityHit.getEntity() instanceof LivingEntity livingEntity && filter.test(livingEntity)) {
                livingTarget = livingEntity;
            } else if (entityHit.getEntity() instanceof PartEntity<?> partEntity
                    && partEntity.getParent() instanceof LivingEntity livingParent
                    && !caster.equals(livingParent)
                    && filter.test(livingParent)) {
                livingTarget = livingParent;
            }
        }

        if (livingTarget != null) {
            double distance = caster.distanceTo(livingTarget);
            if (minimum != null && distance < minimum) {
                failure(caster, "Target is too close for this spell.", sendFailureMessage);
                cir.setReturnValue(false);
                return;
            }
            if (maximum != null && distance > maximum) {
                failure(caster, "Target is too far away for this spell.", sendFailureMessage);
                cir.setReturnValue(false);
                return;
            }

            playerMagicData.setAdditionalCastData(new TargetEntityCastData(livingTarget));

            if (caster instanceof ServerPlayer serverPlayer) {
                if (spell.getCastType() != CastType.INSTANT) {
                    PacketDistributor.sendToPlayer(serverPlayer, new SyncTargetingDataPacket(livingTarget, spell));
                }
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable(
                                "ui.irons_spellbooks.spell_target_success",
                                livingTarget.getDisplayName().getString(),
                                spell.getDisplayName(serverPlayer)
                        ).withStyle(ChatFormatting.GREEN)
                ));
            }

            if (livingTarget instanceof ServerPlayer targetPlayer) {
                Utils.sendTargetedNotification(targetPlayer, caster, spell);
            }

            cir.setReturnValue(true);
            return;
        }

        if (sendFailureMessage && caster instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("ui.irons_spellbooks.cast_error_target")
                            .withStyle(ChatFormatting.RED)
            ));
        }
        cir.setReturnValue(false);
    }

    private static void failure(LivingEntity caster, String message, boolean sendFailureMessage) {
        if (sendFailureMessage && caster instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal(message).withStyle(ChatFormatting.RED)
            ));
        }
    }
}
