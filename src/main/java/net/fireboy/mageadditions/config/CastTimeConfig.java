package net.fireboy.mageadditions.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON model for config/mage_additions.json.
 *
 * Spell IDs are strings on purpose so this works with Iron's addon spell namespaces
 * without Mage Additions needing compile-time dependencies on those addons.
 */
public final class CastTimeConfig {
    public Settings settings = new Settings();
    public Map<String, Rule> cast_time_overrides = new LinkedHashMap<>();
    public CounterspellConfig counterspell = new CounterspellConfig();

    public static final class Settings {
        /**
         * Iron's INSTANT spells use one-shot casting behavior/animation.
         * Leave this false until Mage Additions has dedicated delayed-INSTANT support.
         */
        public boolean allow_instant_spell_delays = false;

        /** One hour at 20 TPS; prevents accidental absurd values. */
        public int max_cast_time_ticks = 72_000;
    }

    public static final class Rule {
        public boolean enabled = true;

        /** "absolute" or "multiplier". */
        public String mode = "absolute";

        /** Ticks for absolute mode; multiplier factor for multiplier mode. */
        public double value = 0.0;
    }
}
