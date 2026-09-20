package net.fireboy.mageadditions.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON model for config/mage_additions.json.
 *
 * The preferred config layout is modular:
 *
 * modules -> master switches
 * balance_tweaks -> cast time / mana / cooldown
 * spell_reworks -> Counterspell and future vanilla-Iron's reworks
 * custom_spells -> reserved for Mage Additions spells
 * experimental -> reserved for unfinished/opt-in mechanics
 *
 * Legacy top-level fields are retained so older Mage Additions configs continue
 * to load while users migrate to the modular layout.
 */
public final class CastTimeConfig {
    // -------------------------------------------------------------------------
    // Preferred modular config layout
    // -------------------------------------------------------------------------

    public Modules modules = new Modules();
    public BalanceTweaks balance_tweaks = new BalanceTweaks();
    public SpellReworks spell_reworks = new SpellReworks();

    /** Reserved for future custom-spell settings. */
    public Map<String, Object> custom_spells = new LinkedHashMap<>();

    /** Reserved for future experimental settings. */
    public Map<String, Object> experimental = new LinkedHashMap<>();

    public static final class Modules {
        /** Master switch for cast-time, mana-cost and cooldown overrides. */
        public boolean balance_tweaks = true;

        /** Master switch for Counterspell and future existing-spell rewrites. */
        public boolean spell_reworks = true;

        /** Master switch reserved for Mage Additions custom spells. */
        public boolean custom_spells = true;

        /** Master switch for unfinished/experimental features. */
        public boolean experimental = false;
    }

    public static final class BalanceTweaks {
        public Settings settings = new Settings();
        public Map<String, Rule> cast_time_overrides = new LinkedHashMap<>();
        public Map<String, Rule> mana_cost_overrides = new LinkedHashMap<>();
        public Map<String, Rule> cooldown_overrides = new LinkedHashMap<>();
    }

    public static final class SpellReworks {
        public CounterspellConfig counterspell = new CounterspellConfig();
    }

    public static final class Settings {
        /**
         * Iron's INSTANT spells use one-shot casting behaviour/animation.
         * Leave false unless a spell has a dedicated rework that safely changes
         * its CastType (targeted Counterspell already does this itself).
         */
        public boolean allow_instant_spell_delays = false;

        /** One hour at 20 TPS; prevents accidental absurd cast-time values. */
        public int max_cast_time_ticks = 72_000;
    }

    public static final class Rule {
        public boolean enabled = true;

        /** "absolute" or "multiplier". */
        public String mode = "absolute";

        /** Meaning depends on the rule type; see the generated example config. */
        public double value = 0.0;
    }

    // -------------------------------------------------------------------------
    // Legacy layout - kept for backwards compatibility.
    // Do not use these in new example configs.
    // -------------------------------------------------------------------------

    public Settings settings = null;
    public Map<String, Rule> cast_time_overrides = null;
    public Map<String, Rule> mana_cost_overrides = null;
    public Map<String, Rule> cooldown_overrides = null;
    public CounterspellConfig counterspell = null;
}
