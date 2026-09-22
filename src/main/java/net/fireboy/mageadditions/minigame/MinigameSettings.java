package net.fireboy.mageadditions.minigame;

/** Per-lobby rule overrides. Border values exposed by Mage Additions are radii, not vanilla diameters. */
public record MinigameSettings(
    int durationSeconds,
    double initialBorderSize,
    double finalBorderSize,
    boolean randomTeleport,
    KitPreset kitPreset,
    String customEquipmentPreset
) {
    public static final int MAX_DURATION_SECONDS = 6 * 60 * 60;
    public static final double MAX_BORDER_RADIUS = 30_000_000.0;

    public static MinigameSettings defaults(MinigameDefinition game) {
        return new MinigameSettings(
            game.durationSeconds(),
            game.initialBorderSize(),
            game.finalBorderSize(),
            game.randomTeleport(),
            KitPreset.MODE_DEFAULT,
            ""
        );
    }

    public MinigameSettings validated() {
        int duration = Math.max(0, Math.min(durationSeconds, MAX_DURATION_SECONDS));
        double initial = clampBorderRadius(initialBorderSize);
        double ending = clampBorderRadius(finalBorderSize);
        KitPreset kit = kitPreset == null ? KitPreset.MODE_DEFAULT : kitPreset;
        String custom = EquipmentPresetStore.sanitizeName(customEquipmentPreset);
        return new MinigameSettings(duration, initial, ending, randomTeleport, kit, custom);
    }

    public boolean hasCustomEquipmentPreset() {
        return customEquipmentPreset != null && !customEquipmentPreset.isBlank();
    }

    private static double clampBorderRadius(double value) {
        if (!Double.isFinite(value)) {
            return 16.5;
        }
        return Math.max(0.5, Math.min(value, MAX_BORDER_RADIUS));
    }

    public enum KitPreset {
        MODE_DEFAULT("screen.mageadditions.settings.kit.default"),
        BLITZ("screen.mageadditions.settings.kit.blitz"),
        BUNDLE_ONLY("screen.mageadditions.settings.kit.bundle"),
        PRACTICE("screen.mageadditions.settings.kit.practice"),
        NONE("screen.mageadditions.settings.kit.none");

        private final String translationKey;

        KitPreset(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public KitPreset next() {
            KitPreset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static KitPreset fromOrdinal(int ordinal) {
            KitPreset[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MODE_DEFAULT;
        }
    }
}
