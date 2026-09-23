package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight capability detection for generic Mage Additions overrides.
 *
 * <p>We deliberately inspect implementation references instead of maintaining a
 * registry-name allowlist. That makes addon spells eligible automatically when
 * they are built from the same projectile / direct-damage concepts.</p>
 */
public final class SpellCapabilities {
    private static final Map<Class<?>, Capabilities> CACHE = new ConcurrentHashMap<>();

    private SpellCapabilities() {}

    public static Capabilities detect(AbstractSpell spell) {
        if (spell == null) return Capabilities.NONE;
        return CACHE.computeIfAbsent(spell.getClass(), SpellCapabilities::inspect);
    }

    private static Capabilities inspect(Class<?> spellClass) {
        // Preserve the existing projectile/shield detection exactly: those
        // capabilities are based on references in the concrete spell class.
        String binaryText = readClassBytes(spellClass);
        if (binaryText.isEmpty()) {
            return Capabilities.NONE;
        }

        String lower = binaryText.toLowerCase(java.util.Locale.ROOT);
        boolean projectile = lower.contains("projectile")
                || lower.contains("abstractmagicprojectile")
                || lower.contains("magicprojectile");

        // Direct shield interaction is intentionally conservative. Persistent
        // clouds/effects commonly reference damage APIs too, but usually do not
        // reference projectile/direct-hit concepts in the spell implementation.
        boolean damage = lower.contains("damagesource")
                || lower.contains("spelldamage")
                || lower.contains("hurt(")
                || lower.contains(".hurt");
        boolean directHit = projectile
                || lower.contains("hitresult")
                || lower.contains("entityhitresult")
                || lower.contains("melee");

        // Targeting-mode overrides are deliberately narrower than the generic
        // damage/projectile heuristics. We only expose them when the spell uses
        // Iron's standard entity-target helper and consumes TargetEntityCastData.
        // That is the path Mage Additions can safely redirect to self/other/both
        // without knowing a spell by registry id.
        String targetingText = readClassHierarchyBytes(spellClass).toLowerCase(java.util.Locale.ROOT);
        boolean targetingMode = targetingText.contains("precasttargethelper")
                && targetingText.contains("targetentitycastdata");

        return new Capabilities(projectile, projectile || (damage && directHit), targetingMode);
    }

    private static String readClassHierarchyBytes(Class<?> type) {
        StringBuilder combined = new StringBuilder();
        Class<?> current = type;
        // Stop before AbstractSpell itself. Its shared implementation is not a
        // capability signal for a particular spell; concrete/intermediate spell
        // classes are. This keeps targeting-mode detection conservative.
        while (current != null && current != Object.class && current != AbstractSpell.class) {
            String bytes = readClassBytes(current);
            if (!bytes.isEmpty()) {
                combined.append(bytes).append('\n');
            }
            current = current.getSuperclass();
        }
        return combined.toString();
    }

    private static String readClassBytes(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) return "";
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (Exception ignored) {
            return "";
        }
    }

    public record Capabilities(boolean projectileSpeed, boolean shieldInteraction, boolean targetingMode) {
        public static final Capabilities NONE = new Capabilities(false, false, false);
    }
}
