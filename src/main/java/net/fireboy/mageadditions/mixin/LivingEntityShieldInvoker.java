package net.fireboy.mageadditions.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses the two protected vanilla shield-reaction methods without widening them. */
@Mixin(LivingEntity.class)
public interface LivingEntityShieldInvoker {
    @Invoker("blockUsingShield")
    void mageadditions$invokeBlockUsingShield(LivingEntity attacker);

    @Invoker("blockedByShield")
    void mageadditions$invokeBlockedByShield(LivingEntity blocker);
}
