package net.fireboy.mageadditions.enchantment;

import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.fireboy.mageadditions.registry.ModEnchantments;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Runtime effects for Mage Additions' elemental Aspect enchantments.
 *
 * <p>The enchantments themselves are normal 1.21 data-driven enchantments. Only
 * their on-hit behavior lives here so future aspects (for example Electric
 * Aspect) can share one small, server-authoritative melee pipeline.</p>
 */
public final class AspectEnchantmentEvents {
    private static final int POISON_TICKS_PER_LEVEL = 80; // 4 seconds.
    private static final int ICE_FREEZE_TICKS_PER_LEVEL = 50;

    private AspectEnchantmentEvents() {}

    public static void onDamageApplied(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || event.getEntity().level().isClientSide()) {
            return;
        }

        LivingEntity attacker = directMeleeAttacker(event.getSource());
        if (attacker == null) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty() || !(attacker.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Registry<Enchantment> enchantments =
                serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        int poisonLevel = enchantments.getHolder(ModEnchantments.POISON_ASPECT)
                .map(weapon::getEnchantmentLevel)
                .orElse(0);
        int iceLevel = enchantments.getHolder(ModEnchantments.ICE_ASPECT)
                .map(weapon::getEnchantmentLevel)
                .orElse(0);

        LivingEntity target = event.getEntity();

        if (poisonLevel > 0 && target.isAlive()) {
            target.addEffect(
                    new MobEffectInstance(
                            MobEffects.POISON,
                            POISON_TICKS_PER_LEVEL * poisonLevel,
                            0,
                            false,
                            true,
                            true
                    ),
                    attacker
            );
        }

        if (iceLevel > 0 && target.isAlive()) {
            int required = target.getTicksRequiredToFreeze();
            int added = ICE_FREEZE_TICKS_PER_LEVEL * iceLevel;
            target.setTicksFrozen(Math.min(required, target.getTicksFrozen() + added));

            int chilledTicks = 40 + (iceLevel - 1) * 20; // 2s at I, 3s at II.
            target.addEffect(
                    new MobEffectInstance(
                            MobEffectRegistry.CHILLED,
                            chilledTicks,
                            0,
                            false,
                            true,
                            true
                    ),
                    attacker
            );
        }
    }

    /**
     * Match Fire Aspect's direct weapon-hit intent: the damaging entity and
     * causing entity must be the same living attacker. Projectiles, thorns and
     * other indirect damage therefore cannot proc an Aspect enchantment.
     */
    private static LivingEntity directMeleeAttacker(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (!(direct instanceof LivingEntity attacker) || causing != attacker) {
            return null;
        }
        return attacker;
    }
}
