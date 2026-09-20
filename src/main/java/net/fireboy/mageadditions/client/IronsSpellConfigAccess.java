package net.fireboy.mageadditions.client;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.compat.irons.IronsSpellConfigBridge;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-facing facade for the version-isolated Iron's config bridge.
 *
 * Keeping this facade means the spell screens do not need to know which Iron's
 * config backend is in use. The backend can change when Iron's is upgraded.
 */
public final class IronsSpellConfigAccess {
    private IronsSpellConfigAccess() {}

    public static Settings read(AbstractSpell spell) {
        return fromBridge(IronsSpellConfigBridge.read(spell));
    }

    public static Settings defaults(AbstractSpell spell) {
        return fromBridge(IronsSpellConfigBridge.defaults(spell));
    }

    /**
     * Local fallback. Normal in-world saves now go through the server network
     * path, but keeping this method makes the facade useful for tests/tools.
     */
    public static SaveResult save(AbstractSpell spell, Settings settings) {
        IronsSpellConfigBridge.SaveResult result = IronsSpellConfigBridge.saveLive(spell, toBridge(settings));
        return new SaveResult(result.success(), result.error(), result.backendName());
    }

    public static String backendName() {
        return IronsSpellConfigBridge.backendInfo().name();
    }

    public static boolean writableBackend() {
        return IronsSpellConfigBridge.backendInfo().writable();
    }

    private static Settings fromBridge(IronsSpellConfigBridge.Settings settings) {
        return new Settings(
                settings.enabled(),
                settings.school(),
                settings.maxLevel(),
                settings.minRarity(),
                settings.manaMultiplier(),
                settings.powerMultiplier(),
                settings.cooldownSeconds(),
                settings.allowCrafting()
        );
    }

    private static IronsSpellConfigBridge.Settings toBridge(Settings settings) {
        return new IronsSpellConfigBridge.Settings(
                settings.enabled(),
                settings.school(),
                settings.maxLevel(),
                settings.minRarity(),
                settings.manaMultiplier(),
                settings.powerMultiplier(),
                settings.cooldownSeconds(),
                settings.allowCrafting()
        );
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
}
