package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.projectile.PiercingProjectileContext;
import net.fireboy.mageadditions.registry.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    @Unique
    private final Set<Integer> mageadditions$piercedEntityIds = new HashSet<>();

    @Unique
    private boolean mageadditions$processingPiercingHit;

    @Unique
    private boolean mageadditions$hasPiercingOwner() {
        Entity owner = ((Projectile) (Object) this).getOwner();
        return owner instanceof LivingEntity living && living.hasEffect(ModEffects.PIERCING);
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void mageadditions$skipAlreadyPierced(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (mageadditions$hasPiercingOwner() && mageadditions$piercedEntityIds.contains(target.getId())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"))
    private void mageadditions$beginPiercingEntityHit(HitResult hitResult, CallbackInfoReturnable<ProjectileDeflection> cir) {
        if (hitResult instanceof EntityHitResult && mageadditions$hasPiercingOwner()) {
            mageadditions$processingPiercingHit = true;
            PiercingProjectileContext.begin((Projectile) (Object) this);
        }
    }

    @Inject(method = "hitTargetOrDeflectSelf", at = @At("RETURN"))
    private void mageadditions$finishPiercingEntityHit(HitResult hitResult, CallbackInfoReturnable<ProjectileDeflection> cir) {
        if (!mageadditions$processingPiercingHit) {
            return;
        }

        try {
            if (hitResult instanceof EntityHitResult entityHitResult
                && cir.getReturnValue() == ProjectileDeflection.NONE) {
                mageadditions$piercedEntityIds.add(entityHitResult.getEntity().getId());
            }
        } finally {
            mageadditions$processingPiercingHit = false;
            PiercingProjectileContext.end();
        }
    }
}
