package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.config.ArrowVolleyConfig;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Dedicated UI for Mage Additions' Arrow Volley cone rework. */
public final class ArrowVolleyEditorScreen extends Screen {
    private static final int FIELD_WIDTH = 178;
    private static final int FIELD_HEIGHT = 18;

    private final Screen parent;
    private ArrowVolleyConfig config;
    private boolean readOnlyRemoteServer;
    private Component status = Component.empty();

    private Button enabledButton;
    private EditBox coneAngleBox;
    private EditBox projectileSpeedBox;
    private EditBox damagePerLevelBox;
    private EditBox maxHitsBox;
    private EditBox closeRangeBox;
    private EditBox closeMultiplierBox;
    private EditBox fullDamageDistanceBox;
    private Button saveButton;

    public ArrowVolleyEditorScreen(Screen parent) {
        super(Component.literal("Arrow Volley Rework Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.readOnlyRemoteServer = this.minecraft != null
                && this.minecraft.getConnection() != null
                && !this.minecraft.hasSingleplayerServer();
        this.config = MageAdditionsConfigEditor.readArrowVolley();

        int leftX = this.width / 2 - 188;
        int rightX = this.width / 2 + 10;
        int top = 58;
        int row = 39;

        this.enabledButton = addRenderableWidget(
                Button.builder(toggleLabel("Rework", this.config.enabled), button -> {
                    this.config.enabled = !this.config.enabled;
                    button.setMessage(toggleLabel("Rework", this.config.enabled));
                }).bounds(leftX, top + 10, FIELD_WIDTH, FIELD_HEIGHT).build()
        );
        this.coneAngleBox = numericBox(leftX, top + row + 10, format(this.config.cone_angle_degrees));
        this.projectileSpeedBox = numericBox(leftX, top + row * 2 + 10, format(this.config.projectile_speed));
        this.damagePerLevelBox = numericBox(leftX, top + row * 3 + 10, format(this.config.damage_per_level));

        this.maxHitsBox = numericBox(rightX, top + 10, Integer.toString(this.config.max_hits_per_target));
        this.closeRangeBox = numericBox(rightX, top + row + 10, format(this.config.close_range_distance));
        this.closeMultiplierBox = numericBox(rightX, top + row * 2 + 10, format(this.config.close_range_damage_multiplier));
        this.fullDamageDistanceBox = numericBox(rightX, top + row * 3 + 10, format(this.config.full_damage_distance));

        int buttonY = Math.max(top + row * 4 + 8, this.height - 28);
        if (buttonY + FIELD_HEIGHT > this.height - 4) {
            buttonY = this.height - FIELD_HEIGHT - 4;
        }
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
        this.coneAngleBox.active = enabled;
        this.projectileSpeedBox.active = enabled;
        this.damagePerLevelBox.active = enabled;
        this.maxHitsBox.active = enabled;
        this.closeRangeBox.active = enabled;
        this.closeMultiplierBox.active = enabled;
        this.fullDamageDistanceBox.active = enabled;
        this.saveButton.active = enabled;
    }

    private void save() {
        try {
            this.config.cone_angle_degrees = parseDouble(this.coneAngleBox, "Cone angle", 1.0D, 120.0D);
            this.config.projectile_speed = parseDouble(this.projectileSpeedBox, "Projectile speed", 0.05D, 4.0D);
            this.config.damage_per_level = parseDouble(this.damagePerLevelBox, "Damage per level", 0.0D, 20.0D);
            this.config.max_hits_per_target = parseInt(this.maxHitsBox, "Max hits per target", 1, 100);
            this.config.close_range_distance = parseDouble(this.closeRangeBox, "Close range distance", 0.0D, 32.0D);
            this.config.close_range_damage_multiplier = parseDouble(this.closeMultiplierBox, "Close range multiplier", 0.0D, 1.0D);
            this.config.full_damage_distance = parseDouble(this.fullDamageDistanceBox, "Full damage distance", 0.01D, 64.0D);
            if (this.config.full_damage_distance <= this.config.close_range_distance) {
                throw new IllegalArgumentException("Full damage distance must be greater than close range distance.");
            }

            CastTimeOverrides.ReloadResult result = MageAdditionsConfigEditor.saveArrowVolley(this.config);
            if (!result.success()) {
                throw new IllegalStateException(
                        result.error() == null ? "Could not save Arrow Volley config" : result.error()
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

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.literal("Cone tuning + point-blank protection").withStyle(ChatFormatting.GRAY),
                this.width / 2,
                28,
                0xFFFFFF
        );

        int leftX = this.width / 2 - 188;
        int rightX = this.width / 2 + 10;
        int top = 58;
        int row = 39;

        drawLabel(graphics, leftX, top, "Enabled");
        drawLabel(graphics, leftX, top + row, "Cone angle (degrees)");
        drawLabel(graphics, leftX, top + row * 2, "Projectile speed");
        drawLabel(graphics, leftX, top + row * 3, "Extra damage per level");

        drawLabel(graphics, rightX, top, "Max hits per target");
        drawLabel(graphics, rightX, top + row, "Close range distance");
        drawLabel(graphics, rightX, top + row * 2, "Close range damage multiplier");
        drawLabel(graphics, rightX, top + row * 3, "Full damage distance");

        if (!CastTimeOverrides.spellReworksEnabled()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Spell Reworks module is OFF; settings are stored but inactive.")
                            .withStyle(ChatFormatting.YELLOW),
                    this.width / 2,
                    42,
                    0xFFFFFF
            );
        } else if (this.readOnlyRemoteServer) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Remote server: editor is read-only until server networking is added.")
                            .withStyle(ChatFormatting.RED),
                    this.width / 2,
                    42,
                    0xFFFFFF
            );
        } else if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, 42, 0xFFFFFF);
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

    private static int parseInt(EditBox box, String label, int min, int max) {
        try {
            int value = Integer.parseInt(box.getValue().trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
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
