package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.item.armor.ExtendedArmorItem;
import io.redspace.ironsspellbooks.item.armor.WizardArmorItem;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the standard Iron's Wizard Armour +2 toughness per equipped piece. */
@Mixin(value = ExtendedArmorItem.class, remap = false)
public abstract class ExtendedArmorItemWizardToughnessMixin {

    @Inject(method = "getDefaultAttributeModifiers", at = @At("RETURN"), cancellable = true, remap = false)
    private void mageadditions$wizardArmorToughness(
            CallbackInfoReturnable<ItemAttributeModifiers> cir
    ) {
        if (!CastTimeOverrides.wizardArmorToughnessEnabled()
                || !((Object) this instanceof WizardArmorItem)) {
            return;
        }

        ArmorItem armor = (ArmorItem) (Object) this;
        ArmorItem.Type type = armor.getType();
        ResourceLocation modifierId = ResourceLocation.withDefaultNamespace("armor." + type.getName());

        cir.setReturnValue(cir.getReturnValue().withModifierAdded(
                Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(modifierId, 2.0D, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.bySlot(type.getSlot())
        ));
    }
}
