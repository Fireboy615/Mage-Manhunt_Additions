package net.fireboy.mageadditions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and resolves per-spell cast-time rules.
 *
 * The Mixin calls resolve() only after Iron's has already calculated its normal
 * effective cast time. This preserves Iron's own spell-specific timing logic first.
 */
public final class CastTimeOverrides {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("mage_additions.json");

    private static volatile Snapshot snapshot = Snapshot.defaults();

    private CastTimeOverrides() {}

    public static ReloadResult reload() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.notExists(CONFIG_PATH)) {
                writeDefaultConfig();
            }

            CastTimeConfig config;
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, CastTimeConfig.class);
            }

            if (config == null) {
                config = new CastTimeConfig();
            }
            if (config.settings == null) {
                config.settings = new CastTimeConfig.Settings();
            }
            if (config.cast_time_overrides == null) {
                config.cast_time_overrides = new LinkedHashMap<>();
            }
            if (config.counterspell == null) {
                config.counterspell = new CounterspellConfig();
            }

            Map<String, CompiledRule> compiled = new LinkedHashMap<>();
            int skipped = 0;

            for (Map.Entry<String, CastTimeConfig.Rule> entry : config.cast_time_overrides.entrySet()) {
                String spellId = entry.getKey();
                CastTimeConfig.Rule rawRule = entry.getValue();

                if (ResourceLocation.tryParse(spellId) == null) {
                    MageAdditions.LOGGER.warn("Ignoring invalid spell id '{}' in {}", spellId, CONFIG_PATH);
                    skipped++;
                    continue;
                }

                if (rawRule == null || !rawRule.enabled) {
                    continue;
                }

                Mode mode = Mode.parse(rawRule.mode);
                if (mode == null) {
                    MageAdditions.LOGGER.warn(
                        "Ignoring cast-time rule for '{}': unknown mode '{}'",
                        spellId,
                        rawRule.mode
                    );
                    skipped++;
                    continue;
                }

                if (!Double.isFinite(rawRule.value) || rawRule.value < 0.0) {
                    MageAdditions.LOGGER.warn(
                        "Ignoring cast-time rule for '{}': value must be finite and >= 0",
                        spellId
                    );
                    skipped++;
                    continue;
                }

                compiled.put(spellId, new CompiledRule(mode, rawRule.value));
            }

            // Counterspell shares this same config file and reload command.
            // Only swap its live settings after the JSON parsed successfully.
            net.fireboy.mageadditions.spell.CounterspellHandler.reload(config.counterspell);

            int maxTicks = Math.max(0, config.settings.max_cast_time_ticks);
            snapshot = new Snapshot(
                Map.copyOf(compiled),
                config.settings.allow_instant_spell_delays,
                maxTicks
            );

            MageAdditions.LOGGER.info(
                "Loaded {} Mage Additions cast-time override(s) from {} ({} skipped)",
                compiled.size(),
                CONFIG_PATH,
                skipped
            );

            return new ReloadResult(true, compiled.size(), skipped, null);
        } catch (Exception exception) {
            MageAdditions.LOGGER.error(
                "Failed to load {}. Keeping the previous cast-time rules.",
                CONFIG_PATH,
                exception
            );
            return new ReloadResult(false, snapshot.rules.size(), 0, exception.getMessage());
        }
    }

    /**
     * @param spell Iron's spell being cast.
     * @param originalEffectiveTicks the duration already calculated by Iron's.
     */
    public static int resolve(AbstractSpell spell, int originalEffectiveTicks) {
        Snapshot current = snapshot;

        // Dedicated spell patches may supply a new base cast time before the
        // generic per-spell override is applied. Counterspell TARGETED mode uses
        // this to become a real LONG cast while preserving the global INSTANT
        // safety rule for every other spell.
        int baseEffectiveTicks = net.fireboy.mageadditions.spell.CounterspellHandler
            .getBaseCastTime(spell, originalEffectiveTicks);

        CompiledRule rule = current.rules.get(spell.getSpellId());

        if (rule == null) {
            return baseEffectiveTicks;
        }

        double result = switch (rule.mode) {
            case ABSOLUTE -> rule.value;
            case MULTIPLIER -> baseEffectiveTicks * rule.value;
        };

        int resolvedTicks = clampRoundedTicks(result, current.maxCastTimeTicks);

        // Do not silently turn an INSTANT spell into a delayed spell until our
        // animation/instant-cast compatibility layer is implemented.
        if (spell.getCastType() == CastType.INSTANT
            && resolvedTicks > 0
            && !current.allowInstantSpellDelays) {
            return originalEffectiveTicks;
        }

        return resolvedTicks;
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    private static int clampRoundedTicks(double value, int maxTicks) {
        long rounded = Math.round(value);
        if (rounded <= 0L || maxTicks == 0) {
            return 0;
        }
        return (int) Math.min(rounded, maxTicks);
    }

    private static void writeDefaultConfig() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty(
            "_comment",
            "Cast time is in ticks (20 ticks = 1 second). Spell IDs can belong to Iron's or any Iron's addon."
        );

        JsonObject settings = new JsonObject();
        settings.addProperty("allow_instant_spell_delays", false);
        settings.addProperty("max_cast_time_ticks", 72_000);
        root.add("settings", settings);

        root.add("cast_time_overrides", new JsonObject());

        JsonObject counterspell = new JsonObject();
        counterspell.addProperty("enabled", false);
        counterspell.addProperty("mode", "cone");
        counterspell.addProperty("cast_time_ticks", 12);
        counterspell.addProperty("range", 6.0);
        counterspell.addProperty("aim_assist", 0.35);
        counterspell.addProperty("angle_degrees", 90.0);
        counterspell.addProperty("require_line_of_sight", true);
        counterspell.addProperty("target_mode", "all");
        counterspell.addProperty("debug_particles", false);
        root.add("counterspell", counterspell);

        JsonObject examples = new JsonObject();
        examples.add(
            "irons_spellbooks:fireball",
            JsonParser.parseString("{\"enabled\":true,\"mode\":\"absolute\",\"value\":20}")
        );
        examples.add(
            "some_addon:meteor",
            JsonParser.parseString("{\"enabled\":true,\"mode\":\"multiplier\",\"value\":0.5}")
        );
        root.add("_examples_copy_into_cast_time_overrides", examples);

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
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

    private record Snapshot(
        Map<String, CompiledRule> rules,
        boolean allowInstantSpellDelays,
        int maxCastTimeTicks
    ) {
        static Snapshot defaults() {
            return new Snapshot(Map.of(), false, 72_000);
        }
    }

    public record ReloadResult(boolean success, int loadedRules, int skippedRules, String error) {}
}
