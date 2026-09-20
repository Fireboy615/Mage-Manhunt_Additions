package net.fireboy.mageadditions.network;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.compat.irons.IronsSpellConfigBridge;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.config.SpellOverrideConfigService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** Server-authoritative read/write handling for the in-game spell editor. */
public final class SpellConfigServerPayloadHandler {
    private SpellConfigServerPayloadHandler() {}

    public static void handle(SpellConfigPayloads.Request payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        AbstractSpell spell = findSpell(payload.spellId());
        if (spell == null) {
            context.reply(errorSnapshot(payload.spellId(), canEdit(player), "Unknown spell id: " + payload.spellId()));
            return;
        }

        context.reply(snapshot(spell, player, true, "Loaded live server values."));
    }

    public static void handle(SpellConfigPayloads.Update payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        AbstractSpell spell = findSpell(payload.spellId());
        if (spell == null) {
            context.reply(errorSnapshot(payload.spellId(), canEdit(player), "Unknown spell id: " + payload.spellId()));
            return;
        }

        if (!canEdit(player)) {
            context.reply(snapshot(spell, player, false, "You need operator permission to edit server spell balance."));
            return;
        }

        try {
            validate(payload);

            SpellRarity rarity = SpellRarity.valueOf(payload.minRarity().toUpperCase(Locale.ROOT));
            IronsSpellConfigBridge.Settings ironSettings = new IronsSpellConfigBridge.Settings(
                    payload.enabled(),
                    payload.school(),
                    payload.maxLevel(),
                    rarity,
                    payload.manaMultiplier(),
                    payload.powerMultiplier(),
                    payload.cooldownSeconds(),
                    payload.allowCrafting()
            );

            IronsSpellConfigBridge.SaveResult ironResult = IronsSpellConfigBridge.saveLive(spell, ironSettings);
            if (!ironResult.success()) {
                context.reply(snapshot(
                        spell,
                        player,
                        false,
                        "Iron's config update failed: " + ironResult.error()
                ));
                return;
            }

            // The server TOML is now live. Push the exact same Iron's values to
            // every connected client immediately so scroll tooltips, generated
            // scroll variants and client-side spell calculations do not stay stale.
            SpellConfigSyncService.broadcast(spell);

            SpellOverrideConfigService.SpellRules rules = new SpellOverrideConfigService.SpellRules(
                    toRule(payload.castMode(), payload.castValue()),
                    toRule(payload.manaMode(), payload.manaValue()),
                    toRule(payload.cooldownMode(), payload.cooldownValue())
            );

            CastTimeOverrides.ReloadResult mageResult = SpellOverrideConfigService.saveSpellRules(spell.getSpellId(), rules);
            if (!mageResult.success()) {
                context.reply(snapshot(
                        spell,
                        player,
                        false,
                        "Iron's values changed, but Mage Additions override save failed: " + mageResult.error()
                ));
                return;
            }

            context.reply(snapshot(spell, player, true, "Saved and applied live on the server."));
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "Invalid spell settings." : exception.getMessage();
            context.reply(snapshot(spell, player, false, message));
        }
    }

    private static SpellConfigPayloads.Snapshot snapshot(
            AbstractSpell spell,
            ServerPlayer player,
            boolean success,
            String message
    ) {
        IronsSpellConfigBridge.Settings iron = IronsSpellConfigBridge.read(spell);
        SpellOverrideConfigService.SpellRules mage = SpellOverrideConfigService.readSpellRules(spell.getSpellId());
        IronsSpellConfigBridge.BackendInfo backend = IronsSpellConfigBridge.backendInfo();

        return new SpellConfigPayloads.Snapshot(
                spell.getSpellResource(),
                success,
                message == null ? "" : message,
                canEdit(player) && backend.writable(),
                backend.name(),
                iron.enabled(),
                iron.school(),
                iron.maxLevel(),
                iron.minRarity().name(),
                iron.manaMultiplier(),
                iron.powerMultiplier(),
                iron.cooldownSeconds(),
                iron.allowCrafting(),
                modeName(mage.castTime()),
                mage.castTime().value(),
                modeName(mage.mana()),
                mage.mana().value(),
                modeName(mage.cooldown()),
                mage.cooldown().value()
        );
    }

    private static SpellConfigPayloads.Snapshot errorSnapshot(ResourceLocation spellId, boolean canEdit, String message) {
        IronsSpellConfigBridge.BackendInfo backend = IronsSpellConfigBridge.backendInfo();
        return new SpellConfigPayloads.Snapshot(
                spellId,
                false,
                message,
                canEdit && backend.writable(),
                backend.name(),
                true,
                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "evocation"),
                1,
                SpellRarity.COMMON.name(),
                1.0,
                1.0,
                0.0,
                true,
                "off",
                0.0,
                "off",
                0.0,
                "off",
                0.0
        );
    }

    private static AbstractSpell findSpell(ResourceLocation id) {
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return spell == SpellRegistry.none() ? null : spell;
    }

    private static boolean canEdit(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return true;
        }
        return player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    private static void validate(SpellConfigPayloads.Update payload) {
        if (SchoolRegistry.getSchool(payload.school()) == null) {
            throw new IllegalArgumentException("Unknown school: " + payload.school());
        }
        if (payload.maxLevel() < 1 || payload.maxLevel() > 1000) {
            throw new IllegalArgumentException("Max level must be between 1 and 1000.");
        }
        try {
            SpellRarity.valueOf(payload.minRarity().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown minimum rarity: " + payload.minRarity());
        }

        requireFiniteRange(payload.manaMultiplier(), 0.0, 1_000_000.0, "Mana multiplier");
        requireFiniteRange(payload.powerMultiplier(), 0.0, 1_000_000.0, "Power multiplier");
        requireFiniteRange(payload.cooldownSeconds(), 0.0, 3600.0, "Cooldown");

        validateRule(payload.castMode(), payload.castValue(), "Cast override");
        validateRule(payload.manaMode(), payload.manaValue(), "Mana override");
        validateRule(payload.cooldownMode(), payload.cooldownValue(), "Cooldown override");
    }

    private static void validateRule(String mode, double value, String label) {
        String normalized = normalizeMode(mode);
        if (!normalized.equals("off") && !normalized.equals("absolute") && !normalized.equals("multiplier")) {
            throw new IllegalArgumentException(label + " mode must be off, absolute, or multiplier.");
        }
        requireFiniteRange(value, 0.0, 1_000_000.0, label);
    }

    private static void requireFiniteRange(double value, double min, double max, String label) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }

    private static SpellOverrideConfigService.RuleState toRule(String mode, double value) {
        String normalized = normalizeMode(mode);
        if (normalized.equals("off")) {
            return SpellOverrideConfigService.RuleState.disabled();
        }
        return new SpellOverrideConfigService.RuleState(true, normalized, value);
    }

    private static String modeName(SpellOverrideConfigService.RuleState state) {
        if (state == null || !state.enabled()) {
            return "off";
        }
        return "multiplier".equalsIgnoreCase(state.mode()) ? "multiplier" : "absolute";
    }

    private static String normalizeMode(String mode) {
        return mode == null ? "off" : mode.trim().toLowerCase(Locale.ROOT);
    }
}
