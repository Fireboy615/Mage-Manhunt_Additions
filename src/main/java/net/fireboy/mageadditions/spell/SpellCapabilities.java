package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight capability detection for generic Mage Additions overrides.
 *
 * <p>We deliberately inspect implementation references instead of maintaining a
 * registry-name allowlist. That makes addon spells eligible automatically when
 * they are built from the same projectile / direct-damage concepts.</p>
 */
public final class SpellCapabilities {
    private static final Map<Class<?>, StructuralCapabilities> CACHE = new ConcurrentHashMap<>();
    private static final Pattern INTERNAL_CLASS_NAME = Pattern.compile("(?:[A-Za-z_$][A-Za-z0-9_$]*/)+[A-Za-z_$][A-Za-z0-9_$]*");

    private SpellCapabilities() {}

    public static Capabilities detect(AbstractSpell spell) {
        if (spell == null) return Capabilities.NONE;
        StructuralCapabilities structural = CACHE.computeIfAbsent(spell.getClass(), SpellCapabilities::inspect);
        boolean continuous = spell.getCastType() == CastType.CONTINUOUS;
        return new Capabilities(
                structural.projectile(),
                structural.projectile(),
                structural.knockback(),
                structural.areaOfEffect(),
                structural.effectDuration(),
                structural.cloudOnImpact(),
                structural.lingerDuration(),
                continuous && structural.followCursorCandidate(),
                structural.projectile(),
                continuous,
                structural.shieldInteraction(),
                structural.targetingMode()
        );
    }

    private static StructuralCapabilities inspect(Class<?> spellClass) {
        // Preserve the existing projectile/shield detection exactly: those
        // capabilities are based on references in the concrete spell class.
        String binaryText = readClassBytes(spellClass);
        if (binaryText.isEmpty()) {
            return StructuralCapabilities.NONE;
        }

        String lower = binaryText.toLowerCase(Locale.ROOT);
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
        String targetingText = readClassHierarchyBytes(spellClass).toLowerCase(Locale.ROOT);
        boolean targetingMode = targetingText.contains("precasttargethelper")
                && targetingText.contains("targetentitycastdata");

        boolean knockback = damage || directHit;
        boolean areaOfEffect = lower.contains("areaeffect")
                || lower.contains("radius")
                || lower.contains("getentitiesofclass")
                || lower.contains("inflate(");
        boolean effectDuration = targetingText.contains("mobeffectinstance")
                || targetingText.contains("addeffect")
                || targetingText.contains("mobeffect");

        ReferencedEntityInfo referenced = inspectReferencedEntities(spellClass, binaryText);
        // Some Iron's spells spawn their projectile through EntityRegistry instead of
        // directly naming a Projectile class (Ball Lightning is one example). Follow
        // that registry hop so bounce/hitbox/projectile-speed controls remain generic.
        projectile = projectile || referenced.projectileEntity();
        knockback = knockback || projectile;
        boolean cloudFactory = referenced.cloudFactory() || hasNamedLingeringEntityReference(spellClass, binaryText);
        boolean followCursorCandidate = hasMutableVec3CastData(spellClass);

        return new StructuralCapabilities(
                projectile,
                knockback,
                areaOfEffect,
                effectDuration,
                cloudFactory,
                referenced.durationEntity() || cloudFactory,
                followCursorCandidate,
                projectile || (damage && directHit),
                targetingMode
        );
    }

