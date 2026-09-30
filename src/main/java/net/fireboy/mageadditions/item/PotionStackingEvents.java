package net.fireboy.mageadditions.item;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Makes vanilla potion bottles use the same four-item stack size as Iron's
 * custom elixirs/potions.
 *
 * This is done through NeoForge's default-component event instead of a mixin,
 * which keeps potion NBT/data components, brewing behaviour, and item use
 * logic entirely vanilla.
 */
@EventBusSubscriber(modid = MageAdditions.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PotionStackingEvents {
    public static final int POTION_STACK_SIZE = 4;

    private PotionStackingEvents() {
    }

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.POTION, builder ->
                builder.set(DataComponents.MAX_STACK_SIZE, POTION_STACK_SIZE));
        event.modify(Items.SPLASH_POTION, builder ->
                builder.set(DataComponents.MAX_STACK_SIZE, POTION_STACK_SIZE));
        event.modify(Items.LINGERING_POTION, builder ->
                builder.set(DataComponents.MAX_STACK_SIZE, POTION_STACK_SIZE));
    }
}
