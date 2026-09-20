package net.fireboy.mageadditions.compat.irons;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Version-isolated bridge for Iron's per-spell balancing configuration.
 *
 * <p>Mage Additions intentionally keeps all knowledge of Iron's old 3.14.x
 * ServerConfigs implementation in this one class. The rest of the UI/network
 * layer only works with the Settings record below. When Mage Additions moves to
 * Iron's newer data-driven spell-config system, this is the main adapter that
 * needs replacing/expanding instead of rewriting the editor and packets.</p>
 *
 * <p>The 3.14.x adapter uses reflection so Mage Additions does not have a hard
 * compile-time dependency on io.redspace.ironsspellbooks.config.ServerConfigs.
 * If that class disappears in a later Iron's release, Mage Additions can still
 * load and report the backend as unsupported instead of failing linkage here.</p>
 */
public final class IronsSpellConfigBridge {
    private static final String LEGACY_SERVER_CONFIGS = "io.redspace.ironsspellbooks.config.ServerConfigs";
    private static final String MODERN_CONFIG_MANAGER = "io.redspace.ironsspellbooks.api.config.SpellConfigManager";
    private static final String MODERN_CONFIG_PARAMETER = "io.redspace.ironsspellbooks.api.config.SpellConfigParameter";
    private static final String MODERN_IRON_CONFIG_PARAMETERS = "io.redspace.ironsspellbooks.api.config.IronConfigParameters";

    private static final String KEY_ENABLED = "Enabled";
    private static final String KEY_SCHOOL = "School";
    private static final String KEY_MAX_LEVEL = "MaxLevel";
    private static final String KEY_MIN_RARITY = "MinRarity";
    private static final String KEY_MANA_MULTIPLIER = "ManaCostMultiplier";
    private static final String KEY_POWER_MULTIPLIER = "SpellPowerMultiplier";
    private static final String KEY_COOLDOWN_SECONDS = "CooldownInSeconds";
    private static final String KEY_ALLOW_CRAFTING = "AllowCrafting";

    private IronsSpellConfigBridge() {}

    public static BackendInfo backendInfo() {
        // Detect the data-driven backend first. Iron's kept the old
        // ServerConfigs#getSpellConfig API around as deprecated compatibility
        // for a while after 3.15.0, so checking legacy first would incorrectly
        // classify newer versions as 3.14.x.
        try {
            Class.forName(MODERN_CONFIG_MANAGER, false, IronsSpellConfigBridge.class.getClassLoader());
            Class.forName(MODERN_CONFIG_PARAMETER, false, IronsSpellConfigBridge.class.getClassLoader());
            return new BackendInfo(
                    false,
                    "Iron's data-driven spell config (3.15+)",
                    "Detected Iron's newer data-driven config backend. Mage Additions can read it, but this build " +
                            "intentionally disables writing until the modern JSON/datapack writer adapter is enabled. " +
                            "That prevents editing a global file that a world datapack may override."
            );
        } catch (ClassNotFoundException ignored) {
            // 3.14.x does not have the data-driven manager.
        }

        try {
            Class<?> serverConfigs = Class.forName(LEGACY_SERVER_CONFIGS, false, IronsSpellConfigBridge.class.getClassLoader());
            serverConfigs.getMethod("getSpellConfig", AbstractSpell.class);
            serverConfigs.getField("SPEC");
            return new BackendInfo(true, "Iron's legacy spell config (3.14.x)", null);
        } catch (ReflectiveOperationException ignored) {
            return new BackendInfo(
                    false,
                    "Unsupported Iron's spell-config backend",
                    "Mage Additions could not identify this Iron's spell-config implementation."
            );
        }
    }

