package net.fireboy.mageadditions.spell;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.registry.ModEffects;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Applies vanilla 1.21.1 mace smash behavior to the player's current main-hand
 * item while Mace Infusion is active.
 */
@EventBusSubscriber(modid = MageAdditions.MODID)
public final class MaceInfusionServerEvents {
    private static final float SMASH_FALL_THRESHOLD = 1.5F;
    private static final float SMASH_HEAVY_THRESHOLD = 5.0F;
    private static final double KNOCKBACK_RADIUS = 3.5D;
    private static final double KNOCKBACK_POWER = 0.7D;

    private MaceInfusionServerEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer attacker = infusedMeleeAttacker(event.getSource());
        if (attacker == null || !canSmashAttack(attacker)) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty() || weapon.getItem() instanceof MaceItem) {
            return;
        }

        float fallDistance = attacker.fallDistance;
        float bonusDamage = vanillaSmashBonus(fallDistance);

        if (attacker.level() instanceof ServerLevel serverLevel) {
            bonusDamage += EnchantmentHelper.modifyFallBasedDamage(
                serverLevel,
                weapon,
                event.getEntity(),
                event.getSource(),
                0.0F
            ) * fallDistance;
        }

        event.setAmount(event.getAmount() + bonusDamage);
    }

    @SubscribeEvent
    public static void onDamageApplied(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        ServerPlayer attacker = infusedMeleeAttacker(event.getSource());
        if (attacker == null || !canSmashAttack(attacker)) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty() || weapon.getItem() instanceof MaceItem) {
            return;
        }

        LivingEntity target = event.getEntity();
        ServerLevel level = attacker.serverLevel();

        if (attacker.isIgnoringFallDamageFromCurrentImpulse() && attacker.currentImpulseImpactPos != null) {
            if (attacker.currentImpulseImpactPos.y > attacker.position().y) {
                attacker.currentImpulseImpactPos = attacker.position();
            }
        } else {
            attacker.currentImpulseImpactPos = attacker.position();
        }

        attacker.setIgnoreFallDamageFromCurrentImpulse(true);
        attacker.setDeltaMovement(attacker.getDeltaMovement().with(Direction.Axis.Y, 0.01F));
        attacker.connection.send(new ClientboundSetEntityMotionPacket(attacker));

        if (target.onGround()) {
            attacker.setSpawnExtraParticlesOnFall(true);
            SoundEvent sound = attacker.fallDistance > SMASH_HEAVY_THRESHOLD
                ? SoundEvents.MACE_SMASH_GROUND_HEAVY
                : SoundEvents.MACE_SMASH_GROUND;
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), sound, attacker.getSoundSource(), 1.0F, 1.0F);
        } else {
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.MACE_SMASH_AIR, attacker.getSoundSource(), 1.0F, 1.0F);
        }

        knockback(level, attacker, target);
        attacker.resetFallDistance();
    }

    private static ServerPlayer infusedMeleeAttacker(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (!(direct instanceof ServerPlayer attacker) || causing != attacker) {
            return null;
        }
        return attacker.hasEffect(ModEffects.MACE_INFUSION) ? attacker : null;
    }

    private static boolean canSmashAttack(LivingEntity attacker) {
        return attacker.fallDistance > SMASH_FALL_THRESHOLD && !attacker.isFallFlying();
    }

    private static float vanillaSmashBonus(float fallDistance) {
        if (fallDistance <= 3.0F) {
            return 4.0F * fallDistance;
        }
        if (fallDistance <= 8.0F) {
            return 12.0F + 2.0F * (fallDistance - 3.0F);
        }
        return 22.0F + (fallDistance - 8.0F);
    }

    private static void knockback(ServerLevel level, ServerPlayer attacker, Entity target) {
        level.levelEvent(2013, target.getOnPos(), 750);
        level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(KNOCKBACK_RADIUS), nearby -> {
            if (nearby.isSpectator() || nearby == attacker || nearby == target || attacker.isAlliedTo(nearby)) {
                return false;
            }
            if (nearby instanceof TamableAnimal tamable
                && tamable.isTame()
                && attacker.getUUID().equals(tamable.getOwnerUUID())) {
                return false;
            }
            if (nearby instanceof ArmorStand armorStand && armorStand.isMarker()) {
                return false;
            }
            return target.distanceToSqr(nearby) <= KNOCKBACK_RADIUS * KNOCKBACK_RADIUS;
        }).forEach(nearby -> {
            Vec3 offset = nearby.position().subtract(target.position());
            double resistance = nearby.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double power = (KNOCKBACK_RADIUS - offset.length())
                * KNOCKBACK_POWER
                * (attacker.fallDistance > SMASH_HEAVY_THRESHOLD ? 2.0D : 1.0D)
                * (1.0D - resistance);

            if (power <= 0.0D || offset.lengthSqr() < 1.0E-7D) {
                return;
            }

            Vec3 push = offset.normalize().scale(power);
            nearby.push(push.x, 0.7F, push.z);
            if (nearby instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        });
    }
}
