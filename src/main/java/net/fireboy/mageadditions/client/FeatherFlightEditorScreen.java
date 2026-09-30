package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.config.FeatherFlightConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Dedicated UI for Mage Additions' Feather Flight rework. */
public final class FeatherFlightEditorScreen extends Screen {
    private static final int FIELD_WIDTH = 160;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_GAP = 28;

    private final Screen parent;
    private FeatherFlightConfig config;
    private boolean readOnlyRemoteServer;
    private Component status = Component.empty();

    private Button enabledButton;
    private EditBox slowFallBox;
    private EditBox airAccelerationBox;
    private EditBox maxHorizontalSpeedBox;
    private EditBox extraJumpStrengthBox;
    private Button fallDamageButton;
    private Button saveButton;

    public FeatherFlightEditorScreen(Screen parent) {
        super(Component.literal("Feather Flight Rework Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.readOnlyRemoteServer = this.minecraft != null
                && this.minecraft.getConnection() != null
                && !this.minecraft.hasSingleplayerServer();
        this.config = MageAdditionsConfigEditor.readFeatherFlight();

        int left = this.width / 2 - 210;
        int fieldX = this.width / 2 + 50;
        int y = 62;

        this.enabledButton = addRenderableWidget(
                Button.builder(toggleLabel("Rework", this.config.enabled), button -> {
                    this.config.enabled = !this.config.enabled;
                    button.setMessage(toggleLabel("Rework", this.config.enabled));
                }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build()
        );
        y += ROW_GAP;

        this.slowFallBox = numericBox(fieldX, y, format(this.config.slow_fall_speed));
        y += ROW_GAP;
        this.airAccelerationBox = numericBox(fieldX, y, format(this.config.air_acceleration));
        y += ROW_GAP;
        this.maxHorizontalSpeedBox = numericBox(fieldX, y, format(this.config.max_horizontal_speed));
        y += ROW_GAP;
        this.extraJumpStrengthBox = numericBox(fieldX, y, format(this.config.extra_jump_strength));
        y += ROW_GAP;

        this.fallDamageButton = addRenderableWidget(
                Button.builder(toggleLabel("Fall damage immunity", this.config.fall_damage_immunity), button -> {
                    this.config.fall_damage_immunity = !this.config.fall_damage_immunity;
                    button.setMessage(toggleLabel("Fall damage immunity", this.config.fall_damage_immunity));
                }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build()
        );

        int buttonY = Math.min(this.height - 36, y + 46);
        this.saveButton = addRenderableWidget(
                Button.builder(Component.literal("Save"), button -> save())
                        .bounds(this.width / 2 - 164, buttonY, 158, FIELD_HEIGHT)
                        .build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), button -> onClose())
                        .bounds(this.width / 2 + 6, buttonY, 158, FIELD_HEIGHT)
                        .build()
        );

        setEditingEnabled(!this.readOnlyRemoteServer);
    }

    private EditBox numericBox(int x, int y, String value) {
        EditBox box = addRenderableWidget(
                new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty())
        );
        box.setMaxLength(32);
        box.setValue(value);
        return box;
    }

    private void setEditingEnabled(boolean enabled) {
        this.enabledButton.active = enabled;
        this.slowFallBox.active = enabled;
        this.airAccelerationBox.active = enabled;
        this.maxHorizontalSpeedBox.active = enabled;
        this.extraJumpStrengthBox.active = enabled;
        this.fallDamageButton.active = enabled;
        this.saveButton.active = enabled;
    }

    private void save() {
        try {
            this.config.slow_fall_speed = parseDouble(this.slowFallBox, "Slow-fall speed", 0.01D, 2.0D);
            this.config.air_acceleration = parseDouble(this.airAccelerationBox, "Air acceleration", 0.0D, 0.25D);
            this.config.max_horizontal_speed = parseDouble(this.maxHorizontalSpeedBox, "Max horizontal speed", 0.01D, 3.0D);
            this.config.extra_jump_strength = parseDouble(this.extraJumpStrengthBox, "Extra jump strength", 0.0D, 2.0D);

            CastTimeOverrides.ReloadResult result = MageAdditionsConfigEditor.saveFeatherFlight(this.config);
            if (!result.success()) {
                throw new IllegalStateException(
                        result.error() == null ? "Could not save Feather Flight config" : result.error()
                );
            }

            this.status = Component.literal("Saved.").withStyle(ChatFormatting.GREEN);
            if (this.minecraft != null) this.minecraft.setScreen(this.parent);
        } catch (Exception exception) {
            this.status = Component.literal(
                    exception.getMessage() == null ? "Save failed" : exception.getMessage()
            ).withStyle(ChatFormatting.RED);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Aeromancy Feather Flight rework"), this.width / 2, 30, 0x888888);

        int left = this.width / 2 - 210;
        int y = 68;
        drawLabel(graphics, left, y, "Enabled"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Slow-fall speed (downward)"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Air acceleration"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Max horizontal speed"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Extra jump strength"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Fall damage immunity");

        if (!CastTimeOverrides.spellReworksEnabled()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Spell Reworks module is OFF; these settings are stored but inactive.")
                            .withStyle(ChatFormatting.YELLOW),
                    this.width / 2,
                    this.height - 56,
                    0xFFFFFF
            );
        } else if (this.readOnlyRemoteServer) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Remote server: editor is read-only until server config networking is added.")
                            .withStyle(ChatFormatting.RED),
                    this.width / 2,
                    this.height - 56,
                    0xFFFFFF
            );
        } else if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 56, 0xFFFFFF);
        }
    }

    private void drawLabel(GuiGraphics graphics, int x, int y, String label) {
        graphics.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
    }

    private static Component toggleLabel(String label, boolean value) {
        return Component.literal(label + ": ").append(
                Component.literal(value ? "ON" : "OFF")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)
        );
    }

    private static double parseDouble(EditBox box, String label, double min, double max) {
        try {
            double value = Double.parseDouble(box.getValue().trim());
            if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    label + " must be between " + format(min) + " and " + format(max) + "."
            );
        }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
