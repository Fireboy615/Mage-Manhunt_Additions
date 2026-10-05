package net.fireboy.mageadditions.server.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fireboy.mageadditions.MageAdditions;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class DomainConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("mage_additions_domain.json");

    private static volatile Settings settings = new Settings();

    private DomainConfig() {
    }

    public static void reload() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.notExists(CONFIG_PATH)) {
                Files.writeString(
                        CONFIG_PATH,
                        GSON.toJson(new Settings()),
                        StandardCharsets.UTF_8
                );
            }

            Settings loaded = GSON.fromJson(
                    Files.readString(CONFIG_PATH, StandardCharsets.UTF_8),
                    Settings.class
            );
            if (loaded == null) {
                loaded = new Settings();
            }

            loaded.base_size = Math.max(8, Math.min(256, loaded.base_size));
            loaded.size_per_level = Math.max(-64, Math.min(64, loaded.size_per_level));
            loaded.glow_seconds = Math.max(0, Math.min(60, loaded.glow_seconds));
            loaded.blocks_per_tick = Math.max(50, Math.min(10_000, loaded.blocks_per_tick));
            loaded.restores_per_tick = Math.max(50, Math.min(10_000, loaded.restores_per_tick));
            settings = loaded;

            // Rewrite the normalized config so older files that only had a
            // fixed `radius` field automatically expose the new level-scaling
            // size controls after one reload/startup.
            Files.writeString(
                    CONFIG_PATH,
                    GSON.toJson(settings),
                    StandardCharsets.UTF_8
            );

            MageAdditions.LOGGER.info(
                    "Loaded Domain config: shape={}, sizeMode={}, baseSize={}, sizePerLevel={}, L1 radius={}, L5 radius={}, glow={}s, build/tick={}, restore/tick={}",
                    shape(),
                    sizeMode(),
                    baseSize(),
                    sizePerLevel(),
                    radius(1),
                    radius(5),
                    glowSeconds(),
                    blocksPerTick(),
                    restoresPerTick()
            );
        } catch (Exception exception) {
            MageAdditions.LOGGER.error(
                    "Failed to load {}. Keeping previous Domain settings.",
                    CONFIG_PATH,
                    exception
            );
        }
    }

    public static DomainShape shape() {
        try {
            return DomainShape.valueOf(
                    settings.shape.trim().toUpperCase(Locale.ROOT)
            );
        } catch (Exception ignored) {
            return DomainShape.DOME;
        }
    }

    public static SizeMode sizeMode() {
        try {
            return SizeMode.valueOf(
                    settings.size_mode.trim().toUpperCase(Locale.ROOT)
            );
        } catch (Exception ignored) {
            return SizeMode.RADIUS;
        }
    }

    public static int baseSize() {
        return settings.base_size;
    }

    public static int sizePerLevel() {
        return settings.size_per_level;
    }

    /**
     * Configured size before conversion to radius.
     *
     * Example defaults in RADIUS mode:
     * L1 = 30, L2 = 35, L3 = 40, L4 = 45, L5 = 50.
     */
    public static int configuredSize(int spellLevel) {
        int level = Math.max(1, Math.min(5, spellLevel));
        int raw = settings.base_size + settings.size_per_level * (level - 1);
        int max = sizeMode() == SizeMode.DIAMETER ? 256 : 128;
        return Math.max(8, Math.min(max, raw));
    }

    public static int radius(int spellLevel) {
        int size = configuredSize(spellLevel);
        return sizeMode() == SizeMode.DIAMETER
                ? Math.max(4, (int) Math.round(size / 2.0D))
                : size;
    }

    public static int diameter(int spellLevel) {
        return radius(spellLevel) * 2;
    }

    public static int glowSeconds() {
        return settings.glow_seconds;
    }

    public static int glowTicks() {
        return glowSeconds() * 20;
    }

    public static int blocksPerTick() {
        return settings.blocks_per_tick;
    }

    public static int restoresPerTick() {
        return settings.restores_per_tick;
    }

    public static int durationTicks(int spellLevel) {
        int level = Math.max(1, Math.min(5, spellLevel));
        return (level + 2) * 60 * 20;
    }

    public enum SizeMode {
        RADIUS,
        DIAMETER
    }

    public static final class Settings {
        public String shape = "DOME";

        /**
         * RADIUS = base_size/size_per_level describe radius.
         * DIAMETER = they describe full width instead.
         */
        public String size_mode = "RADIUS";

        /**
         * Level 1 size. Default 30 radius = 60 block diameter.
         */
        public int base_size = 30;

        /**
         * Added once per spell level above level 1.
         * Default +5 radius per level gives L5 = 50 radius.
         */
        public int size_per_level = 5;

        public int glow_seconds = 5;
        public int blocks_per_tick = 1200;
        public int restores_per_tick = 2000;
    }
}