    public static Settings read(AbstractSpell spell) {
        BackendInfo backend = backendInfo();
        if (backend.name().contains("3.14")) {
            try {
                Object parameters = legacySpellParameters(spell);
                boolean enabled = (boolean) invokeNoArg(parameters, "enabled");
                SchoolType school = (SchoolType) invokeNoArg(parameters, "school");
                int maxLevel = ((Number) invokeNoArg(parameters, "maxLevel")).intValue();
                SpellRarity minRarity = (SpellRarity) invokeNoArg(parameters, "minRarity");
                double manaMultiplier = ((Number) invokeNoArg(parameters, "manaMultiplier")).doubleValue();
                double powerMultiplier = ((Number) invokeNoArg(parameters, "powerMultiplier")).doubleValue();
                double cooldownSeconds = ((Number) invokeNoArg(parameters, "cooldownInTicks")).doubleValue() / 20.0;
                boolean allowCrafting = (boolean) invokeNoArg(parameters, "allowCrafting");

                return new Settings(
                        enabled,
                        school.getId(),
                        maxLevel,
                        minRarity,
                        manaMultiplier,
                        powerMultiplier,
                        cooldownSeconds,
                        allowCrafting
                );
            } catch (Exception exception) {
                MageAdditions.LOGGER.warn("Unable to read Iron's live config for {} through legacy adapter", spell.getSpellId(), exception);
            }
        } else if (backend.name().contains("3.15+")) {
            try {
                return readModern(spell);
            } catch (Exception exception) {
                MageAdditions.LOGGER.warn("Unable to read Iron's live config for {} through data-driven adapter", spell.getSpellId(), exception);
            }
        }
        return fallbackRead(spell);
    }

    private static Settings readModern(AbstractSpell spell) throws Exception {
        ClassLoader loader = IronsSpellConfigBridge.class.getClassLoader();
        Class<?> manager = Class.forName(MODERN_CONFIG_MANAGER, true, loader);
        Class<?> parameter = Class.forName(MODERN_CONFIG_PARAMETER, true, loader);

        Method getter = null;
        for (Method method : manager.getMethods()) {
            if (method.getName().equals("getSpellConfigValue") && method.getParameterCount() == 2) {
                getter = method;
                break;
            }
        }
        if (getter == null) {
            throw new NoSuchMethodException(MODERN_CONFIG_MANAGER + "#getSpellConfigValue");
        }

        Object getterTarget = null;
        if (!Modifier.isStatic(getter.getModifiers())) {
            Method getInstance = manager.getMethod("getInstance");
            getterTarget = getInstance.invoke(null);
        }

        Object schoolParam = modernParameter(loader, parameter, "SCHOOL");
        Object minRarityParam = modernParameter(loader, parameter, "MIN_RARITY");
        Object maxLevelParam = modernParameter(loader, parameter, "MAX_LEVEL");
        Object enabledParam = modernParameter(loader, parameter, "ENABLED");
        Object cooldownParam = modernParameter(loader, parameter, "COOLDOWN_IN_SECONDS");
        Object craftingParam = modernParameter(loader, parameter, "ALLOW_CRAFTING");
        Object powerParam = modernParameter(loader, parameter, "POWER_MULTIPLIER");
        Object manaParam = modernParameter(loader, parameter, "MANA_MULTIPLIER");

        Object schoolValue = getter.invoke(getterTarget, spell, schoolParam);
        ResourceLocation schoolId;
        if (schoolValue instanceof SchoolType school) {
            schoolId = school.getId();
        } else if (schoolValue instanceof ResourceLocation id) {
            schoolId = id;
        } else {
            schoolId = ResourceLocation.parse(String.valueOf(schoolValue));
        }

        Object rarityValue = getter.invoke(getterTarget, spell, minRarityParam);
        SpellRarity rarity = rarityValue instanceof SpellRarity spellRarity
                ? spellRarity
                : SpellRarity.valueOf(String.valueOf(rarityValue));

        int maxLevel = ((Number) getter.invoke(getterTarget, spell, maxLevelParam)).intValue();
        boolean enabled = (boolean) getter.invoke(getterTarget, spell, enabledParam);
        double cooldown = ((Number) getter.invoke(getterTarget, spell, cooldownParam)).doubleValue();
        boolean crafting = (boolean) getter.invoke(getterTarget, spell, craftingParam);
        double power = ((Number) getter.invoke(getterTarget, spell, powerParam)).doubleValue();
        double mana = ((Number) getter.invoke(getterTarget, spell, manaParam)).doubleValue();

        return new Settings(enabled, schoolId, maxLevel, rarity, mana, power, cooldown, crafting);
    }

    /**
     * 3.15 initially exposed the built-in parameter singletons through the
     * compatibility IronConfigParameters holder; newer builds expose them
     * directly on SpellConfigParameter. Supporting both keeps the reader useful
     * across that transition.
     */
    private static Object modernParameter(ClassLoader loader, Class<?> parameterClass, String fieldName) throws Exception {
        try {
            return parameterClass.getField(fieldName).get(null);
        } catch (NoSuchFieldException missingDirectField) {
            Class<?> compatibilityHolder = Class.forName(MODERN_IRON_CONFIG_PARAMETERS, true, loader);
            return compatibilityHolder.getField(fieldName).get(null);
        }
    }

