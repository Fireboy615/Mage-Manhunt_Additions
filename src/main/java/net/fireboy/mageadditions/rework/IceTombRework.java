package net.fireboy.mageadditions.rework;

import net.fireboy.mageadditions.config.CastTimeOverrides;

/** Hit-based durability rules for the cast Ice Tomb rework. */
public final class IceTombRework {
    private IceTombRework() {}

    public static boolean enabled() {
        return CastTimeOverrides.spellReworksEnabled();
    }

    /**
     * Level I blocks two hits, with one additional blocked hit for every
     * additional spell level. Iron's Ice Tomb currently has eight levels.
     */
    public static int blockedHits(int spellLevel) {
        return Math.max(2, spellLevel + 1);
    }
}
