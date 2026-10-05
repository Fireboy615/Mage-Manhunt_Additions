package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.entity.spells.ice_tomb.IceTombEntity;
import io.redspace.ironsspellbooks.spells.ice.IceTombSpell;
import net.fireboy.mageadditions.rework.IceTombHitState;
import net.fireboy.mageadditions.rework.IceTombRework;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Adds level-scaled hit durability to player-cast Ice Tombs. */
@Mixin(value = IceTombSpell.class, remap = false)
public abstract class IceTombSpellMixin {

    @Inject(method = "onCast", at = @At("TAIL"), remap = false)
    private void mageadditions$assignIceTombHitBudget(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData playerMagicData,
            CallbackInfo ci
    ) {
        if (!IceTombRework.enabled()) {
            return;
        }

        if (caster.getVehicle() instanceof IceTombEntity tomb
                && tomb instanceof IceTombHitState state) {
            state.mageadditions$setHitBudget(IceTombRework.blockedHits(spellLevel));
        }
    }

    @Inject(method = "getUniqueInfo", at = @At("RETURN"), cancellable = true, remap = false)
    private void mageadditions$showBlockedHits(
            int spellLevel,
            LivingEntity caster,
            CallbackInfoReturnable<List<MutableComponent>> cir
    ) {
        if (!IceTombRework.enabled()) {
            return;
        }

        List<MutableComponent> info = new ArrayList<>(cir.getReturnValue());
        info.add(Component.translatable(
                "ui.mageadditions.blocked_hits",
                IceTombRework.blockedHits(spellLevel)
        ));
        cir.setReturnValue(info);
    }
}
