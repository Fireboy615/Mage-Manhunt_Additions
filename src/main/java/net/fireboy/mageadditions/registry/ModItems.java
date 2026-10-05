package net.fireboy.mageadditions.registry;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.item.DomainRelicItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MageAdditions.MODID);

    public static final DeferredItem<DomainRelicItem> DOMAIN_RELIC =
            ITEMS.registerItem(
                    "domain_relic",
                    DomainRelicItem::new,
                    new Item.Properties().stacksTo(1)
            );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DOMAIN_RELIC.get());
        }
    }
}
