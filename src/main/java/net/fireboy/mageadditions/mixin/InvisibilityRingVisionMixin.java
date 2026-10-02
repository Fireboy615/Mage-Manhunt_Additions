package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class InvisibilityRingVisionMixin {

    @Inject(
            method = "isInvisibleTo",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mageadditions$seeInvisiblePlayersWithRing(
            Player viewer,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Entity target = (Entity) (Object) this;

        if (!(target instanceof Player)) {
            return;
        }

        if (ItemRegistry.INVISIBILITY_RING.get().isEquippedBy(viewer)) {
            cir.setReturnValue(false);
        }
    }
}