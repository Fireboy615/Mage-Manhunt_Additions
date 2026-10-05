package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.effect.EvasionEffect;
import net.fireboy.mageadditions.rework.EvasionRework;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes Iron's Evasion teleport attempts through the Mage Additions rework. */
@Mixin(value = EvasionEffect.class, remap = false)
public abstract class EvasionEffectMixin {

    @Redirect(
            method = "doEffect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"
            ),
            remap = false
    )
    private static boolean mageAdditions$rejectCoveredEvasionDestination(
            LivingEntity entity,
            double x,
            double y,
            double z,
            boolean broadcast
    ) {
        return EvasionRework.safeRandomTeleport(entity, x, y, z, broadcast);
    }
}
