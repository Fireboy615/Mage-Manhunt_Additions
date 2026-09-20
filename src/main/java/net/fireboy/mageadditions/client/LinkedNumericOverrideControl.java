package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.config.SpellOverrideConfigService;
import net.minecraft.client.gui.components.EditBox;

import java.util.Locale;

/**
 * Keeps an absolute value and a multiplier view of the same per-spell setting in sync.
 *
 * <p>The last valid field edited becomes authoritative. That distinction is persisted:
 * an absolute override keeps the same absolute value when an upstream spell changes,
 * while a multiplier override follows the upstream/base value.</p>
 */
public final class LinkedNumericOverrideControl {
    private static final double EPSILON = 0.0000001;

    public enum Authority {
        DEFAULT,
        ABSOLUTE,
        MULTIPLIER
    }

    private final EditBox valueBox;
    private final EditBox multiplierBox;
    private double originalValue;
    private final double minValue;
    private final double maxValue;
    private final double minMultiplier;
    private final double maxMultiplier;
    private final Runnable changedCallback;

    private boolean updating;
    private boolean externallyActive = true;
    private Authority authority = Authority.DEFAULT;
    private double lastMultiplierValue = 1.0;

    public LinkedNumericOverrideControl(
            EditBox valueBox,
            EditBox multiplierBox,
            double originalValue,
            double minValue,
            double maxValue,
            double minMultiplier,
            double maxMultiplier,
            Runnable changedCallback
    ) {
        this.valueBox = valueBox;
        this.multiplierBox = multiplierBox;
        this.originalValue = originalValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.minMultiplier = minMultiplier;
        this.maxMultiplier = maxMultiplier;
        this.changedCallback = changedCallback == null ? () -> {} : changedCallback;

        this.valueBox.setResponder(this::onValueEdited);
        this.multiplierBox.setResponder(this::onMultiplierEdited);
        resetToDefault();
    }

    public void applyState(String mode, double storedValue) {
        Authority next = switch (normalizeMode(mode)) {
            case "absolute" -> Authority.ABSOLUTE;
            case "multiplier" -> Authority.MULTIPLIER;
            default -> Authority.DEFAULT;
        };

        this.updating = true;
        try {
            this.authority = next;
            if (next == Authority.ABSOLUTE) {
                this.valueBox.setValue(format(storedValue));
                if (canUseMultiplier()) {
                    this.multiplierBox.setValue(format(storedValue / this.originalValue));
                } else {
                    this.multiplierBox.setValue("N/A");
                }
            } else if (next == Authority.MULTIPLIER) {
                double multiplier = storedValue;
                this.lastMultiplierValue = multiplier;
                this.multiplierBox.setValue(canUseMultiplier() ? format(multiplier) : "N/A");
                this.valueBox.setValue(format(this.originalValue * multiplier));
            } else {
                this.valueBox.setValue(format(this.originalValue));
                this.multiplierBox.setValue(canUseMultiplier() ? "1" : "N/A");
            }
        } finally {
            this.updating = false;
            refreshActiveState();
            this.changedCallback.run();
        }
    }

    /**
     * Updates the upstream/native value and then applies the persisted state.
     * Useful for properties such as target range whose original value is only
     * known after the authoritative server snapshot arrives.
     */
    public void applyStateWithOriginal(double originalValue, String mode, double storedValue) {
        if (!Double.isFinite(originalValue)) {
            originalValue = 0.0;
        }
        this.originalValue = Math.max(this.minValue, Math.min(this.maxValue, originalValue));
        applyState(mode, storedValue);
    }

    public void resetToDefault() {
        applyState("off", 0.0);
    }

    public SpellOverrideConfigService.RuleState toRuleState(String label) {
        if (this.authority == Authority.DEFAULT) {
            return SpellOverrideConfigService.RuleState.disabled();
        }

        if (this.authority == Authority.ABSOLUTE) {
            double value = parse(this.valueBox.getValue(), label + " value");
            requireRange(value, this.minValue, this.maxValue, label + " value");
            return new SpellOverrideConfigService.RuleState(true, "absolute", value);
        }

        if (!canUseMultiplier()) {
            return new SpellOverrideConfigService.RuleState(true, "multiplier", this.lastMultiplierValue);
        }
        double multiplier = parse(this.multiplierBox.getValue(), label + " multiplier");
        requireRange(multiplier, this.minMultiplier, this.maxMultiplier, label + " multiplier");
        double effectiveValue = this.originalValue * multiplier;
        requireRange(effectiveValue, this.minValue, this.maxValue, label + " calculated value");
        return new SpellOverrideConfigService.RuleState(true, "multiplier", multiplier);
    }

    public void setActive(boolean active) {
        this.externallyActive = active;
        refreshActiveState();
    }

    public void setVisible(boolean visible) {
        this.valueBox.visible = visible;
        this.multiplierBox.visible = visible;
    }

    public Authority authority() {
        return this.authority;
    }

    public double originalValue() {
        return this.originalValue;
    }

    public String authorityDisplayName() {
        return switch (this.authority) {
            case DEFAULT -> "Default / inherited";
            case ABSOLUTE -> "Absolute value";
            case MULTIPLIER -> "Multiplier";
        };
    }

    public boolean canUseMultiplier() {
        return Math.abs(this.originalValue) > EPSILON;
    }

    private void onValueEdited(String text) {
        if (this.updating) return;

        Double parsed = tryParse(text);
        if (parsed == null || parsed < this.minValue || parsed > this.maxValue) {
            return;
        }

        this.authority = Authority.ABSOLUTE;
        if (canUseMultiplier()) {
            this.updating = true;
            try {
                this.multiplierBox.setValue(format(parsed / this.originalValue));
            } finally {
                this.updating = false;
            }
        }
        refreshActiveState();
        this.changedCallback.run();
    }

    private void onMultiplierEdited(String text) {
        if (this.updating || !canUseMultiplier()) return;

        Double parsed = tryParse(text);
        if (parsed == null || parsed < this.minMultiplier || parsed > this.maxMultiplier) {
            return;
        }

        double value = this.originalValue * parsed;
        if (!Double.isFinite(value) || value < this.minValue || value > this.maxValue) {
            return;
        }

        this.authority = Authority.MULTIPLIER;
        this.lastMultiplierValue = parsed;
        this.updating = true;
        try {
            this.valueBox.setValue(format(value));
        } finally {
            this.updating = false;
        }
        refreshActiveState();
        this.changedCallback.run();
    }

    private void refreshActiveState() {
        this.valueBox.active = this.externallyActive;
        this.multiplierBox.active = this.externallyActive && canUseMultiplier();
    }

    private static Double tryParse(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double parse(String raw, String label) {
        Double value = tryParse(raw);
        if (value == null) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
        return value;
    }

    private static void requireRange(double value, double min, double max, String label) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + format(min) + " and " + format(max) + ".");
        }
    }

    private static String normalizeMode(String mode) {
        return mode == null ? "off" : mode.trim().toLowerCase(Locale.ROOT);
    }

    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
