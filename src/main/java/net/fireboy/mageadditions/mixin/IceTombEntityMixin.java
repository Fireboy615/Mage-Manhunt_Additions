package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.ice_tomb.IceTombEntity;
import net.fireboy.mageadditions.rework.IceTombHitState;
import net.fireboy.mageadditions.rework.IceTombRework;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the cast tomb's one-health durability with a hit counter.
 * Healing/lifetime/passenger behavior remains entirely Iron's implementation.
 */
@Mixin(value = IceTombEntity.class, remap = false)
public abstract class IceTombEntityMixin implements IceTombHitState {
    @Unique private static final String MAGEADDITIONS_HITS = "MageAdditionsBlockedHits";
    @Unique private static final String MAGEADDITIONS_MAX_HITS = "MageAdditionsMaxBlockedHits";

    @Unique private int mageadditions$hitsRemaining = -1;
    @Unique private int mageadditions$maxHits = -1;

    @Override
    public int mageadditions$getHitsRemaining() {
        return mageadditions$hitsRemaining;
    }

    @Override
    public int mageadditions$getMaxHits() {
        return mageadditions$maxHits;
    }

    @Override
    public void mageadditions$setHitBudget(int hits) {
        int safeHits = Math.max(1, hits);
        mageadditions$hitsRemaining = safeHits;
        mageadditions$maxHits = safeHits;
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, remap = false)
    private void mageadditions$useHitDurability(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!IceTombRework.enabled()
                || mageadditions$hitsRemaining < 0
                || amount <= 0.0F) {
            return;
        }

        IceTombEntity tomb = (IceTombEntity) (Object) this;
        if (tomb.level().isClientSide()) {
            return;
        }

        Entity passenger = tomb.getFirstPassenger();
        Entity attacker = source.getEntity();

        // Preserve Iron's friendly-fire and self/passenger protections.
        if (DamageSources.isFriendlyFireBetween(attacker, passenger)) {
            cir.setReturnValue(false);
            return;
        }
        if (tomb.isInvulnerableTo(source)
                || (attacker != null && tomb.isPassengerOfSameVehicle(attacker))) {
            return;
        }

        mageadditions$hitsRemaining--;

        // The final allowed hit is still blocked; it shatters the tomb instead of
        // passing damage through to the protected rider.
        if (mageadditions$hitsRemaining <= 0) {
            tomb.die(source, amount);
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void mageadditions$saveHitBudget(CompoundTag tag, CallbackInfo ci) {
        if (mageadditions$hitsRemaining >= 0) {
            tag.putInt(MAGEADDITIONS_HITS, mageadditions$hitsRemaining);
            tag.putInt(MAGEADDITIONS_MAX_HITS, mageadditions$maxHits);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void mageadditions$loadHitBudget(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(MAGEADDITIONS_HITS)) {
            mageadditions$hitsRemaining = tag.getInt(MAGEADDITIONS_HITS);
            mageadditions$maxHits = tag.contains(MAGEADDITIONS_MAX_HITS)
                    ? tag.getInt(MAGEADDITIONS_MAX_HITS)
                    : mageadditions$hitsRemaining;
        }
    }
}
