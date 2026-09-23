package net.fireboy.mageadditions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Common/server-safe editor for Mage Additions' generic per-spell overrides.
 *
 * <p>This deliberately lives outside the client package so dedicated servers
 * can authoritatively apply GUI changes received over the network.</p>
 */
public final class SpellOverrideConfigService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SpellOverrideConfigService() {}

    public static SpellRules readSpellRules(String spellId) {
        try {
            Document document = readDocument();
            CastTimeConfig.BalanceTweaks balance = readBalance(document.root, document.config);
            return new SpellRules(
                    toState(balance.cast_time_overrides.get(spellId)),
                    toState(balance.mana_cost_overrides.get(spellId)),
                    toState(balance.cooldown_overrides.get(spellId)),
                    toBehaviorState(balance.spell_behavior_overrides.get(spellId))
            );
        } catch (Exception ignored) {
            return SpellRules.defaults();
        }
    }

    /**
     * Returns every spell id that has a non-default Mage Additions override.
     * The config document is parsed only once so the spell-manager status
     * request does not reread the JSON file once per registered spell.
     */
    public static Set<String> modifiedSpellIds() {
        try {
            Document document = readDocument();
            CastTimeConfig.BalanceTweaks balance = readBalance(document.root, document.config);
            Set<String> result = new LinkedHashSet<>();

            collectEnabledRules(result, balance.cast_time_overrides);
            collectEnabledRules(result, balance.mana_cost_overrides);
            collectEnabledRules(result, balance.cooldown_overrides);

            for (Map.Entry<String, CastTimeConfig.SpellBehavior> entry : balance.spell_behavior_overrides.entrySet()) {
                if (!toBehaviorState(entry.getValue()).isDefault()) {
                    result.add(entry.getKey());
                }
            }
            return Set.copyOf(result);
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static void collectEnabledRules(Set<String> output, Map<String, CastTimeConfig.Rule> rules) {
        for (Map.Entry<String, CastTimeConfig.Rule> entry : rules.entrySet()) {
            if (toState(entry.getValue()).enabled()) {
                output.add(entry.getKey());
            }
        }
    }

    public static CastTimeOverrides.ReloadResult saveSpellRules(String spellId, SpellRules rules) {
        try {
            Document document = readDocument();
            CastTimeConfig.BalanceTweaks balance = readBalance(document.root, document.config);

            updateRule(balance.cast_time_overrides, spellId, rules.castTime());
            updateRule(balance.mana_cost_overrides, spellId, rules.mana());
            updateRule(balance.cooldown_overrides, spellId, rules.cooldown());

            writeTopLevelObject(document.raw, "balance_tweaks", GSON.toJson(balance));
            return CastTimeOverrides.reload();
        } catch (Exception exception) {
            return new CastTimeOverrides.ReloadResult(false, 0, 0, rootMessage(exception), 0, 0);
        }
    }


    /** Saves the fields currently exposed by the server-authoritative spell editor. */
    public static CastTimeOverrides.ReloadResult saveEditorRules(
            String spellId,
            RuleState castTime,
            BehaviorState behavior
    ) {
        try {
            Document document = readDocument();
            CastTimeConfig.BalanceTweaks balance = readBalance(document.root, document.config);

            updateRule(balance.cast_time_overrides, spellId, castTime);
            updateBehavior(balance.spell_behavior_overrides, spellId, behavior);

            writeTopLevelObject(document.raw, "balance_tweaks", GSON.toJson(balance));
            return CastTimeOverrides.reload();
        } catch (Exception exception) {
            return new CastTimeOverrides.ReloadResult(false, 0, 0, rootMessage(exception), 0, 0);
        }
    }

    /**
     * Updates only the cast-time rule. Legacy Mage Additions mana/cooldown rules
     * are intentionally left untouched so removing them from the GUI does not
     * silently rewrite an existing hand-authored config.
     */
    public static CastTimeOverrides.ReloadResult saveCastTimeRule(String spellId, RuleState castTime) {
        try {
            Document document = readDocument();
            CastTimeConfig.BalanceTweaks balance = readBalance(document.root, document.config);

            updateRule(balance.cast_time_overrides, spellId, castTime);

            writeTopLevelObject(document.raw, "balance_tweaks", GSON.toJson(balance));
            return CastTimeOverrides.reload();
        } catch (Exception exception) {
            return new CastTimeOverrides.ReloadResult(false, 0, 0, rootMessage(exception), 0, 0);
        }
    }

    private static Document readDocument() throws Exception {
        Path path = CastTimeOverrides.configPath();
        if (Files.notExists(path)) {
            CastTimeOverrides.reload();
        }

        String raw = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(stripJsonComments(raw));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Root config value must be a JSON object");
        }

        JsonObject root = parsed.getAsJsonObject();
        CastTimeConfig config = GSON.fromJson(root, CastTimeConfig.class);
        if (config == null) {
            config = new CastTimeConfig();
        }
        return new Document(raw, root, config);
    }

    private static CastTimeConfig.BalanceTweaks readBalance(JsonObject root, CastTimeConfig config) {
        CastTimeConfig.BalanceTweaks balance;

        if (root.has("balance_tweaks") && config.balance_tweaks != null) {
            balance = config.balance_tweaks;
        } else {
            balance = new CastTimeConfig.BalanceTweaks();
            balance.settings = config.settings != null ? config.settings : new CastTimeConfig.Settings();
            balance.cast_time_overrides = nonNullMap(config.cast_time_overrides);
            balance.mana_cost_overrides = nonNullMap(config.mana_cost_overrides);
            balance.cooldown_overrides = nonNullMap(config.cooldown_overrides);
        }

        if (balance.settings == null) {
            balance.settings = new CastTimeConfig.Settings();
        }
        balance.cast_time_overrides = nonNullMap(balance.cast_time_overrides);
        balance.mana_cost_overrides = nonNullMap(balance.mana_cost_overrides);
        balance.cooldown_overrides = nonNullMap(balance.cooldown_overrides);
        balance.spell_behavior_overrides = nonNullBehaviorMap(balance.spell_behavior_overrides);
        return balance;
    }

    private static Map<String, CastTimeConfig.Rule> nonNullMap(Map<String, CastTimeConfig.Rule> map) {
        return map != null ? map : new LinkedHashMap<>();
    }

    private static Map<String, CastTimeConfig.SpellBehavior> nonNullBehaviorMap(
            Map<String, CastTimeConfig.SpellBehavior> map
    ) {
        return map != null ? map : new LinkedHashMap<>();
    }

    private static RuleState toState(CastTimeConfig.Rule rule) {
        if (rule == null || !rule.enabled) {
            return RuleState.disabled();
        }
        return new RuleState(true, normalizeMode(rule.mode), rule.value);
    }


    private static BehaviorState toBehaviorState(CastTimeConfig.SpellBehavior behavior) {
        if (behavior == null) {
            return BehaviorState.defaults();
        }

        // Legacy behaviour entries predate the per-spell master switch, so keep
        // those enabled. New untouched spells have no entry and therefore remain
        // disabled by default.
        boolean overridesEnabled = behavior.enabled == null || behavior.enabled;

        boolean maxHeightEnabled = behavior.max_height_above_ground_enabled != null
                ? behavior.max_height_above_ground_enabled
                : behavior.max_height_above_ground != null
                    || "blocked".equalsIgnoreCase(behavior.airborne)
                    || "allowed".equalsIgnoreCase(behavior.airborne);
        Double maxHeight = null;
        if (maxHeightEnabled) {
            maxHeight = behavior.max_height_above_ground;
            if (maxHeight == null) {
                if ("blocked".equalsIgnoreCase(behavior.airborne)) {
                    maxHeight = 0.0;
                } else if ("allowed".equalsIgnoreCase(behavior.airborne)) {
                    maxHeight = 1_000_000.0;
                } else {
                    maxHeight = 10.0;
                }
            }
        }

        Boolean requireLineOfSight = behavior.require_line_of_sight;
        if (requireLineOfSight == null && "required".equalsIgnoreCase(behavior.line_of_sight)) {
            requireLineOfSight = Boolean.TRUE;
        }

        return new BehaviorState(
                overridesEnabled,
                normalizeMovement(behavior.movement),
                finiteOrDefault(behavior.movement_multiplier, 0.5),
                maxHeight == null ? null : (nullableDistance(maxHeight) == null ? 10.0 : maxHeight),
                requireLineOfSight,
                nullableDistance(behavior.min_cast_distance),
                toState(behavior.range),
                toState(behavior.projectile_speed),
                normalizeShieldInteraction(behavior.shield_interaction),
                normalizeTargetingMode(behavior.targeting_mode)
        ).normalized();
    }

    private static void updateBehavior(
            Map<String, CastTimeConfig.SpellBehavior> map,
            String spellId,
            BehaviorState state
    ) {
        BehaviorState safe = state == null ? BehaviorState.defaults() : state.normalized();
        if (safe.isDefault()) {
            map.remove(spellId);
            return;
        }

        CastTimeConfig.SpellBehavior behavior = new CastTimeConfig.SpellBehavior();
        behavior.enabled = safe.enabled();
        behavior.movement = safe.movementMode();
        behavior.movement_multiplier = safe.movementMultiplier();
        behavior.max_height_above_ground_enabled = safe.maxHeightAboveGround() != null;
        behavior.max_height_above_ground = safe.maxHeightAboveGround();
        behavior.require_line_of_sight = safe.lineOfSightOverride();

        // New saves no longer use the legacy mode strings.
        behavior.airborne = "default";
        behavior.line_of_sight = "default";

        behavior.min_cast_distance = safe.minCastDistance();
        behavior.range = toConfigRule(safe.range());
        behavior.projectile_speed = toConfigRule(safe.projectileSpeed());
        behavior.shield_interaction = safe.shieldInteraction();
        behavior.targeting_mode = safe.targetingMode();
        map.put(spellId, behavior);
    }

    private static CastTimeConfig.Rule toConfigRule(RuleState state) {
        if (state == null || !state.enabled()) {
            return null;
        }
        CastTimeConfig.Rule rule = new CastTimeConfig.Rule();
        rule.enabled = true;
        rule.mode = normalizeMode(state.mode());
        rule.value = state.value();
        return rule;
    }

    private static String normalizeShieldInteraction(String mode) {
        if (mode == null) return "vanilla";
        return switch (mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "can_disable", "cannot_disable" -> mode.trim().toLowerCase(java.util.Locale.ROOT);
            default -> "vanilla";
        };
    }

    private static String normalizeTargetingMode(String mode) {
        if (mode == null) return "vanilla";
        return switch (mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "self", "others", "both" -> mode.trim().toLowerCase(java.util.Locale.ROOT);
            default -> "vanilla";
        };
    }

    private static String normalizeMovement(String mode) {
        if (mode == null) return "default";
        return switch (mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "normal", "slowed", "rooted" -> mode.trim().toLowerCase(java.util.Locale.ROOT);
            default -> "default";
        };
    }

    private static double finiteOrDefault(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static Double nullableDistance(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0) return null;
        return value;
    }

    private static void updateRule(Map<String, CastTimeConfig.Rule> map, String spellId, RuleState state) {
        if (state == null || !state.enabled()) {
            map.remove(spellId);
            return;
        }

        CastTimeConfig.Rule rule = new CastTimeConfig.Rule();
        rule.enabled = true;
        rule.mode = normalizeMode(state.mode());
        rule.value = state.value();
        map.put(spellId, rule);
    }

    private static String normalizeMode(String mode) {
        return "multiplier".equalsIgnoreCase(mode) ? "multiplier" : "absolute";
    }

    private static void writeTopLevelObject(String raw, String property, String replacement) throws Exception {
        Path path = CastTimeOverrides.configPath();
        int[] bounds = findTopLevelObjectProperty(raw, property);
        String updated;

        if (bounds != null) {
            updated = raw.substring(0, bounds[0]) + replacement + raw.substring(bounds[1]);
        } else {
            int rootOpen = findRootObjectOpen(raw);
            if (rootOpen < 0) {
                throw new IllegalArgumentException("Root config value must be a JSON object");
            }
            String insertion = "\n  \"" + property + "\": " + indentAfterFirstLine(replacement, "  ") + ",";
            updated = raw.substring(0, rootOpen + 1) + insertion + raw.substring(rootOpen + 1);
        }

        Files.writeString(path, updated, StandardCharsets.UTF_8);
    }

    private static String indentAfterFirstLine(String input, String indent) {
        return input.replace("\n", "\n" + indent);
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
                if (c == '\n' || c == '\r') lineComment = false;
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
            if (c != '"' || depth != 1) continue;

            int stringEnd = findStringEnd(input, i);
            if (stringEnd < 0) throw new IllegalArgumentException("Unterminated string in config");
            String token = input.substring(i + 1, stringEnd);
            if (!token.equals(propertyName)) {
                i = stringEnd;
                continue;
            }

            int cursor = skipWhitespaceAndComments(input, stringEnd + 1);
            if (cursor >= input.length() || input.charAt(cursor) != ':') continue;
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
                if (c == '\n' || c == '\r') lineComment = false;
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
                if (stringEnd < 0) throw new IllegalArgumentException("Unterminated string in config");
                i = stringEnd;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new IllegalArgumentException("Unterminated object in config");
    }

    private static int findStringEnd(String input, int quoteStart) {
        boolean escaped = false;
        for (int i = quoteStart + 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') return i;
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
                while (i < input.length() && input.charAt(i) != '\n' && input.charAt(i) != '\r') i++;
                continue;
            }
            if (c == '/' && next == '*') {
                int end = input.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("Unterminated block comment in config");
                i = end + 2;
                continue;
            }
            break;
        }
        return i;
    }

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
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                output.append(c);
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
            output.append(c);
        }
        return output.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public record RuleState(boolean enabled, String mode, double value) {
        public static RuleState disabled() {
            return new RuleState(false, "absolute", 0.0);
        }
    }

    public record BehaviorState(
            boolean enabled,
            String movementMode,
            double movementMultiplier,
            Double maxHeightAboveGround,
            Boolean lineOfSightOverride,
            Double minCastDistance,
            RuleState range,
            RuleState projectileSpeed,
            String shieldInteraction,
            String targetingMode
    ) {
        public static BehaviorState defaults() {
            return new BehaviorState(false, "default", 0.5, null, null, null, RuleState.disabled(), RuleState.disabled(), "vanilla", "vanilla");
        }

        public BehaviorState normalized() {
            double movement = Double.isFinite(movementMultiplier) ? movementMultiplier : 0.5;
            movement = Math.max(0.0, Math.min(10.0, movement));

            Double maxHeight = maxHeightAboveGround;
            if (maxHeight != null) {
                if (!Double.isFinite(maxHeight)) {
                    maxHeight = 10.0;
                }
                maxHeight = Math.max(0.0, Math.min(1_000_000.0, maxHeight));
            }

            Double min = nullableDistance(minCastDistance);
            RuleState rangeRule = normalizeRuleState(range);
            RuleState projectileRule = normalizeRuleState(projectileSpeed);

            return new BehaviorState(
                    enabled,
                    normalizeMovement(movementMode),
                    movement,
                    maxHeight,
                    lineOfSightOverride,
                    min,
                    rangeRule,
                    projectileRule,
                    normalizeShieldInteraction(shieldInteraction),
                    normalizeTargetingMode(targetingMode)
            );
        }

        public boolean isDefault() {
            BehaviorState value = normalized();
            return !value.enabled
                    && value.movementMode.equals("default")
                    && value.maxHeightAboveGround == null
                    && value.lineOfSightOverride == null
                    && value.minCastDistance == null
                    && !value.range.enabled()
                    && !value.projectileSpeed.enabled()
                    && value.shieldInteraction.equals("vanilla")
                    && value.targetingMode.equals("vanilla");
        }
    }

    private static RuleState normalizeRuleState(RuleState state) {
        if (state == null || !state.enabled()) {
            return RuleState.disabled();
        }
        double value = state.value();
        if (!Double.isFinite(value) || value < 0.0) {
            return RuleState.disabled();
        }
        return new RuleState(true, normalizeMode(state.mode()), value);
    }

    public record SpellRules(RuleState castTime, RuleState mana, RuleState cooldown, BehaviorState behavior) {
        public static SpellRules defaults() {
            return new SpellRules(
                    RuleState.disabled(),
                    RuleState.disabled(),
                    RuleState.disabled(),
                    BehaviorState.defaults()
            );
        }
    }

    private record Document(String raw, JsonObject root, CastTimeConfig config) {}
}
