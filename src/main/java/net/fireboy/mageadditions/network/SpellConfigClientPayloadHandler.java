package net.fireboy.mageadditions.network;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.client.SpellEditorScreen;
import net.fireboy.mageadditions.client.SpellManagerScreen;
import net.fireboy.mageadditions.compat.irons.IronsSpellConfigBridge;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.mixin.CreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Clientbound handlers for authoritative spell-config state. */
public final class SpellConfigClientPayloadHandler {
    private SpellConfigClientPayloadHandler() {}

    public static void handle(SpellConfigPayloads.Snapshot payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof SpellEditorScreen screen) {
                screen.applyServerSnapshot(payload);
            }
        });
    }

    public static void handle(SpellConfigPayloads.ModifiedSync payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof SpellManagerScreen screen) {
                screen.applyModifiedStatus(payload.spellIds());
            }
        });
    }

    public static void handle(SpellConfigPayloads.RuntimeSync payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> applyRuntimeSync(payload));
    }

    private static void applyRuntimeSync(SpellConfigPayloads.RuntimeSync payload) {
        Map<AbstractSpell, IronsSpellConfigBridge.Settings> updates = new LinkedHashMap<>();
        boolean appliedCustomState = false;

        for (SpellConfigPayloads.RuntimeEntry entry : payload.entries()) {
            AbstractSpell spell = SpellRegistry.getSpell(entry.spellId());
            if (spell == null || spell == SpellRegistry.none()) {
                MageAdditions.LOGGER.debug("Ignoring runtime config for unknown spell {}", entry.spellId());
                continue;
            }

            if (MageAdditions.MODID.equals(entry.spellId().getNamespace())) {
                // Iron's 3.14 has no mutable config entry for addon spells. Their
                // effective Enabled state is synchronized through Mage Additions
                // instead, so isEnabled() changes immediately without reconnecting.
                CastTimeOverrides.setSyncedCustomSpellUsable(entry.spellId(), entry.enabled());
                appliedCustomState = true;
                continue;
            }

            SpellRarity rarity;
            try {
                rarity = SpellRarity.valueOf(entry.minRarity().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                MageAdditions.LOGGER.warn("Ignoring runtime config for {} because rarity '{}' is invalid", entry.spellId(), entry.minRarity());
                continue;
            }

            updates.put(spell, new IronsSpellConfigBridge.Settings(
                    entry.enabled(),
                    entry.school(),
                    entry.maxLevel(),
                    rarity,
                    entry.manaMultiplier(),
                    entry.powerMultiplier(),
                    entry.cooldownSeconds(),
                    entry.allowCrafting()
            ));
        }

        if (!updates.isEmpty()) {
            IronsSpellConfigBridge.SaveResult result = IronsSpellConfigBridge.applyRuntimeBatch(updates);
            if (!result.success()) {
                MageAdditions.LOGGER.warn("Could not apply server spell runtime sync: {}", result.error());
                return;
            }
            // applyRuntimeBatch already rebuilds Iron's registry cache after the
            // synchronized custom states above have been installed.
        } else if (appliedCustomState) {
            IronsSpellConfigBridge.refreshRegistryCache();
        }

        if (updates.isEmpty() && !appliedCustomState) {
            return;
        }

        // Vanilla caches creative-tab ItemStacks. Mark that cache dirty rather
        // than rebuilding it here; an open creative screen rebuilds next tick,
        // and a closed one rebuilds when next opened. Search trees are refreshed
        // by the creative screen as part of the same vanilla/NeoForge path.
        CreativeModeTabsAccessor.mageadditions$setCachedParameters(null);
    }
}
