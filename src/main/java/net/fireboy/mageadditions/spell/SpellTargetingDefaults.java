package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures the native range supplied to Iron's generic target helper.
 *
 * <p>Most targeted spells use the same helper, but the range is supplied by the
 * spell at runtime rather than exposed as a universal property. Recording it
 * here lets the editor show/reset to the actual value once Iron's has evaluated
 * that spell. Until then we use Iron's common 32-block target-helper baseline.</p>
 */
public final class SpellTargetingDefaults {
    public static final double DEFAULT_MIN_DISTANCE = 0.0;
    public static final double FALLBACK_MAX_DISTANCE = 32.0;
    public static final boolean DEFAULT_REQUIRE_LINE_OF_SIGHT = true;

    private static final Map<String, Double> OBSERVED_NATIVE_MAX_DISTANCE = new ConcurrentHashMap<>();

    private SpellTargetingDefaults() {}

    public static void observeNativeRange(AbstractSpell spell, double range) {
        if (spell == null || !Double.isFinite(range) || range < 0.0) {
            return;
        }
        OBSERVED_NATIVE_MAX_DISTANCE.put(spell.getSpellId(), range);
    }

    public static double originalMaxDistance(AbstractSpell spell) {
        if (spell == null) {
            return FALLBACK_MAX_DISTANCE;
        }
        return OBSERVED_NATIVE_MAX_DISTANCE.getOrDefault(spell.getSpellId(), FALLBACK_MAX_DISTANCE);
    }
}