    /**
     * Finds the native factory that creates a lingering cloud/pool/field from an
     * impacting spell projectile. Package-private so the runtime event layer can
     * invoke the exact same structurally-detected path.
     */
    static Method findImpactAreaFactory(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                for (Method method : current.getDeclaredMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) continue;
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    boolean creator = name.contains("create") || name.contains("spawn")
                            || name.contains("place") || name.contains("make");
                    boolean lingerArea = name.contains("cloud") || name.contains("puddle")
                            || name.contains("pool") || name.contains("field");
                    if (!creator || !lingerArea) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 0 || (params.length == 1 && Vec3.class.isAssignableFrom(params[0]))) {
                        try {
                            method.setAccessible(true);
                        } catch (RuntimeException ignored) {}
                        return method;
                    }
                }
            } catch (Throwable ignored) {
                // Optional addon dependencies can make reflective inspection fail.
            }
            current = current.getSuperclass();
        }
        return null;
    }

    static boolean hasDurationAccessors(Class<?> type) {
        try {
            Method getter = type.getMethod("getDuration");
            Method setter = type.getMethod("setDuration", int.class);
            return Number.class.isAssignableFrom(boxed(getter.getReturnType())) && setter.getReturnType() == void.class;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }


    /**
     * Bytecode-only fallback for effect entities whose class cannot be reflectively
     * loaded during capability discovery. This stays generic: the entity reference
     * must live in a cloud/puddle/pool/field package and its simple name must match
     * the spell (for example PoisonSplashSpell -> ...poison_cloud.PoisonSplash).
     */
    private static boolean hasNamedLingeringEntityReference(Class<?> spellClass, String binaryText) {
        String spellToken = normalizedSpellToken(spellClass.getSimpleName());
        Matcher matcher = INTERNAL_CLASS_NAME.matcher(binaryText);
        while (matcher.find()) {
            String internal = matcher.group();
            if (internal.startsWith("L") && internal.length() > 1) internal = internal.substring(1);
            String lower = internal.toLowerCase(Locale.ROOT);
            if (!(lower.contains("cloud") || lower.contains("puddle") || lower.contains("pool") || lower.contains("field"))) {
                continue;
            }
            int slash = internal.lastIndexOf('/');
            String simple = slash >= 0 ? internal.substring(slash + 1) : internal;
            if (entityNameMatchesSpell(spellToken, simple)) return true;
        }
        return false;
    }

    private static ReferencedEntityInfo inspectReferencedEntities(Class<?> spellClass, String binaryText) {
        boolean cloudFactory = false;
        boolean durationEntity = false;
        boolean projectileEntity = false;
        Set<Class<?>> direct = referencedClasses(spellClass, binaryText);

        for (Class<?> candidate : direct) {
            try {
                if (!Entity.class.isAssignableFrom(candidate)) continue;
                projectileEntity |= Projectile.class.isAssignableFrom(candidate);
                cloudFactory |= findImpactAreaFactory(candidate) != null;
                durationEntity |= hasDurationAccessors(candidate);
                if (cloudFactory && durationEntity && projectileEntity) break;
            } catch (Throwable ignored) {}
        }

        // Some Iron's spells (notably Poison Splash) spawn their effect through
        // EntityRegistry instead of directly naming the concrete entity in the
        // spell bytecode. Follow registry references one hop, but only accept an
        // entity whose class name matches the spell name. This fixes that
        // indirection without making every spell that touches EntityRegistry
        // look cloud-compatible.
        if (!cloudFactory || !durationEntity) {
            String spellToken = normalizedSpellToken(spellClass.getSimpleName());
            for (Class<?> holder : direct) {
                if (!looksLikeEntityRegistry(holder)) continue;
                String holderBytes = readClassBytes(holder);
                if (holderBytes.isEmpty()) continue;
                for (Class<?> candidate : referencedClassesMatchingSpell(holder, holderBytes, spellToken)) {
                    try {
                        if (!Entity.class.isAssignableFrom(candidate)) continue;
                        projectileEntity |= Projectile.class.isAssignableFrom(candidate);
                        cloudFactory |= findImpactAreaFactory(candidate) != null;
                        durationEntity |= hasDurationAccessors(candidate);
                        if (cloudFactory && durationEntity && projectileEntity) break;
                    } catch (Throwable ignored) {}
                }
                if (cloudFactory && durationEntity) break;
            }
        }

        return new ReferencedEntityInfo(cloudFactory, durationEntity, projectileEntity);
    }

    private static boolean looksLikeEntityRegistry(Class<?> type) {
        String name = type.getName().toLowerCase(Locale.ROOT);
        return name.contains("registr") && name.contains("entit");
    }

    private static String normalizedSpellToken(String simpleName) {
        String value = simpleName == null ? "" : simpleName.toLowerCase(Locale.ROOT);
        if (value.endsWith("spell")) value = value.substring(0, value.length() - 5);
        return value.replaceAll("[^a-z0-9]", "");
    }

    private static boolean entityNameMatchesSpell(String spellToken, String entitySimpleName) {
        if (spellToken.isEmpty() || entitySimpleName == null) return false;
        String entityToken = entitySimpleName.toLowerCase(Locale.ROOT)
                .replace("projectile", "")
                .replace("entity", "")
                .replaceAll("[^a-z0-9]", "");
        return entityToken.equals(spellToken)
                || entityToken.startsWith(spellToken)
                || spellToken.startsWith(entityToken);
    }

    private static Set<Class<?>> referencedClassesMatchingSpell(Class<?> owner, String binaryText, String spellToken) {
        Set<Class<?>> result = new HashSet<>();
        Matcher matcher = INTERNAL_CLASS_NAME.matcher(binaryText);
        ClassLoader loader = owner.getClassLoader();
        while (matcher.find()) {
            String internal = matcher.group();
            String candidate = internal.startsWith("L") && internal.length() > 1 ? internal.substring(1) : internal;
            int slash = candidate.lastIndexOf('/');
            String simple = slash >= 0 ? candidate.substring(slash + 1) : candidate;
            if (!entityNameMatchesSpell(spellToken, simple)) continue;
            loadCandidate(result, loader, candidate);
        }
        return result;
    }

    private static Set<Class<?>> referencedClasses(Class<?> owner, String binaryText) {
        Set<Class<?>> result = new HashSet<>();
        Matcher matcher = INTERNAL_CLASS_NAME.matcher(binaryText);
        ClassLoader loader = owner.getClassLoader();
        while (matcher.find()) {
            String internal = matcher.group();
            loadCandidate(result, loader, internal);
            if (internal.startsWith("L") && internal.length() > 1) {
                loadCandidate(result, loader, internal.substring(1));
            }
        }
        return result;
    }

    private static void loadCandidate(Set<Class<?>> output, ClassLoader loader, String internalName) {
        if (!(internalName.startsWith("io/redspace/") || internalName.startsWith("net/") || internalName.startsWith("com/"))) {
            return;
        }
        String name = internalName.replace('/', '.');
        try {
            output.add(Class.forName(name, false, loader));
        } catch (Throwable ignored) {}
    }

    private static boolean hasMutableVec3CastData(Class<?> spellClass) {
        try {
            for (Class<?> nested : spellClass.getDeclaredClasses()) {
                for (Field field : nested.getDeclaredFields()) {
                    int mods = field.getModifiers();
                    if (!Modifier.isStatic(mods) && !Modifier.isFinal(mods) && Vec3.class.isAssignableFrom(field.getType())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }

    private static String readClassHierarchyBytes(Class<?> type) {
        StringBuilder combined = new StringBuilder();
        Class<?> current = type;
        while (current != null && current != Object.class && current != AbstractSpell.class) {
            String bytes = readClassBytes(current);
            if (!bytes.isEmpty()) combined.append(bytes).append('\n');
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

    private record ReferencedEntityInfo(boolean cloudFactory, boolean durationEntity, boolean projectileEntity) {}

    private record StructuralCapabilities(
            boolean projectile,
            boolean knockback,
            boolean areaOfEffect,
            boolean effectDuration,
            boolean cloudOnImpact,
            boolean lingerDuration,
            boolean followCursorCandidate,
            boolean shieldInteraction,
            boolean targetingMode
    ) {
        static final StructuralCapabilities NONE = new StructuralCapabilities(false, false, false, false, false, false, false, false, false);
    }

    public record Capabilities(
            boolean projectileSpeed,
            boolean hitboxSize,
            boolean knockback,
            boolean areaOfEffect,
            boolean effectDuration,
            boolean cloudOnImpact,
            boolean lingerDuration,
            boolean followCursor,
            boolean bounces,
            boolean castDuration,
            boolean shieldInteraction,
            boolean targetingMode
    ) {
        public static final Capabilities NONE = new Capabilities(false, false, false, false, false, false, false, false, false, false, false, false);
    }
}
