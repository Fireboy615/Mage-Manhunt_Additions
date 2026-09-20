package net.fireboy.mageadditions.minigame;

/** Per-lobby rule overrides. Defaults come from the selected minigame definition. */
public record MinigameSettings(
    int durationSeconds,
    double initialBorderSize,
    double finalBorderSize,
    boolean randomTeleport,
    KitPreset kitPreset
) {
    public static final int MAX_DURATION_SECONDS = 6 * 60 * 60;
    public static final double MAX_BORDER_SIZE = 60_000_000.0;

    public static MinigameSettings defaults(MinigameDefinition game) {
        return new MinigameSettings(
            game.durationSeconds(),
            game.initialBorderSize(),
            game.finalBorderSize(),
            game.randomTeleport(),
            KitPreset.MODE_DEFAULT
        );
    }

    public MinigameSettings validated() {
        int duration = Math.max(0, Math.min(durationSeconds, MAX_DURATION_SECONDS));
        double initial = clampBorder(initialBorderSize);
        double ending = clampBorder(finalBorderSize);
        KitPreset kit = kitPreset == null ? KitPreset.MODE_DEFAULT : kitPreset;
        return new MinigameSettings(duration, initial, ending, randomTeleport, kit);
    }

    private static double clampBorder(double value) {
        if (!Double.isFinite(value)) {
            return 33.0;
        }
        return Math.max(1.0, Math.min(value, MAX_BORDER_SIZE));
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
