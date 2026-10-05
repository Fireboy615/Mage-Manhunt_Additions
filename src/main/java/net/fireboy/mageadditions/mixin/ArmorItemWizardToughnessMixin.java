package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.item.armor.WizardArmorItem;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps ArmorItem#getToughness consistent with the Wizard Armour attribute override. */
@Mixin(ArmorItem.class)
public abstract class ArmorItemWizardToughnessMixin {

    @Inject(method = "getToughness", at = @At("RETURN"), cancellable = true)
    private void mageadditions$wizardArmorToughnessValue(CallbackInfoReturnable<Float> cir) {
        if (CastTimeOverrides.wizardArmorToughnessEnabled()
                && (Object) this instanceof WizardArmorItem) {
            cir.setReturnValue(2.0F);
        }
    }
}
