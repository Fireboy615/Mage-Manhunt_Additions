package net.fireboy.mageadditions.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Replaces selected Iron's Spells 'n Spellbooks loot tables with the custom
 * Mage Manhunt versions bundled inside Mage Additions.
 *
 * The replacement JSON lives outside Minecraft's normal loot_table directory,
 * so it never overrides Iron's by resource-pack priority on its own. We only
 * parse and install it here when the Loot Changes config module is enabled.
 * Disabling the module therefore lets Iron's original table load normally.
 */
@EventBusSubscriber(modid = MageAdditions.MODID)
public final class LootTableOverrideEvents {
    private static final String RESOURCE_ROOT = "data/mageadditions/loot_overrides/";

    private static final Set<ResourceLocation> TARGET_TABLES = Set.of(
            id("blocks/wisewood_bookshelf"),
            id("chests/bookshelf_loot"),
            id("chests/generic_magic_treasure"),
            id("chests/magic_bookshelf_loot"),
            id("chests/pyromancer_tower/pyromancer_supplies"),
            id("magic_items/basic_curios"),
            id("magic_items/great_ink")
    );

    private LootTableOverrideEvents() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!CastTimeOverrides.lootChangesEnabled()) {
            return;
        }

        ResourceLocation tableId = event.getName();
        if (!TARGET_TABLES.contains(tableId)) {
            return;
        }

        String resourcePath = RESOURCE_ROOT
                + tableId.getNamespace()
                + "/loot_table/"
                + tableId.getPath()
                + ".json";

        try (InputStream stream = MageAdditions.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                MageAdditions.LOGGER.error(
                        "Loot Changes is enabled, but bundled replacement {} was not found",
                        resourcePath
                );
                return;
            }

            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            convertNormalPotionsToSplash(json);
            LootTable replacement = LootTable.DIRECT_CODEC
                    .parse(event.getRegistries().createSerializationContext(JsonOps.INSTANCE), json)
                    .getOrThrow(message -> new IllegalArgumentException(
                            "Could not decode replacement loot table " + tableId + ": " + message
                    ));

            replacement.setLootTableId(tableId);
            event.setTable(replacement);
            MageAdditions.LOGGER.debug("Applied Mage Additions loot replacement for {}", tableId);
        } catch (Exception exception) {
            // Fail open: keep Iron's original table if a bundled replacement is
            // ever invalid after an Iron's/Minecraft update.
            MageAdditions.LOGGER.error(
                    "Failed to apply Mage Additions loot replacement for {}. Keeping Iron's original table.",
                    tableId,
                    exception
            );
        }
    }

    /**
     * Custom Manhunt loot is combat-focused, so any normal vanilla potion entry
     * authored into one of the replacement tables becomes its splash variant.
     * Loot functions/components remain untouched, meaning the resulting splash
     * bottle keeps the exact potion type selected by the table.
     */
    private static void convertNormalPotionsToSplash(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement name = object.get("name");
            if (name != null
                    && name.isJsonPrimitive()
                    && "minecraft:potion".equals(name.getAsString())) {
                object.addProperty("name", "minecraft:splash_potion");
            }

            for (String key : object.keySet()) {
                convertNormalPotionsToSplash(object.get(key));
            }
            return;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                convertNormalPotionsToSplash(child);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", path);
    }
}
