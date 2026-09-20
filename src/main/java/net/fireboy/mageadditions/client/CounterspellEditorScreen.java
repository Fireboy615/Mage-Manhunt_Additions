package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.config.CounterspellConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Dedicated UI for Mage Additions' Counterspell rework settings. */
public final class CounterspellEditorScreen extends Screen {
    private static final int FIELD_WIDTH = 160;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_GAP = 27;

    private final Screen parent;
    private CounterspellConfig config;
    private boolean readOnlyRemoteServer;
    private Component status = Component.empty();

    private Button enabledButton;
    private Button modeButton;
    private Button losButton;
    private Button targetModeButton;
    private Button particlesButton;
    private Button saveButton;

    private EditBox castTimeBox;
    private EditBox rangeBox;
    private EditBox aimAssistBox;
    private EditBox angleBox;

    public CounterspellEditorScreen(Screen parent) {
        super(Component.literal("Counterspell Rework Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.readOnlyRemoteServer = this.minecraft != null
                && this.minecraft.getConnection() != null
                && !this.minecraft.hasSingleplayerServer();
        this.config = MageAdditionsConfigEditor.readCounterspell();

        int left = this.width / 2 - 210;
        int fieldX = this.width / 2 + 50;
        int y = 70;

        this.enabledButton = addRenderableWidget(Button.builder(toggleLabel("Rework", this.config.enabled), b -> {
            this.config.enabled = !this.config.enabled;
            b.setMessage(toggleLabel("Rework", this.config.enabled));
        }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += ROW_GAP;

        this.modeButton = addRenderableWidget(Button.builder(Component.literal("Mode: " + modeDisplay()), b -> {
            this.config.mode = nextMode(this.config.mode);
            b.setMessage(Component.literal("Mode: " + modeDisplay()));
            updateFieldStates();
        }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += ROW_GAP;

        this.castTimeBox = numericBox(fieldX, y, Integer.toString(this.config.cast_time_ticks)); y += ROW_GAP;
        this.rangeBox = numericBox(fieldX, y, format(this.config.range)); y += ROW_GAP;
        this.aimAssistBox = numericBox(fieldX, y, format(this.config.aim_assist)); y += ROW_GAP;
        this.angleBox = numericBox(fieldX, y, format(this.config.angle_degrees)); y += ROW_GAP;

        this.losButton = addRenderableWidget(Button.builder(toggleLabel("Line of sight", this.config.require_line_of_sight), b -> {
            this.config.require_line_of_sight = !this.config.require_line_of_sight;
            b.setMessage(toggleLabel("Line of sight", this.config.require_line_of_sight));
        }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += ROW_GAP;

        this.targetModeButton = addRenderableWidget(Button.builder(Component.literal("Cone target: " + targetModeDisplay()), b -> {
            this.config.target_mode = nextTargetMode(this.config.target_mode);
            b.setMessage(Component.literal("Cone target: " + targetModeDisplay()));
        }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += ROW_GAP;

        this.particlesButton = addRenderableWidget(Button.builder(toggleLabel("Debug particles", this.config.debug_particles), b -> {
            this.config.debug_particles = !this.config.debug_particles;
            b.setMessage(toggleLabel("Debug particles", this.config.debug_particles));
        }).bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());

        int bottom = this.height - 30;
        this.saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(this.width / 2 - 104, bottom, 100, FIELD_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width / 2 + 4, bottom, 100, FIELD_HEIGHT).build());

        updateFieldStates();
        setEditingEnabled(!this.readOnlyRemoteServer);
    }

    private EditBox numericBox(int x, int y, String value) {
        EditBox box = addRenderableWidget(new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty()));
        box.setMaxLength(32);
        box.setValue(value);
        return box;
    }

    private void setEditingEnabled(boolean enabled) {
        this.enabledButton.active = enabled;
        this.modeButton.active = enabled;
        this.castTimeBox.active = enabled;
        this.rangeBox.active = enabled;
        this.aimAssistBox.active = enabled;
        this.angleBox.active = enabled;
        this.losButton.active = enabled;
        this.targetModeButton.active = enabled;
        this.particlesButton.active = enabled;
        this.saveButton.active = enabled;
        updateFieldStates();
    }

    private void updateFieldStates() {
        if (this.config == null) return;
        boolean editable = !this.readOnlyRemoteServer;
        String mode = normalizeMode(this.config.mode);
        boolean cone = mode.equals("cone");
        boolean targetLock = mode.equals("targeted") || mode.equals("haste");

        if (this.castTimeBox != null) this.castTimeBox.active = editable && targetLock;
        if (this.aimAssistBox != null) this.aimAssistBox.active = editable && targetLock;
        if (this.angleBox != null) this.angleBox.active = editable && cone;
        if (this.targetModeButton != null) this.targetModeButton.active = editable && cone;
        if (this.particlesButton != null) this.particlesButton.active = editable && cone;
        if (this.losButton != null) this.losButton.active = editable;
        if (this.rangeBox != null) this.rangeBox.active = editable;
    }

    private void save() {
        try {
            this.config.cast_time_ticks = parseInt(this.castTimeBox, "Cast time", 0, 72_000);
            this.config.range = parseDouble(this.rangeBox, "Range", 0.0, 64.0);
            this.config.aim_assist = parseDouble(this.aimAssistBox, "Aim assist", 0.0, 3.0);
            this.config.angle_degrees = parseDouble(this.angleBox, "Cone angle", 1.0, 180.0);

            CastTimeOverrides.ReloadResult result = MageAdditionsConfigEditor.saveCounterspell(this.config);
            if (!result.success()) {
                throw new IllegalStateException(result.error() == null ? "Could not save Counterspell config" : result.error());
            }

            this.status = Component.literal("Saved.").withStyle(ChatFormatting.GREEN);
            if (this.minecraft != null) this.minecraft.setScreen(this.parent);
        } catch (Exception exception) {
            this.status = Component.literal(exception.getMessage() == null ? "Save failed" : exception.getMessage())
                    .withStyle(ChatFormatting.RED);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Mage Additions spell rework"), this.width / 2, 32, 0x888888);

        int left = this.width / 2 - 210;
        int y = 76;
        drawLabel(graphics, left, y, "Enabled"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Mode"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Cast time (ticks)"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Target / cone range"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Aim assist"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Cone angle (degrees)"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Require line of sight"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Cone target mode"); y += ROW_GAP;
        drawLabel(graphics, left, y, "Cone debug particles");

        int infoY = Math.min(this.height - 90, 320);
        graphics.drawWordWrap(
                this.font,
                Component.literal(modeHelp()).withStyle(ChatFormatting.GRAY),
                left,
                infoY,
                420,
                0xFFFFFF
        );

        if (!CastTimeOverrides.spellReworksEnabled()) {
            graphics.drawCenteredString(this.font, Component.literal("Spell Reworks module is OFF; these settings are stored but inactive.").withStyle(ChatFormatting.YELLOW), this.width / 2, this.height - 56, 0xFFFFFF);
        } else if (this.readOnlyRemoteServer) {
            graphics.drawCenteredString(this.font, Component.literal("Remote server: editor is read-only until server networking is added.").withStyle(ChatFormatting.RED), this.width / 2, this.height - 56, 0xFFFFFF);
        } else if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 56, 0xFFFFFF);
        }
    }

    private void drawLabel(GuiGraphics graphics, int x, int y, String label) {
        graphics.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
    }

    private String modeHelp() {
        return switch (normalizeMode(this.config.mode)) {
            case "haste" -> "Haste: lock one valid target, show a moving 3-block area, then Counterspell up to 5 valid targets in that area. Area radius and max targets are currently fixed to Iron's Haste-style values.";
            case "targeted" -> "Targeted: lock one valid entity, charge for the configured cast time, then Counterspell that same entity.";
            default -> "Cone: instant forward area Counterspell. Range, cone angle, line of sight, target mode and debug particles apply.";
        };
    }

    private String modeDisplay() {
        return switch (normalizeMode(this.config.mode)) {
            case "haste" -> "Haste";
            case "targeted" -> "Targeted";
            default -> "Cone";
        };
    }

    private String targetModeDisplay() {
        return switch (normalizeTargetMode(this.config.target_mode)) {
            case "nearest" -> "Nearest";
            case "crosshair" -> "Crosshair";
            default -> "All";
        };
    }

    private static String nextMode(String current) {
        return switch (normalizeMode(current)) {
            case "cone" -> "targeted";
            case "targeted" -> "haste";
            default -> "cone";
        };
    }

    private static String nextTargetMode(String current) {
        return switch (normalizeTargetMode(current)) {
            case "all" -> "nearest";
            case "nearest" -> "crosshair";
            default -> "all";
        };
    }

    private static String normalizeMode(String value) {
        if (value == null) return "cone";
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("targeted") || lower.equals("haste") ? lower : "cone";
    }

    private static String normalizeTargetMode(String value) {
        if (value == null) return "all";
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("nearest") || lower.equals("crosshair") ? lower : "all";
    }

    private static Component toggleLabel(String label, boolean value) {
        return Component.literal(label + ": ").append(
                Component.literal(value ? "ON" : "OFF")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)
        );
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

    private static double parseDouble(EditBox box, String label, double min, double max) {
        try {
            double value = Double.parseDouble(box.getValue().trim());
            if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be between " + format(min) + " and " + format(max) + ".");
        }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}
