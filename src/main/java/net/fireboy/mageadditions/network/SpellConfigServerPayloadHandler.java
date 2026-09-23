package net.fireboy.mageadditions.network;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.compat.irons.IronsSpellConfigBridge;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.config.SpellOverrideConfigService;
import net.fireboy.mageadditions.spell.SpellTargetingDefaults;
import net.fireboy.mageadditions.spell.SpellCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public static void handle(SpellConfigPayloads.StatusRequest payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer)) {
            return;
        }

        Set<String> mageModified = SpellOverrideConfigService.modifiedSpellIds();
        List<ResourceLocation> modified = new ArrayList<>();

        for (AbstractSpell spell : SpellRegistry.REGISTRY.stream().toList()) {
            if (spell == null || spell == SpellRegistry.none()) {
                continue;
            }

            boolean changedInIrons = !sameSettings(
                    IronsSpellConfigBridge.read(spell),
                    IronsSpellConfigBridge.defaults(spell)
            );
            if (changedInIrons || mageModified.contains(spell.getSpellId())) {
                modified.add(spell.getSpellResource());
            }
        }

        context.reply(new SpellConfigPayloads.ModifiedSync(List.copyOf(modified)));
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

            // Keep every connected client aligned with the server's live Iron's values.
            SpellConfigSyncService.broadcast(spell);

            SpellOverrideConfigService.RuleState castRule = toRule(payload.castMode(), payload.castValue());
            SpellOverrideConfigService.BehaviorState behavior = new SpellOverrideConfigService.BehaviorState(
                    payload.mageOverridesEnabled(),
                    payload.movementMode(),
                    payload.movementMultiplier(),
                    payload.maxHeightEnabled() ? payload.maxHeightAboveGround() : null,
                    payload.hasLineOfSightOverride() ? payload.lineOfSightValue() : null,
                    payload.hasMinCastDistance() ? payload.minCastDistance() : null,
                    toRule(payload.rangeMode(), payload.rangeValue()),
                    toRule(payload.projectileSpeedMode(), payload.projectileSpeedValue()),
                    payload.shieldInteraction(),
                    payload.targetingMode()
            ).normalized();
            CastTimeOverrides.ReloadResult mageResult = SpellOverrideConfigService.saveEditorRules(
                    spell.getSpellId(),
                    castRule,
                    behavior
            );
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

        boolean originalLineOfSight = SpellTargetingDefaults.DEFAULT_REQUIRE_LINE_OF_SIGHT;
        double originalMinDistance = SpellTargetingDefaults.DEFAULT_MIN_DISTANCE;
        double originalMaxDistance = SpellTargetingDefaults.originalMaxDistance(spell);
        Boolean losOverride = mage.behavior().lineOfSightOverride();
        Double minOverride = mage.behavior().minCastDistance();
        SpellOverrideConfigService.RuleState rangeRule = mage.behavior().range();
        SpellOverrideConfigService.RuleState projectileSpeedRule = mage.behavior().projectileSpeed();
        SpellCapabilities.Capabilities capabilities = SpellCapabilities.detect(spell);

        boolean permission = canEdit(player);
        boolean editable = permission && backend.writable();
        String resolvedMessage = message == null ? "" : message;
        if (success && !editable) {
            if (!permission) {
                resolvedMessage = "Loaded live server values, but this player does not have edit permission.";
            } else if (!backend.writable()) {
                resolvedMessage = "Loaded live server values, but the detected Iron's config backend is read-only: "
                        + backend.name();
            }
        }

        return new SpellConfigPayloads.Snapshot(
                spell.getSpellResource(),
                success,
                resolvedMessage,
                editable,
                backend.name(),
                iron.enabled(),
                iron.school(),
                iron.maxLevel(),
                iron.minRarity().name(),
                iron.manaMultiplier(),
                iron.powerMultiplier(),
                iron.cooldownSeconds(),
                iron.allowCrafting(),
                mage.behavior().enabled(),
                modeName(mage.castTime()),
                mage.castTime().value(),
                modeName(rangeRule),
                rangeRule.value(),
                originalMaxDistance,
                mage.behavior().movementMode(),
                mage.behavior().movementMultiplier(),
                mage.behavior().maxHeightAboveGround() != null,
                mage.behavior().maxHeightAboveGround() == null ? 10.0 : mage.behavior().maxHeightAboveGround(),
                losOverride != null,
                losOverride == null ? originalLineOfSight : losOverride,
                originalLineOfSight,
                minOverride != null,
                minOverride == null ? originalMinDistance : minOverride,
                originalMinDistance,
                capabilities.projectileSpeed(),
                modeName(projectileSpeedRule),
                projectileSpeedRule.value(),
                capabilities.shieldInteraction(),
                mage.behavior().shieldInteraction(),
                capabilities.targetingMode(),
                mage.behavior().targetingMode()
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
                false,
                "off",
                0.0,
                "off",
                0.0,
                SpellTargetingDefaults.FALLBACK_MAX_DISTANCE,
                "default",
                0.5,
                false,
                10.0,
                false,
                SpellTargetingDefaults.DEFAULT_REQUIRE_LINE_OF_SIGHT,
                SpellTargetingDefaults.DEFAULT_REQUIRE_LINE_OF_SIGHT,
                false,
                SpellTargetingDefaults.DEFAULT_MIN_DISTANCE,
                SpellTargetingDefaults.DEFAULT_MIN_DISTANCE,
                false,
                "off",
                0.0,
                false,
                "vanilla",
                false,
                "vanilla"
        );
    }

    private static boolean sameSettings(
            IronsSpellConfigBridge.Settings left,
            IronsSpellConfigBridge.Settings right
    ) {
        return left.enabled() == right.enabled()
                && left.school().equals(right.school())
                && left.maxLevel() == right.maxLevel()
                && left.minRarity() == right.minRarity()
                && Double.compare(left.manaMultiplier(), right.manaMultiplier()) == 0
                && Double.compare(left.powerMultiplier(), right.powerMultiplier()) == 0
                && Double.compare(left.cooldownSeconds(), right.cooldownSeconds()) == 0
                && left.allowCrafting() == right.allowCrafting();
    }

    private static AbstractSpell findSpell(ResourceLocation id) {
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return spell == SpellRegistry.none() ? null : spell;
    }

    private static boolean canEdit(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }

        var server = player.getServer();
        if (server == null) {
            return false;
        }

        // Vanilla's integrated-server owner check is normally enough, but during
        // a freshly-created world the singleplayer profile can be populated a few
        // ticks later than the first config-screen request. Do not permanently
        // put the local host into read-only mode because of that startup race.
        if (server.isSingleplayerOwner(player.getGameProfile())) {
            return true;
        }

        if (server.isSingleplayer()) {
            var owner = server.getSingleplayerProfile();
            if (owner != null) {
                if (owner.getId() != null && owner.getId().equals(player.getUUID())) {
                    return true;
                }
                if (owner.getName() != null
                        && owner.getName().equalsIgnoreCase(player.getGameProfile().getName())) {
                    return true;
                }
            }

            // Last-resort integrated-server startup fallback. Before the owner
            // profile is available there can only be one local player in the new
            // world, so allowing that sole player is safe. As soon as a LAN guest
            // exists this fallback no longer applies.
            if (server.getPlayerList().getPlayerCount() == 1) {
                return true;
            }
        }

        return false;
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
        validateRule(payload.castMode(), payload.castValue(), "Cast time override");
        validateRule(payload.rangeMode(), payload.rangeValue(), "Range override");
        validateMovementMode(payload.movementMode());
        validateTargetingMode(payload.targetingMode());
        validateRule(payload.projectileSpeedMode(), payload.projectileSpeedValue(), "Projectile speed override");
        validateShieldInteraction(payload.shieldInteraction());
        requireFiniteRange(payload.movementMultiplier(), 0.0, 10.0, "Movement multiplier");
        if (payload.maxHeightEnabled()) {
            requireFiniteRange(payload.maxHeightAboveGround(), 0.0, 1_000_000.0, "Maximum height above ground");
        }
        if (payload.hasMinCastDistance()) {
            requireFiniteRange(payload.minCastDistance(), 0.0, 1_000_000.0, "Minimum cast distance");
        }
    }

    private static void validateRule(String mode, double value, String label) {
        String normalized = normalizeMode(mode);
        if (!normalized.equals("off") && !normalized.equals("absolute") && !normalized.equals("multiplier")) {
            throw new IllegalArgumentException(label + " mode must be off, absolute, or multiplier.");
        }
        requireFiniteRange(value, 0.0, 1_000_000.0, label);
    }

    private static void validateShieldInteraction(String mode) {
        String value = mode == null ? "vanilla" : mode.trim().toLowerCase(Locale.ROOT);
        if (!value.equals("vanilla") && !value.equals("can_disable") && !value.equals("cannot_disable")) {
            throw new IllegalArgumentException("Shield interaction must be vanilla, can_disable, or cannot_disable.");
        }
    }

    private static void validateTargetingMode(String mode) {
        String value = mode == null ? "vanilla" : mode.trim().toLowerCase(Locale.ROOT);
        if (!value.equals("vanilla") && !value.equals("self") && !value.equals("others") && !value.equals("both")) {
            throw new IllegalArgumentException("Targeting mode must be vanilla, self, others, or both.");
        }
    }

    private static void validateMovementMode(String mode) {
        String value = mode == null ? "default" : mode.trim().toLowerCase(Locale.ROOT);
        if (!value.equals("default") && !value.equals("normal") && !value.equals("slowed") && !value.equals("rooted")) {
            throw new IllegalArgumentException("Movement mode must be default, normal, slowed, or rooted.");
        }
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