    public static Settings defaults(AbstractSpell spell) {
        var defaults = spell.getDefaultConfig();
        return new Settings(
                defaults.enabled,
                defaults.schoolResource,
                defaults.maxLevel,
                defaults.minRarity,
                1.0,
                1.0,
                defaults.cooldownInSeconds,
                defaults.allowCrafting
        );
    }

    /**
     * Applies values to Iron's live runtime state and persists the server TOML.
     * Runtime mutation is deliberately separated from persistence so the exact
     * same adapter can be used on connected clients without writing client files.
     */
    public static SaveResult saveLive(AbstractSpell spell, Settings settings) {
        BackendInfo backend = backendInfo();
        if (!backend.writable()) {
            return new SaveResult(false, backend.problem(), backend.name());
        }

        try {
            applyLegacyValues(spell, settings);
            saveLegacySpec();
            invalidateSpellCache(spell);
            invalidateRegistryCache();
            return new SaveResult(true, null, backend.name());
        } catch (Exception exception) {
            MageAdditions.LOGGER.error("Failed to apply Iron's live spell config for {}", spell.getSpellId(), exception);
            return new SaveResult(false, rootMessage(exception), backend.name());
        }
    }

    /**
     * Applies server-authoritative values to this process only. This is used by
     * the clientbound runtime-sync packet so dedicated-server clients immediately
     * use the same max levels, rarities, mana/power multipliers and cooldowns.
     * No config file is written.
     */
    public static SaveResult applyRuntime(AbstractSpell spell, Settings settings) {
        Map<AbstractSpell, Settings> single = new LinkedHashMap<>();
        single.put(spell, settings);
        return applyRuntimeBatch(single);
    }

    /**
     * Batch form used when a player joins. Registry-wide caches are invalidated
     * once after every spell has been updated instead of once per network entry.
     */
    public static SaveResult applyRuntimeBatch(Map<AbstractSpell, Settings> updates) {
        BackendInfo backend = backendInfo();
        if (!backend.writable()) {
            return new SaveResult(false, backend.problem(), backend.name());
        }

        try {
            for (Map.Entry<AbstractSpell, Settings> entry : updates.entrySet()) {
                applyLegacyValues(entry.getKey(), entry.getValue());
                invalidateSpellCache(entry.getKey());
            }
            invalidateRegistryCache();
            return new SaveResult(true, null, backend.name());
        } catch (Exception exception) {
            MageAdditions.LOGGER.error("Failed to apply Iron's runtime spell config batch", exception);
            return new SaveResult(false, rootMessage(exception), backend.name());
        }
    }

    private static void applyLegacyValues(AbstractSpell spell, Settings settings) throws Exception {
        Object parameters = legacySpellParameters(spell);
        if (parameters == null) {
            throw new IllegalStateException("Iron's did not expose config parameters for " + spell.getSpellId());
        }

        Map<String, Object> values = discoverConfigValues(parameters);
        setConfigValue(values, KEY_ENABLED, settings.enabled());
        setConfigValue(values, KEY_SCHOOL, settings.school().toString());
        setConfigValue(values, KEY_MAX_LEVEL, settings.maxLevel());
        setConfigValue(values, KEY_MIN_RARITY, settings.minRarity());
        setConfigValue(values, KEY_MANA_MULTIPLIER, settings.manaMultiplier());
        setConfigValue(values, KEY_POWER_MULTIPLIER, settings.powerMultiplier());
        setConfigValue(values, KEY_COOLDOWN_SECONDS, settings.cooldownSeconds());
        setConfigValue(values, KEY_ALLOW_CRAFTING, settings.allowCrafting());
    }

    private static Settings fallbackRead(AbstractSpell spell) {
        Settings defaults = defaults(spell);
        try {
            int rarityIndex = Math.max(0, Math.min(SpellRarity.values().length - 1, spell.getMinRarity()));
            return new Settings(
                    spell.isEnabled(),
                    spell.getSchoolType().getId(),
                    spell.getMaxLevel(),
                    SpellRarity.values()[rarityIndex],
                    defaults.manaMultiplier(),
                    defaults.powerMultiplier(),
                    spell.getSpellCooldown() / 20.0,
                    spell.allowCrafting()
            );
        } catch (Exception ignored) {
            return defaults;
        }
    }

