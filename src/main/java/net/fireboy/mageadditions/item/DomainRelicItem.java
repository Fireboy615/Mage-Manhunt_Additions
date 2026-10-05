package net.fireboy.mageadditions.item;

import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.item.CastingItem;
import io.redspace.ironsspellbooks.item.UniqueItem;
import net.fireboy.mageadditions.registry.ModSpells;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DomainRelicItem extends CastingItem implements IPresetSpellContainer, UniqueItem {
    public DomainRelicItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void initializeSpellContainer(ItemStack stack) {
        if (stack == null || ISpellContainer.isSpellContainer(stack)) {
            return;
        }

        ISpellContainerMutable container =
                ISpellContainer.create(1, true, false).mutableCopy();
        container.addSpell(ModSpells.DOMAIN.get(), 3, true);
        ISpellContainer.set(stack, container.toImmutable());
    }
}
