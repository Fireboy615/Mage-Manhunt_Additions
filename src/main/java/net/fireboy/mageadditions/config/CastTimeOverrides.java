package net.fireboy.mageadditions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads Mage Additions' modular config and resolves generic per-spell overrides.
 *
 * The live config accepts JSONC-style // and block comments even though the file
 * keeps the .json extension for backwards compatibility with existing installs.
 */
public final class CastTimeOverrides {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("mage_additions.json");
    private static final Path EXAMPLE_PATH = FMLPaths.CONFIGDIR.get().resolve("mage_additions.example.jsonc");

    private static final int MAX_MANA_COST = 1_000_000;
    private static final int MAX_COOLDOWN_TICKS = 72_000; // one hour at 20 TPS

    private static volatile Snapshot snapshot = Snapshot.defaults();

    /**
     * Effective custom-spell state received from the authoritative server.
     * Dedicated clients cannot read the server's mage_additions.json, so this
     * cache overrides the local snapshot while connected. Integrated servers
     * share the same JVM, but receive the same value, so the result is identical.
     */
    private static final Map<String, Boolean> SYNCED_CUSTOM_SPELL_USABLE = new ConcurrentHashMap<>();

    private CastTimeOverrides() {}

    public static ReloadResult reload() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.notExists(CONFIG_PATH)) {
                writeDefaultConfig();
            }

            // Keep a fully documented reference config beside the live config.
            writeExampleConfig();

            String rawJson = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(stripJsonComments(rawJson));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Root config value must be a JSON object");
            }

            JsonObject root = parsed.getAsJsonObject();
            CastTimeConfig config = GSON.fromJson(root, CastTimeConfig.class);
            if (config == null) {
                config = new CastTimeConfig();
            }

            CastTimeConfig.Modules modules = config.modules != null
                    ? config.modules
                    : new CastTimeConfig.Modules();

            // Prefer the new modular sections when present. If they are absent,
            // transparently fall back to the old top-level config layout.
            BalanceSource balance = readBalanceSource(root, config);
            CounterspellConfig counterspell = readCounterspellSource(root, config);
            ArrowVolleyConfig arrowVolley = readArrowVolleySource(root, config);
            FeatherFlightConfig featherFlight = readFeatherFlightSource(root, config);

            CompileResult castTime = modules.balance_tweaks
                    ? compileRules(balance.castTimeRules, "cast-time")
                    : CompileResult.empty();

            CompileResult mana = modules.balance_tweaks
                    ? compileRules(balance.manaRules, "mana-cost")
                    : CompileResult.empty();

            CompileResult cooldown = modules.balance_tweaks
                    ? compileRules(balance.cooldownRules, "cooldown")
                    : CompileResult.empty();

            BehaviorCompileResult behaviors = modules.balance_tweaks
                    ? compileBehaviorRules(balance.behaviorRules)
                    : BehaviorCompileResult.empty();

            // The module switch is a true master switch. Even if the individual
            // Counterspell config says enabled=true, spell_reworks=false restores
            // Iron's original Counterspell behaviour.
            if (modules.spell_reworks) {
                net.fireboy.mageadditions.spell.CounterspellHandler.reload(counterspell);
                net.fireboy.mageadditions.rework.ArrowVolleyRework.reload(arrowVolley);
                net.fireboy.mageadditions.rework.FeatherFlightRework.reload(featherFlight);
            } else {
                net.fireboy.mageadditions.spell.CounterspellHandler.reload(disabledCounterspell());
                net.fireboy.mageadditions.rework.ArrowVolleyRework.reload(disabledArrowVolley());
                net.fireboy.mageadditions.rework.FeatherFlightRework.reload(disabledFeatherFlight());
            }

            Map<String, Boolean> customSpellEnabledStates = readCustomSpellEnabledStates(root);
            int maxTicks = Math.max(0, balance.settings.max_cast_time_ticks);
            snapshot = new Snapshot(
                    Map.copyOf(castTime.rules),
                    Map.copyOf(mana.rules),
                    Map.copyOf(cooldown.rules),
                    Map.copyOf(behaviors.rules),
                    Map.copyOf(customSpellEnabledStates),
                    balance.settings.allow_instant_spell_delays,
                    maxTicks,
                    modules.balance_tweaks,
                    modules.spell_reworks,
                    modules.custom_spells,
                    modules.minigame,
                    modules.loot_changes,
                    modules.experimental
            );

            int skipped = castTime.skipped + mana.skipped + cooldown.skipped + behaviors.skipped;

            MageAdditions.LOGGER.info(
                    "Loaded Mage Additions config from {}: modules [balance={}, reworks={}, customSpells={}, minigame={}, lootChanges={}, experimental={}], rules [{} cast-time, {} mana, {} cooldown] ({} skipped)",
                    CONFIG_PATH,
                    onOff(modules.balance_tweaks),
                    onOff(modules.spell_reworks),
                    onOff(modules.custom_spells),
                    onOff(modules.minigame),
                    onOff(modules.loot_changes),
                    onOff(modules.experimental),
                    castTime.rules.size(),
                    mana.rules.size(),
                    cooldown.rules.size(),
                    skipped
            );

            return new ReloadResult(
                    true,
                    castTime.rules.size(),
                    skipped,
                    null,
                    mana.rules.size(),
                    cooldown.rules.size()
            );
        } catch (Exception exception) {
            MageAdditions.LOGGER.error(
                    "Failed to load {}. Keeping the previous Mage Additions rules.",
                    CONFIG_PATH,
                    exception
            );
            return new ReloadResult(
                    false,
                    snapshot.castTimeRules.size(),
                    0,
                    exception.getMessage(),
                    snapshot.manaCostRules.size(),
                    snapshot.cooldownRules.size()
            );
        }
    }

    /**
     * Resolves final effective cast time after Iron's has done its own cast-speed
     * calculation. Dedicated spell reworks are allowed to provide their own base
     * duration even if the balance_tweaks module is disabled.
     */
    public static int resolve(AbstractSpell spell, int originalEffectiveTicks) {
        Snapshot current = snapshot;

        // Targeted Counterspell uses this to become a genuine LONG cast. If the
        // spell_reworks module is off the handler simply returns the original value.
        int baseEffectiveTicks = net.fireboy.mageadditions.spell.CounterspellHandler
                .getBaseCastTime(spell, originalEffectiveTicks);

        if (!current.balanceTweaksEnabled || !spellOverridesEnabled(current, spell)) {
            return baseEffectiveTicks;
        }

        CompiledRule rule = current.castTimeRules.get(spell.getSpellId());
        if (rule == null) {
            return baseEffectiveTicks;
        }

        double result = switch (rule.mode) {
            case ABSOLUTE -> rule.value;
            case MULTIPLIER -> baseEffectiveTicks * rule.value;
        };

        int resolvedTicks = clampRounded(result, current.maxCastTimeTicks);

        // A per-spell cast-time rule is an explicit opt-in, including for
        // Iron's INSTANT spells. A positive resolved duration is promoted to a
        // LONG active-cast state by MagicDataMixin so Iron's normal casting UI,
        // animation and interruption state can participate without permanently
        // changing the spell's declared CastType. MULTIPLIER still naturally
        // resolves to 0 for native 0-tick spells.
        return resolvedTicks;
    }

    /**
     * True when this spell has an explicit positive cast-time override while its
     * native Iron's cast type is INSTANT. The server uses the resolved duration
     * as the authoritative signal for the active cast; this helper is also a
     * client-side fallback for presentation (for example remote-player
     * animations) when the same config is available locally.
     */
    public static boolean usesDelayedInstantPresentation(AbstractSpell spell) {
        Snapshot current = snapshot;
        if (spell == null || spell.getCastType() != CastType.INSTANT
                || !current.balanceTweaksEnabled || !spellOverridesEnabled(current, spell)) {
            return false;
        }

        CompiledRule rule = current.castTimeRules.get(spell.getSpellId());
        if (rule == null) {
            return false;
        }

        // Native INSTANT spells have a zero base duration. Therefore only an
        // ABSOLUTE rule can intentionally create a delayed cast.
        return rule.mode == Mode.ABSOLUTE
                && clampRounded(rule.value, current.maxCastTimeTicks) > 0;
    }

    /**
     * ABSOLUTE = fixed mana points. MULTIPLIER = Iron's normal mana cost x value.
     */
    public static int resolveManaCost(AbstractSpell spell, int originalManaCost) {
        Snapshot current = snapshot;
        if (!current.balanceTweaksEnabled || !spellOverridesEnabled(current, spell)) {
            return originalManaCost;
        }

        CompiledRule rule = current.manaCostRules.get(spell.getSpellId());
        if (rule == null) {
            return originalManaCost;
        }

        double result = switch (rule.mode) {
            case ABSOLUTE -> rule.value;
            case MULTIPLIER -> originalManaCost * rule.value;
        };

        return clampRounded(result, MAX_MANA_COST);
    }

    /**
     * ABSOLUTE config values are seconds. MULTIPLIER scales Iron's normal base
     * cooldown. Iron's own cooldown-reduction attributes are applied afterward.
     */
    public static int resolveBaseCooldownTicks(AbstractSpell spell, int originalCooldownTicks) {
        Snapshot current = snapshot;
        if (!current.balanceTweaksEnabled || !spellOverridesEnabled(current, spell)) {
            return originalCooldownTicks;
        }

        CompiledRule rule = current.cooldownRules.get(spell.getSpellId());
        if (rule == null) {
            return originalCooldownTicks;
        }

        double resultTicks = switch (rule.mode) {
            case ABSOLUTE -> rule.value * 20.0;
            case MULTIPLIER -> originalCooldownTicks * rule.value;
        };

        return clampRounded(resultTicks, MAX_COOLDOWN_TICKS);
    }

    /** Returns the active Mage Additions behaviour overrides for a spell. */
    public static BehaviorSettings behavior(AbstractSpell spell) {
        Snapshot current = snapshot;
        if (!current.balanceTweaksEnabled || spell == null) {
            return BehaviorSettings.disabled();
        }
        BehaviorSettings settings = current.behaviorRules.get(spell.getSpellId());
        return settings != null && settings.enabled() ? settings : BehaviorSettings.disabled();
    }

    private static boolean spellOverridesEnabled(Snapshot current, AbstractSpell spell) {
        if (spell == null) return false;
        BehaviorSettings settings = current.behaviorRules.get(spell.getSpellId());
        return settings != null && settings.enabled();
    }

    /**
     * Applies Mage Additions' generic target-range override. The original value
     * is the range Iron's supplied to preCastTargetHelper for this spell.
     */
    /** Applies the generic projectile-speed override to a native velocity magnitude. */
    public static double resolveProjectileSpeed(AbstractSpell spell, double originalSpeed) {
        NumericOverride override = behavior(spell).projectileSpeedOverride();
        if (!override.enabled() || !Double.isFinite(originalSpeed) || originalSpeed < 0.0) {
            return originalSpeed;
        }
        double resolved = override.mode() == NumericMode.MULTIPLIER
                ? originalSpeed * override.value()
                : override.value();
        return Math.max(0.0, Math.min(1_000_000.0, resolved));
    }

    public static double resolveHitboxSize(AbstractSpell spell, double originalSize) {
        return resolveBehaviorNumber(behavior(spell).hitboxSizeOverride(), originalSize, 0.0, 1_000_000.0, false);
    }

    public static double resolveKnockback(AbstractSpell spell, double originalStrength) {
        return resolveBehaviorNumber(behavior(spell).knockbackOverride(), originalStrength, 0.0, 1_000_000.0, false);
    }

    public static double resolveAreaOfEffect(AbstractSpell spell, double originalRadius) {
        return resolveBehaviorNumber(behavior(spell).areaOfEffectOverride(), originalRadius, 0.0, 1_000_000.0, false);
    }

    public static int resolveEffectDurationTicks(AbstractSpell spell, int originalTicks) {
        return resolveDurationTicks(behavior(spell).effectDurationOverride(), originalTicks);
    }

    /** Applies the generic lifetime override to entity-backed lingering spell areas. */
    public static int resolveLingerDurationTicks(AbstractSpell spell, int originalTicks) {
        return resolveDurationTicks(behavior(spell).lingerDurationOverride(), originalTicks);
    }

    /** Applies the dedicated duration override for CONTINUOUS spells. */
    public static int resolveCastDurationTicks(AbstractSpell spell, int originalTicks) {
        NumericOverride override = behavior(spell).castDurationOverride();
        if (!override.enabled() || originalTicks < 0) return originalTicks;
        double resolved = override.mode() == NumericMode.MULTIPLIER
                ? originalTicks * override.value()
                : override.value() * 20.0;
        int max = Math.max(1, snapshot.maxCastTimeTicks);
        return clampRounded(resolved, max);
    }

    private static int resolveDurationTicks(NumericOverride override, int originalTicks) {
        if (!override.enabled() || originalTicks < 0) return originalTicks;
        double resolved = override.mode() == NumericMode.MULTIPLIER
                ? originalTicks * override.value()
                : override.value() * 20.0;
        return clampRounded(resolved, 72_000 * 20);
    }

    private static double resolveBehaviorNumber(NumericOverride override, double original, double min, double max, boolean absoluteSeconds) {
        if (!override.enabled() || !Double.isFinite(original)) return original;
        double absolute = absoluteSeconds ? override.value() * 20.0 : override.value();
        double resolved = override.mode() == NumericMode.MULTIPLIER ? original * override.value() : absolute;
        if (!Double.isFinite(resolved)) return original;
        return Math.max(min, Math.min(max, resolved));
    }

    public static double resolveTargetRange(AbstractSpell spell, double originalRange) {
        if (spell == null || !Double.isFinite(originalRange) || originalRange < 0.0) {
            return Double.isFinite(originalRange) ? Math.max(0.0, originalRange) : 0.0;
        }

        NumericOverride override = behavior(spell).rangeOverride();
        if (!override.enabled()) {
            return originalRange;
        }

        double resolved = override.mode() == NumericMode.MULTIPLIER
                ? originalRange * override.value()
                : override.value();
        if (!Double.isFinite(resolved)) {
            return originalRange;
        }
        return Math.max(0.0, Math.min(1_000_000.0, resolved));
    }

    /**
     * Current master-module states used by the in-game config screen.
     */
    public static ModuleStates moduleStates() {
        Snapshot current = snapshot;
        return new ModuleStates(
                current.balanceTweaksEnabled,
                current.spellReworksEnabled,
                current.customSpellsEnabled,
                current.minigameEnabled,
                current.lootChangesEnabled,
                current.experimentalEnabled
        );
    }

    /**
     * Persists only the top-level module switches, preserving the rest of the
     * user's JSON/JSONC file (including comments outside the modules object),
     * then reloads Mage Additions so the changes take effect immediately.
     */
    public static synchronized ReloadResult saveModuleStates(ModuleStates states) {
        if (states == null) {
            return new ReloadResult(
                    false,
                    snapshot.castTimeRules.size(),
                    0,
                    "Module states cannot be null",
                    snapshot.manaCostRules.size(),
                    snapshot.cooldownRules.size()
            );
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.notExists(CONFIG_PATH)) {
                writeDefaultConfig();
            }

            String raw = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            String updated = replaceOrInsertModulesObject(raw, states);
            Files.writeString(CONFIG_PATH, updated, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MageAdditions.LOGGER.error("Failed to save module switches to {}", CONFIG_PATH, exception);
            return new ReloadResult(
                    false,
                    snapshot.castTimeRules.size(),
                    0,
                    exception.getMessage(),
                    snapshot.manaCostRules.size(),
                    snapshot.cooldownRules.size()
            );
        }

        return reload();
    }

    public static boolean balanceTweaksEnabled() {
        return snapshot.balanceTweaksEnabled;
    }

    public static boolean spellReworksEnabled() {
        return snapshot.spellReworksEnabled;
    }

    public static boolean customSpellsEnabled() {
        return snapshot.customSpellsEnabled;
    }

    public static boolean minigameEnabled() {
        return snapshot.minigameEnabled;
    }

    /** Per-spell setting only; does not include the custom-spells module master switch. */
    public static boolean customSpellSettingEnabled(AbstractSpell spell) {
        if (spell == null || !MageAdditions.MODID.equals(spell.getSpellResource().getNamespace())) {
            return true;
        }
        return snapshot.customSpellEnabledStates.getOrDefault(spell.getSpellId(), true);
    }

    /**
     * Updates the effective state received from the connected server. This is
     * deliberately separate from the on-disk snapshot because a dedicated
     * client's local config is not authoritative for server spell availability.
     */
    public static void setSyncedCustomSpellUsable(ResourceLocation spellId, boolean usable) {
        if (spellId == null || !MageAdditions.MODID.equals(spellId.getNamespace())) {
            return;
        }
        SYNCED_CUSTOM_SPELL_USABLE.put(spellId.toString(), usable);
    }

    public static void clearSyncedCustomSpellUsable() {
        SYNCED_CUSTOM_SPELL_USABLE.clear();
    }

    /** Authoritative runtime gate for Mage Additions custom spells; non-custom spells always pass. */
    public static boolean customSpellUsable(AbstractSpell spell) {
        if (spell == null || !MageAdditions.MODID.equals(spell.getSpellResource().getNamespace())) {
            return true;
        }

        Boolean synced = SYNCED_CUSTOM_SPELL_USABLE.get(spell.getSpellId());
        if (synced != null) {
            return synced;
        }
        return customSpellsEnabled() && customSpellSettingEnabled(spell);
    }

    public static boolean lootChangesEnabled() {
        return snapshot.lootChangesEnabled;
    }

    public static boolean experimentalEnabled() {
        return snapshot.experimentalEnabled;
    }

    public static String moduleSummary() {
        Snapshot current = snapshot;
        return "balance=" + onOff(current.balanceTweaksEnabled)
                + ", reworks=" + onOff(current.spellReworksEnabled)
                + ", custom_spells=" + onOff(current.customSpellsEnabled)
                + ", minigame=" + onOff(current.minigameEnabled)
                + ", loot_changes=" + onOff(current.lootChangesEnabled)
                + ", experimental=" + onOff(current.experimentalEnabled);
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static Path examplePath() {
        return EXAMPLE_PATH;
    }

    private static Map<String, Boolean> readCustomSpellEnabledStates(JsonObject root) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        JsonElement section = root.get("custom_spells");
        if (section == null || !section.isJsonObject()) {
            return result;
        }

        for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject().entrySet()) {
            if (ResourceLocation.tryParse(entry.getKey()) == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonElement enabled = entry.getValue().getAsJsonObject().get("enabled");
            if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
                result.put(entry.getKey(), enabled.getAsBoolean());
            }
        }
        return result;
    }

    private static BalanceSource readBalanceSource(JsonObject root, CastTimeConfig config) {
        if (root.has("balance_tweaks")) {
            CastTimeConfig.BalanceTweaks balance = config.balance_tweaks != null
                    ? config.balance_tweaks
                    : new CastTimeConfig.BalanceTweaks();

            CastTimeConfig.Settings settings = balance.settings != null
                    ? balance.settings
                    : new CastTimeConfig.Settings();

            return new BalanceSource(
                    settings,
                    nonNullMap(balance.cast_time_overrides),
                    nonNullMap(balance.mana_cost_overrides),
                    nonNullMap(balance.cooldown_overrides),
                    nonNullBehaviorMap(balance.spell_behavior_overrides)
            );
        }

        // Legacy Mage Additions layout.
        CastTimeConfig.Settings settings = config.settings != null
                ? config.settings
                : new CastTimeConfig.Settings();

        return new BalanceSource(
                settings,
                nonNullMap(config.cast_time_overrides),
                nonNullMap(config.mana_cost_overrides),
                nonNullMap(config.cooldown_overrides),
                Map.of()
        );
    }

    private static CounterspellConfig readCounterspellSource(JsonObject root, CastTimeConfig config) {
        if (root.has("spell_reworks")) {
            CastTimeConfig.SpellReworks reworks = config.spell_reworks != null
                    ? config.spell_reworks
                    : new CastTimeConfig.SpellReworks();
            return reworks.counterspell != null
                    ? reworks.counterspell
                    : new CounterspellConfig();
        }

        // Legacy Mage Additions layout.
        return config.counterspell != null
                ? config.counterspell
                : new CounterspellConfig();
    }

    private static CounterspellConfig disabledCounterspell() {
        CounterspellConfig disabled = new CounterspellConfig();
        disabled.enabled = false;
        return disabled;
    }

    private static ArrowVolleyConfig readArrowVolleySource(JsonObject root, CastTimeConfig config) {
        if (root.has("spell_reworks")) {
            CastTimeConfig.SpellReworks reworks = config.spell_reworks != null
                    ? config.spell_reworks
                    : new CastTimeConfig.SpellReworks();
            return reworks.arrow_volley != null
                    ? reworks.arrow_volley
                    : new ArrowVolleyConfig();
        }

        return new ArrowVolleyConfig();
    }

    private static ArrowVolleyConfig disabledArrowVolley() {
        ArrowVolleyConfig disabled = new ArrowVolleyConfig();
        disabled.enabled = false;
        return disabled;
    }

    private static FeatherFlightConfig readFeatherFlightSource(JsonObject root, CastTimeConfig config) {
        if (root.has("spell_reworks")) {
            CastTimeConfig.SpellReworks reworks = config.spell_reworks != null
                    ? config.spell_reworks
                    : new CastTimeConfig.SpellReworks();
            return reworks.feather_flight != null
                    ? reworks.feather_flight
                    : new FeatherFlightConfig();
        }

        return new FeatherFlightConfig();
    }

    private static FeatherFlightConfig disabledFeatherFlight() {
        FeatherFlightConfig disabled = new FeatherFlightConfig();
        disabled.enabled = false;
        return disabled;
    }

    private static Map<String, CastTimeConfig.Rule> nonNullMap(Map<String, CastTimeConfig.Rule> source) {
        return source != null ? source : new LinkedHashMap<>();
    }

    private static Map<String, CastTimeConfig.SpellBehavior> nonNullBehaviorMap(
            Map<String, CastTimeConfig.SpellBehavior> source
    ) {
        return source != null ? source : new LinkedHashMap<>();
    }

    private static CompileResult compileRules(
            Map<String, CastTimeConfig.Rule> source,
            String ruleType
    ) {
        Map<String, CompiledRule> compiled = new LinkedHashMap<>();
        int skipped = 0;

        if (source == null) {
            return new CompileResult(compiled, 0);
        }

        for (Map.Entry<String, CastTimeConfig.Rule> entry : source.entrySet()) {
            String spellId = entry.getKey();
            CastTimeConfig.Rule rawRule = entry.getValue();

            if (ResourceLocation.tryParse(spellId) == null) {
                MageAdditions.LOGGER.warn(
                        "Ignoring invalid spell id '{}' in {} {} rule",
                        spellId,
                        ruleType,
                        CONFIG_PATH
                );
                skipped++;
                continue;
            }

            if (rawRule == null || !rawRule.enabled) {
                continue;
            }

            Mode mode = Mode.parse(rawRule.mode);
            if (mode == null) {
                MageAdditions.LOGGER.warn(
                        "Ignoring {} rule for '{}': unknown mode '{}'",
                        ruleType,
                        spellId,
                        rawRule.mode
                );
                skipped++;
                continue;
            }

            if (!Double.isFinite(rawRule.value) || rawRule.value < 0.0) {
                MageAdditions.LOGGER.warn(
                        "Ignoring {} rule for '{}': value must be finite and >= 0",
                        ruleType,
                        spellId
                );
                skipped++;
                continue;
            }

            compiled.put(spellId, new CompiledRule(mode, rawRule.value));
        }

        return new CompileResult(compiled, skipped);
    }

    private static BehaviorCompileResult compileBehaviorRules(
            Map<String, CastTimeConfig.SpellBehavior> source
    ) {
        Map<String, BehaviorSettings> compiled = new LinkedHashMap<>();
        int skipped = 0;
        if (source == null) return BehaviorCompileResult.empty();

        for (Map.Entry<String, CastTimeConfig.SpellBehavior> entry : source.entrySet()) {
            if (ResourceLocation.tryParse(entry.getKey()) == null || entry.getValue() == null) {
                skipped++;
                continue;
            }
            CastTimeConfig.SpellBehavior raw = entry.getValue();
            MovementMode movement = MovementMode.parse(raw.movement);
            Double maxHeight = migratedMaxHeight(raw);
            Boolean requireLineOfSight = migratedLineOfSight(raw);
            NumericOverride rangeOverride = compileBehaviorNumericOverride(raw.range);
            NumericOverride projectileSpeedOverride = compileBehaviorNumericOverride(raw.projectile_speed);
            NumericOverride hitboxSizeOverride = compileBehaviorNumericOverride(raw.hitbox_size);
            NumericOverride knockbackOverride = compileBehaviorNumericOverride(raw.knockback);
            NumericOverride areaOfEffectOverride = compileBehaviorNumericOverride(raw.area_of_effect);
            NumericOverride effectDurationOverride = compileBehaviorNumericOverride(raw.effect_duration);
            CloudMode cloudMode = raw.cloud_on_impact == null
                    ? CloudMode.NATIVE
                    : (raw.cloud_on_impact ? CloudMode.ON : CloudMode.OFF);
            NumericOverride lingerDurationOverride = compileBehaviorNumericOverride(raw.linger_duration);
            boolean followCursor = Boolean.TRUE.equals(raw.follow_cursor);
            int bounceCount = raw.bounces == null ? 0 : raw.bounces;
            NumericOverride castDurationOverride = compileBehaviorNumericOverride(raw.cast_duration);
            ShieldInteraction shieldInteraction = ShieldInteraction.parse(raw.shield_interaction);
            TargetingMode targetingMode = TargetingMode.parse(raw.targeting_mode);
            if (movement == null
                    || !Double.isFinite(raw.movement_multiplier)
                    || raw.movement_multiplier < 0.0 || raw.movement_multiplier > 10.0
                    || !validOptionalDistance(maxHeight)
                    || !validOptionalDistance(raw.min_cast_distance)
                    || rangeOverride == null
                    || projectileSpeedOverride == null
                    || hitboxSizeOverride == null
                    || knockbackOverride == null
                    || areaOfEffectOverride == null
                    || effectDurationOverride == null
                    || lingerDurationOverride == null
                    || bounceCount < 0 || bounceCount > 1000
                    || castDurationOverride == null
                    || shieldInteraction == null
                    || targetingMode == null) {
                MageAdditions.LOGGER.warn("Ignoring invalid spell behaviour rule for '{}'", entry.getKey());
                skipped++;
                continue;
            }
            boolean overridesEnabled = raw.enabled == null || raw.enabled;
            compiled.put(entry.getKey(), new BehaviorSettings(
                    overridesEnabled,
                    movement,
                    raw.movement_multiplier,
                    maxHeight,
                    requireLineOfSight,
                    raw.min_cast_distance,
                    rangeOverride,
                    projectileSpeedOverride,
                    hitboxSizeOverride,
                    knockbackOverride,
                    areaOfEffectOverride,
                    effectDurationOverride,
                    cloudMode,
                    lingerDurationOverride,
                    followCursor,
                    bounceCount,
                    castDurationOverride,
                    shieldInteraction,
                    targetingMode
            ));
        }
        return new BehaviorCompileResult(compiled, skipped);
    }

    private static NumericOverride compileBehaviorNumericOverride(CastTimeConfig.Rule rule) {
        if (rule == null || !rule.enabled) {
            return NumericOverride.disabled();
        }
        NumericMode mode = NumericMode.parse(rule.mode);
        if (mode == null || !Double.isFinite(rule.value) || rule.value < 0.0 || rule.value > 1_000_000.0) {
            return null;
        }
        return new NumericOverride(true, mode, rule.value);
    }

    private static boolean validOptionalDistance(Double value) {
        return value == null || (Double.isFinite(value) && value >= 0.0 && value <= 1_000_000.0);
    }

    private static Double migratedMaxHeight(CastTimeConfig.SpellBehavior raw) {
        if (Boolean.FALSE.equals(raw.max_height_above_ground_enabled)) {
            return null;
        }
        if (raw.max_height_above_ground != null) {
            return raw.max_height_above_ground;
        }
        if ("blocked".equalsIgnoreCase(raw.airborne)) {
            return 0.0;
        }
        if ("allowed".equalsIgnoreCase(raw.airborne)) {
            return 1_000_000.0;
        }
        // No explicit height setting means disabled. This keeps ordinary jumping
        // and airborne spells untouched until the user opts into the restriction.
        return Boolean.TRUE.equals(raw.max_height_above_ground_enabled) ? 10.0 : null;
    }

    private static Boolean migratedLineOfSight(CastTimeConfig.SpellBehavior raw) {
        if (raw.require_line_of_sight != null) {
            return raw.require_line_of_sight;
        }
        return "required".equalsIgnoreCase(raw.line_of_sight) ? Boolean.TRUE : null;
    }

    /**
     * Replaces the value of a top-level "modules" object without serialising
     * the whole file. This means using the GUI does not erase comments from the
     * rest of mage_additions.json. Legacy configs without a modules object get
     * one inserted at the start of the root object.
     */
    private static String replaceOrInsertModulesObject(String raw, ModuleStates states) {
        int[] bounds = findTopLevelObjectProperty(raw, "modules");
        String objectText = moduleObjectText(states);

        if (bounds != null) {
            return raw.substring(0, bounds[0]) + objectText + raw.substring(bounds[1]);
        }

        int rootOpen = findRootObjectOpen(raw);
        if (rootOpen < 0) {
            throw new IllegalArgumentException("Root config value must be a JSON object");
        }

        String insertion = "\n  \"modules\": " + objectText + ",";
        return raw.substring(0, rootOpen + 1) + insertion + raw.substring(rootOpen + 1);
    }

    private static String moduleObjectText(ModuleStates states) {
        return "{\n"
                + "    \"balance_tweaks\": " + states.balanceTweaks() + ",\n"
                + "    \"spell_reworks\": " + states.spellReworks() + ",\n"
                + "    \"custom_spells\": " + states.customSpells() + ",\n"
                + "    \"minigame\": " + states.minigame() + ",\n"
                + "    \"loot_changes\": " + states.lootChanges() + ",\n"
                + "    \"experimental\": " + states.experimental() + "\n"
                + "  }";
    }

    /** Returns {objectStart, objectEndExclusive}, or null if absent. */
    private static int[] findTopLevelObjectProperty(String input, String propertyName) {
        int depth = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';

            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }

            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }

            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                continue;
            }

            if (c != '"' || depth != 1) {
                continue;
            }

            int stringEnd = findStringEnd(input, i);
            if (stringEnd < 0) {
                throw new IllegalArgumentException("Unterminated string in config");
            }

            String token = input.substring(i + 1, stringEnd);
            if (!token.equals(propertyName)) {
                i = stringEnd;
                continue;
            }

            int cursor = skipWhitespaceAndComments(input, stringEnd + 1);
            if (cursor >= input.length() || input.charAt(cursor) != ':') {
                i = stringEnd;
                continue;
            }

            cursor = skipWhitespaceAndComments(input, cursor + 1);
            if (cursor >= input.length() || input.charAt(cursor) != '{') {
                throw new IllegalArgumentException('"' + propertyName + "\" must be an object");
            }

            int objectEnd = findMatchingObjectEnd(input, cursor);
            return new int[] {cursor, objectEnd + 1};
        }

        return null;
    }

    private static int findRootObjectOpen(String input) {
        int cursor = skipWhitespaceAndComments(input, 0);
        return cursor < input.length() && input.charAt(cursor) == '{' ? cursor : -1;
    }

    private static int findMatchingObjectEnd(String input, int objectStart) {
        int depth = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = objectStart; i < input.length(); i++) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';

            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                int stringEnd = findStringEnd(input, i);
                if (stringEnd < 0) {
                    throw new IllegalArgumentException("Unterminated string in config");
                }
                i = stringEnd;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        throw new IllegalArgumentException("Unterminated modules object in config");
    }

    private static int findStringEnd(String input, int quoteStart) {
        boolean escaped = false;
        for (int i = quoteStart + 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespaceAndComments(String input, int start) {
        int i = start;
        while (i < input.length()) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                i += 2;
                while (i < input.length() && input.charAt(i) != '\n' && input.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                int end = input.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("Unterminated block comment in config");
                }
                i = end + 2;
                continue;
            }
            break;
        }
        return i;
    }

    private static int clampRounded(double value, int max) {
        if (!Double.isFinite(value) || value <= 0.0 || max <= 0) {
            return 0;
        }

        long rounded = Math.round(value);
        if (rounded <= 0L) {
            return 0;
        }
        return (int) Math.min(rounded, max);
    }

    /** Removes // and block comments while preserving comment-like text in strings. */
    private static String stripJsonComments(String input) {
        StringBuilder output = new StringBuilder(input.length());
        boolean inString = false;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';

            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                    output.append(c);
                }
                continue;
            }

            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                } else if (c == '\n' || c == '\r') {
                    output.append(c);
                }
                continue;
            }

            if (inString) {
                output.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                output.append(c);
            } else if (c == '/' && next == '/') {
                lineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                blockComment = true;
                i++;
            } else {
                output.append(c);
            }
        }

        if (blockComment) {
            throw new IllegalArgumentException("Unterminated block comment in config");
        }

        return output.toString();
    }

    private static void writeDefaultConfig() throws IOException {
        Files.writeString(CONFIG_PATH, referenceConfig(), StandardCharsets.UTF_8);
    }

    private static void writeExampleConfig() throws IOException {
        Files.writeString(EXAMPLE_PATH, referenceConfig(), StandardCharsets.UTF_8);
    }

    private static String referenceConfig() {
        return """
            {
              // ================================================================
              // MAGE ADDITIONS
              // ================================================================
              // This file accepts // comments and /* block comments */.
              //
              // MASTER MODULE SWITCHES
              // Turning a module off disables every feature inside that module,
              // without requiring you to delete its individual settings.
              "modules": {
                // Per-spell balance values and extra casting/targeting behaviour.
                "balance_tweaks": true,

                // Rewrites of existing Iron's spells, such as Counterspell.
                "spell_reworks": true,

                // Spells added by Mage Additions itself.
                "custom_spells": true,

                // Team selection, match setup, protection, borders and all other
                // native Mage Additions minigame behaviour.
                "minigame": true,

                // Custom Iron's loot tables supplied by Mage Additions.
                // Turning this off restores Iron's normal loot tables after
                // the next resource reload (the in-game toggle reloads them).
                "loot_changes": true,

                // Reserved for unfinished or risky opt-in mechanics.
                // No experimental features are currently registered.
                "experimental": false
              },

              // ================================================================
              // BALANCE TWEAKS MODULE
              // ================================================================
              "balance_tweaks": {
                "settings": {
                  // Legacy compatibility field. Explicit per-spell Absolute
                  // cast-time overrides now act as the opt-in for delaying
                  // INSTANT spells, so this value no longer gates them.
                  "allow_instant_spell_delays": false,

                  // Safety cap. Cast time uses ticks: 20 ticks = 1 second.
                  "max_cast_time_ticks": 72000
                },

                // Spell keys are registry IDs. Iron's addon namespaces work too.
                //
                // CAST TIME
                // absolute   = final effective duration in ticks.
                // multiplier = Iron's effective cast time x value.
                "cast_time_overrides": {
                  // "irons_spellbooks:fireball": {
                  //   "enabled": true,
                  //   "mode": "absolute",
                  //   "value": 40
                  // }
                },

                // MANA COST
                // absolute   = fixed mana points at every spell level.
                // multiplier = Iron's normal level-scaled mana cost x value.
                //
                // Limitation: an addon spell that completely overrides its own
                // getManaCost() can bypass this generic hook and may need a
                // dedicated compatibility patch.
                "mana_cost_overrides": {
                  // "irons_spellbooks:fireball": {
                  //   "enabled": true,
                  //   "mode": "absolute",
                  //   "value": 25
                  // }
                },

                // COOLDOWN
                // absolute   = base cooldown in SECONDS.
                // multiplier = Iron's normal base cooldown x value.
                // Iron's normal cooldown-reduction attributes apply afterward.
                //
                // Limitation: an addon spell that completely overrides its own
                // getSpellCooldown() can bypass this generic hook.
                "cooldown_overrides": {
                  // "irons_spellbooks:fireball": {
                  //   "enabled": true,
                  //   "mode": "absolute",
                  //   "value": 5.0
                  // }
                },

                // EXTRA SPELL BEHAVIOUR
                // These values are Mage Additions-only and default to leaving
                // Iron's/addon behaviour untouched.
                "spell_behavior_overrides": {
                  // "irons_spellbooks:root": {
                  //   "enabled": true,                     // per-spell Mage Additions master switch
                  //   "movement": "slowed",               // default, normal, slowed, rooted
                  //   "movement_multiplier": 0.5,         // used by slowed
                  //   "max_height_above_ground_enabled": true,
                  //   "max_height_above_ground": 10.0,    // blocks; disabled by default
                  //   "range": {                          // generic target-helper range
                  //     "enabled": true,
                  //     "mode": "multiplier",            // absolute=blocks, multiplier=native range x value
                  //     "value": 1.5
                  //   },
                  //   "require_line_of_sight": true,      // true or false; omit to inherit
                  //   "min_cast_distance": 3.0,           // omit to inherit native minimum
                  //   "projectile_speed": { "enabled": true, "mode": "multiplier", "value": 1.5 },
                  //   "hitbox_size": { "enabled": true, "mode": "multiplier", "value": 1.25 },
                  //   "knockback": { "enabled": true, "mode": "multiplier", "value": 1.5 },
                  //   "area_of_effect": { "enabled": true, "mode": "multiplier", "value": 1.25 },
                  //   "effect_duration": { "enabled": true, "mode": "multiplier", "value": 1.5 },
                  //   "cloud_on_impact": true,             // null/native = untouched; true = force/allow; false = suppress detected native cloud
                  //   "linger_duration": { "enabled": true, "mode": "absolute", "value": 6.0 }, // seconds
                  //   "follow_cursor": true,               // compatible continuous ground-target spells
                  //   "bounces": 2,                        // block/wall bounces for compatible projectiles
                  //   "cast_duration": { "enabled": true, "mode": "absolute", "value": 6.0 }, // continuous casts, seconds
                  //   "shield_interaction": "cannot_disable", // vanilla, can_disable, cannot_disable
                  //   "targeting_mode": "both"               // vanilla, self, others, both
                  // }
                }
              },

              // ================================================================
              // SPELL REWORKS MODULE
              // ================================================================
              "spell_reworks": {
                // COUNTERSPELL
                // enabled=false keeps Iron's original Counterspell even while the
                // spell_reworks module itself is enabled.
                "counterspell": {
                  "enabled": true,

                  // "targeted" = target-lock LONG cast.
                  // "cone"     = instant forward-area Counterspell.
                  "mode": "targeted",

                  // Targeted mode only. 20 ticks = 1 second.
                  "cast_time_ticks": 12,

                  // Used by targeted and cone modes.
                  "range": 12.0,

                  // Targeted mode only. Higher = more forgiving aim acquisition.
                  "aim_assist": 0.35,

                  // Cone mode only. Full cone angle, not half-angle.
                  "angle_degrees": 90.0,

                  // Cone mode only. true prevents hits through solid blocks.
                  "require_line_of_sight": true,

                  // Cone mode only: "all", "nearest", or "crosshair".
                  "target_mode": "all",

                  // Cone debug visualisation; normally leave false.
                  "debug_particles": false
                },

                // ARROW VOLLEY
                // Fires every arrow at once in a forward cone.
                "arrow_volley": {
                  "enabled": true,

                  // Full cone angle in degrees.
                  "cone_angle_degrees": 40.0,

                  // Absolute launch speed in blocks/tick.
                  "projectile_speed": 1.15,

                  // Extra damage added to every arrow per level above I.
                  "damage_per_level": 0.25,

                  // Maximum damaging arrows from one cast that one entity can absorb.
                  "max_hits_per_target": 6,

                  // At or below this distance, use the close-range multiplier.
                  "close_range_distance": 3.0,

                  // Point-blank damage multiplier per arrow (0.60 = 60%).
                  "close_range_damage_multiplier": 0.60,

                  // Damage ramps linearly back to 100% by this distance.
                  "full_damage_distance": 7.0
                },

                // FEATHER FLIGHT (Aeromancy)
                // Replaces air-swimming with controlled feather movement.
                "feather_flight": {
                  "enabled": true,

                  // Maximum downward speed while jump is held. Positive blocks/tick.
                  "slow_fall_speed": 0.115,

                  // Horizontal acceleration added per airborne tick.
                  "air_acceleration": 0.018,

                  // Maximum horizontal speed while airborne.
                  "max_horizontal_speed": 0.48,

                  // Added to vanilla's normal 0.42 jump strength.
                  "extra_jump_strength": 0.16,

                  // Complete fall-damage immunity while Feather Flight is active.
                  "fall_damage_immunity": true
                }
              },

              // ================================================================
              // CUSTOM SPELLS MODULE
              // ================================================================
              // Per-spell enable state for Mage Additions' own spells. Missing
              // entries default to enabled.
              "custom_spells": {
                // "mageadditions:piercing": {
                //   "enabled": false
                // }
              },

              // ================================================================
              // EXPERIMENTAL MODULE
              // ================================================================
              // Reserved for unfinished features that should never be enabled by
              // default in a normal modpack/server.
              "experimental": {
              }
            }
            """;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private enum Mode {
        ABSOLUTE,
        MULTIPLIER;

        static Mode parse(String raw) {
            if (raw == null) {
                return null;
            }

            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record CompiledRule(Mode mode, double value) {}

    private record CompileResult(Map<String, CompiledRule> rules, int skipped) {
        static CompileResult empty() {
            return new CompileResult(Map.of(), 0);
        }
    }

    public enum MovementMode {
        DEFAULT, NORMAL, SLOWED, ROOTED;
        static MovementMode parse(String raw) {
            if (raw == null) return DEFAULT;
            try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }

    public enum CloudMode {
        NATIVE, ON, OFF;

        static CloudMode parse(String raw) {
            if (raw == null) return NATIVE;
            try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return null; }
        }

        public boolean overridden() {
            return this != NATIVE;
        }

        public boolean enabled() {
            return this == ON;
        }
    }

    public enum ShieldInteraction {
        VANILLA, CAN_DISABLE, CANNOT_DISABLE;

        static ShieldInteraction parse(String raw) {
            if (raw == null) return VANILLA;
            try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }

    public enum TargetingMode {
        VANILLA, SELF, OTHERS, BOTH;

        static TargetingMode parse(String raw) {
            if (raw == null) return VANILLA;
            try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return null; }
        }

        public boolean allowsSelf() {
            return this == SELF || this == BOTH;
        }

        public boolean allowsOthers() {
            return this == OTHERS || this == BOTH || this == VANILLA;
        }
    }

    public enum NumericMode {
        ABSOLUTE, MULTIPLIER;

        static NumericMode parse(String raw) {
            if (raw == null) return ABSOLUTE;
            try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }

    public record NumericOverride(boolean enabled, NumericMode mode, double value) {
        public static NumericOverride disabled() {
            return new NumericOverride(false, NumericMode.ABSOLUTE, 0.0);
        }
    }

    public record BehaviorSettings(
            boolean enabled,
            MovementMode movementMode,
            double movementMultiplier,
            Double maxHeightAboveGround,
            Boolean lineOfSightOverride,
            Double minCastDistance,
            NumericOverride rangeOverride,
            NumericOverride projectileSpeedOverride,
            NumericOverride hitboxSizeOverride,
            NumericOverride knockbackOverride,
            NumericOverride areaOfEffectOverride,
            NumericOverride effectDurationOverride,
            CloudMode cloudMode,
            NumericOverride lingerDurationOverride,
            boolean followCursor,
            int bounceCount,
            NumericOverride castDurationOverride,
            ShieldInteraction shieldInteraction,
            TargetingMode targetingMode
    ) {
        /** Untouched spells have no Mage Additions overrides by default. */
        public static BehaviorSettings defaults() {
            return disabled();
        }

        /** No Mage Additions behaviour when the per-spell master switch is off. */
        public static BehaviorSettings disabled() {
            return new BehaviorSettings(
                    false, MovementMode.DEFAULT, 0.5, null, null, null, NumericOverride.disabled(), NumericOverride.disabled(), NumericOverride.disabled(), NumericOverride.disabled(), NumericOverride.disabled(), NumericOverride.disabled(), CloudMode.NATIVE, NumericOverride.disabled(), false, 0, NumericOverride.disabled(), ShieldInteraction.VANILLA, TargetingMode.VANILLA
            );
        }
    }

    private record BehaviorCompileResult(Map<String, BehaviorSettings> rules, int skipped) {
        static BehaviorCompileResult empty() { return new BehaviorCompileResult(Map.of(), 0); }
    }

    private record BalanceSource(
            CastTimeConfig.Settings settings,
            Map<String, CastTimeConfig.Rule> castTimeRules,
            Map<String, CastTimeConfig.Rule> manaRules,
            Map<String, CastTimeConfig.Rule> cooldownRules,
            Map<String, CastTimeConfig.SpellBehavior> behaviorRules
    ) {}

    private record Snapshot(
            Map<String, CompiledRule> castTimeRules,
            Map<String, CompiledRule> manaCostRules,
            Map<String, CompiledRule> cooldownRules,
            Map<String, BehaviorSettings> behaviorRules,
            Map<String, Boolean> customSpellEnabledStates,
            boolean allowInstantSpellDelays,
            int maxCastTimeTicks,
            boolean balanceTweaksEnabled,
            boolean spellReworksEnabled,
            boolean customSpellsEnabled,
            boolean minigameEnabled,
            boolean lootChangesEnabled,
            boolean experimentalEnabled
    ) {
        static Snapshot defaults() {
            return new Snapshot(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    false,
                    72_000,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false
            );
        }
    }

    /** Master switches exposed to the client config screen. */
    public record ModuleStates(
            boolean balanceTweaks,
            boolean spellReworks,
            boolean customSpells,
            boolean minigame,
            boolean lootChanges,
            boolean experimental
    ) {
        public static ModuleStates defaults() {
            return new ModuleStates(true, true, true, true, true, false);
        }
    }

    /**
     * loadedRules remains the cast-time count so the existing reload command stays
     * source-compatible with previous Mage Additions versions.
     */
    public record ReloadResult(
            boolean success,
            int loadedRules,
            int skippedRules,
            String error,
            int manaRules,
            int cooldownRules
    ) {}
}
