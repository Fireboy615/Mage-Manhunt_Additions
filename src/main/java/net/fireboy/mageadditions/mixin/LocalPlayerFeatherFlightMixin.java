package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.rework.FeatherFlightRework;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-side movement feel for the Feather Flight rework. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerFeatherFlightMixin {
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void mageadditions$featherFlightMovement(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!FeatherFlightRework.isActive(player) || player.onGround() || player.isPassenger()) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double x = velocity.x;
        double y = velocity.y;
        double z = velocity.z;

        // Holding jump turns a normal fall into a controlled feather descent.
        // Releasing jump immediately restores normal gravity/fall speed.
        double descentCap = -FeatherFlightRework.slowFallSpeed();
        if (player.input.jumping && y < descentCap) {
            y = descentCap;
            player.resetFallDistance();
        }

        // Stronger horizontal air control without any upward thrust. This uses
        // the player's actual movement input, so simply falling does not create
        // free horizontal acceleration.
        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        float inputLength = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength > 0.001F) {
            forward /= Math.max(1.0F, inputLength);
            strafe /= Math.max(1.0F, inputLength);

            double yaw = Math.toRadians(player.getYRot());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            x += (strafe * cos - forward * sin) * FeatherFlightRework.airAcceleration();
            z += (forward * cos + strafe * sin) * FeatherFlightRework.airAcceleration();

            double horizontal = Math.sqrt(x * x + z * z);
            double maxHorizontalSpeed = FeatherFlightRework.maxHorizontalSpeed();
            if (horizontal > maxHorizontalSpeed) {
                double scale = maxHorizontalSpeed / horizontal;
                x *= scale;
                z *= scale;
            }
        }

        player.setDeltaMovement(x, y, z);
    }
}
