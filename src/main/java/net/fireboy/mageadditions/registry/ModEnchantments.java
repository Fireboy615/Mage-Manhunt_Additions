package net.fireboy.mageadditions.registry;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Resource keys for Mage Additions' data-driven enchantments.
 *
 * <p>1.21 enchantments live in the dynamic enchantment registry, so the actual
 * definitions are supplied by data/mageadditions/enchantment/*.json. These keys
 * give gameplay code a stable way to query their live levels.</p>
 */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> POISON_ASPECT = key("poison_aspect");
    public static final ResourceKey<Enchantment> ICE_ASPECT = key("ice_aspect");

    private ModEnchantments() {}

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, path)
        );
    }
}
