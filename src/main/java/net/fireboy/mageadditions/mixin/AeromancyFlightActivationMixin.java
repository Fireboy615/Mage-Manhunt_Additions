package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.rework.FeatherFlightRework;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents Aeromancy's Flight effect from enabling ExpandAbility air-swimming. */
@Mixin(targets = "com.snackpirate.aeromancy.AAServerEvents$Game", remap = false)
public abstract class AeromancyFlightActivationMixin {
    @Redirect(
            method = "activateFlight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"
            ),
            remap = false
    )
    private static boolean mageadditions$disableAirSwimming(Player player, Holder<MobEffect> effect) {
        return !FeatherFlightRework.enabled() && player.hasEffect(effect);
    }
}
