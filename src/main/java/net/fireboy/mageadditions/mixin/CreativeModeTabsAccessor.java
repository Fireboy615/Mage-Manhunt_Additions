package net.fireboy.mageadditions.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-side accessor used to make creative tab contents rebuild after live spell config changes. */
@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void mageadditions$setCachedParameters(CreativeModeTab.ItemDisplayParameters value) {
        throw new AssertionError();
    }
}