    private static Object legacySpellParameters(AbstractSpell spell) throws Exception {
        Class<?> serverConfigs = Class.forName(LEGACY_SERVER_CONFIGS, true, IronsSpellConfigBridge.class.getClassLoader());
        Method getter = serverConfigs.getMethod("getSpellConfig", AbstractSpell.class);
        return getter.invoke(null, spell);
    }

    /**
     * Discovers ConfigValues by their stable config-path leaf (e.g. MaxLevel)
     * rather than by Iron's private Java field name. This survives private-field
     * renames within the legacy config implementation.
     */
    private static Map<String, Object> discoverConfigValues(Object parameters) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Field field : parameters.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(parameters);
            if (value == null) {
                continue;
            }

            Method getPath = findMethod(value.getClass(), "getPath", 0);
            if (getPath == null) {
                continue;
            }

            Object rawPath = getPath.invoke(value);
            if (rawPath instanceof List<?> path && !path.isEmpty()) {
                String leaf = String.valueOf(path.get(path.size() - 1));
                result.put(leaf, value);
            }
        }

        return result;
    }

    private static void setConfigValue(Map<String, Object> values, String key, Object value) throws Exception {
        Object configValue = values.get(key);
        if (configValue == null) {
            throw new IllegalStateException("Iron's config entry '" + key + "' was not found");
        }

        Method set = findMethod(configValue.getClass(), "set", 1);
        Method clearCache = findMethod(configValue.getClass(), "clearCache", 0);
        if (set == null || clearCache == null) {
            throw new IllegalStateException("Iron's config entry '" + key + "' is not a mutable NeoForge ConfigValue");
        }

        set.invoke(configValue, value);

        // ConfigValue#get() is cached in NeoForge. set() updates non-restart
        // values in current 1.21.1 NeoForge, but clear explicitly as a defensive
        // compatibility step before Iron's immediately re-reads the value.
        clearCache.invoke(configValue);
    }

    private static void saveLegacySpec() throws Exception {
        Class<?> serverConfigs = Class.forName(LEGACY_SERVER_CONFIGS, true, IronsSpellConfigBridge.class.getClassLoader());
        Field specField = serverConfigs.getField("SPEC");
        Object spec = specField.get(null);
        Method save = findMethod(spec.getClass(), "save", 0);
        if (save == null) {
            throw new IllegalStateException("Iron's legacy config spec has no save() method");
        }
        save.invoke(spec);
    }

    private static void invalidateSpellCache(AbstractSpell spell) {
        // 3.15+ exposes resetRarityWeights(); 3.14.x keeps the field private.
        try {
            Method reset = findMethod(spell.getClass(), "resetRarityWeights", 0);
            if (reset != null) {
                reset.invoke(spell);
            } else {
                Field rarityWeights = AbstractSpell.class.getDeclaredField("rarityWeights");
                rarityWeights.setAccessible(true);
                rarityWeights.set(spell, null);
            }
        } catch (ReflectiveOperationException exception) {
            MageAdditions.LOGGER.debug("Could not invalidate Iron's rarity cache for {}", spell.getSpellId(), exception);
        }
    }

    private static void invalidateRegistryCache() {
        // 3.14.x caches spell lists by school in SpellRegistry. Call this only
        // when the method exists so a future removal does not become a hard link.
        try {
            Method registryReload = findMethod(SpellRegistry.class, "onConfigReload", 0);
            if (registryReload != null) {
                registryReload.invoke(null);
            }
        } catch (ReflectiveOperationException exception) {
            MageAdditions.LOGGER.debug("Could not invalidate Iron's school cache", exception);
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = findMethod(target.getClass(), methodName, 0);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName + "()");
        }
        return method.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public record Settings(
            boolean enabled,
            ResourceLocation school,
            int maxLevel,
            SpellRarity minRarity,
            double manaMultiplier,
            double powerMultiplier,
            double cooldownSeconds,
            boolean allowCrafting
    ) {}

    public record SaveResult(boolean success, String error, String backendName) {}

    public record BackendInfo(boolean writable, String name, String problem) {}
}
