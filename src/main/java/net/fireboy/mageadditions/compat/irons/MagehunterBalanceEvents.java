package net.fireboy.mageadditions.compat.irons;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Small Iron's balance hooks that do not need a compile-time dependency on
 * Iron's implementation classes.
 */
public final class MagehunterBalanceEvents {
    private static final ResourceLocation MAGEHUNTER_ID =
        ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "magehunter");
    private static final int MAGEHUNTER_SHARPNESS_LEVEL = 2;

    private MagehunterBalanceEvents() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Vindicator vindicator)) {
            return;
        }

        ItemStack weapon = vindicator.getMainHandItem();
        if (weapon.isEmpty() || !MAGEHUNTER_ID.equals(BuiltInRegistries.ITEM.getKey(weapon.getItem()))) {
            return;
        }

        Holder<Enchantment> sharpness = event.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SHARPNESS);

        int currentLevel = EnchantmentHelper.getTagEnchantmentLevel(sharpness, weapon);
        if (currentLevel > MAGEHUNTER_SHARPNESS_LEVEL) {
            EnchantmentHelper.updateEnchantments(
                weapon,
                enchantments -> enchantments.set(sharpness, MAGEHUNTER_SHARPNESS_LEVEL)
            );
        }
    }
}
