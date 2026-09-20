package net.fireboy.mageadditions.client.screen;

import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-lobby overrides. Nothing here changes the mode's stored defaults. */
public final class MinigameSettingsScreen extends Screen {
    private final MinigameSetupScreen parent;
    private final MinigameDefinition game;
    private MinigameSettings working;
    private EditBox durationBox;
    private EditBox initialBorderBox;
    private EditBox finalBorderBox;
    private Button teleportButton;
    private Button kitButton;

    public MinigameSettingsScreen(MinigameSetupScreen parent, MinigameDefinition game, MinigameSettings current) {
        super(Component.translatable("screen.mageadditions.settings.title", game.displayName()));
        this.parent = parent;
        this.game = game;
        this.working = current;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldX = center - 15;
        int y = 68;

        durationBox = numericBox(fieldX, y, formatMinutes(working.durationSeconds()));
        y += 32;
        initialBorderBox = numericBox(fieldX, y, trimNumber(working.initialBorderSize()));
        y += 32;
        finalBorderBox = numericBox(fieldX, y, trimNumber(working.finalBorderSize()));
        y += 38;

        teleportButton = addRenderableWidget(Button.builder(teleportLabel(), b -> {
            working = new MinigameSettings(
                working.durationSeconds(), working.initialBorderSize(), working.finalBorderSize(), !working.randomTeleport(), working.kitPreset()
            );
            teleportButton.setMessage(teleportLabel());
        }).bounds(center - 110, y, 220, 22).build());

        y += 30;
        kitButton = addRenderableWidget(Button.builder(kitLabel(), b -> {
            working = new MinigameSettings(
                working.durationSeconds(), working.initialBorderSize(), working.finalBorderSize(), working.randomTeleport(), working.kitPreset().next()
            );
            kitButton.setMessage(kitLabel());
        }).bounds(center - 110, y, 220, 22).build());

        y += 38;
        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.settings.reset"), b -> resetDefaults())
            .bounds(center - 110, y, 106, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> saveAndClose())
            .bounds(center + 4, y, 106, 22).build());

    }

    private EditBox numericBox(int x, int y, String value) {
        EditBox box = new EditBox(font, x, y, 150, 22, Component.empty());
        box.setMaxLength(12);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9]*\\.?[0-9]*"));
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void resetDefaults() {
        working = MinigameSettings.defaults(game);
        durationBox.setValue(formatMinutes(working.durationSeconds()));
        initialBorderBox.setValue(trimNumber(working.initialBorderSize()));
        finalBorderBox.setValue(trimNumber(working.finalBorderSize()));
        teleportButton.setMessage(teleportLabel());
        kitButton.setMessage(kitLabel());
    }

    private void saveAndClose() {
        double minutes = parseDouble(durationBox.getValue(), working.durationSeconds() / 60.0);
        double initial = parseDouble(initialBorderBox.getValue(), working.initialBorderSize());
        double ending = parseDouble(finalBorderBox.getValue(), working.finalBorderSize());
        working = new MinigameSettings(
            (int) Math.round(minutes * 60.0),
            initial,
            ending,
            working.randomTeleport(),
            working.kitPreset()
        ).validated();
        parent.setSettings(working);
        Minecraft.getInstance().setScreen(parent);
    }

    private Component teleportLabel() {
        return Component.translatable(
            working.randomTeleport() ? "screen.mageadditions.settings.random_spawn.on" : "screen.mageadditions.settings.random_spawn.off"
        );
    }

    private Component kitLabel() {
        return Component.translatable("screen.mageadditions.settings.kit", Component.translatable(working.kitPreset().translationKey()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int labelX = center - 170;
        graphics.drawCenteredString(font, title, center, 22, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.settings.subtitle"), center, 42, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.mageadditions.settings.duration"), labelX, 75, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.settings.initial_border"), labelX, 107, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.settings.final_border"), labelX, 139, 0xFFFFFF, false);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.settings.note"), center, height - 26, 0x808080);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatMinutes(int seconds) {
        if (seconds <= 0) {
            return "0";
        }
        double minutes = seconds / 60.0;
        return Math.rint(minutes) == minutes ? Integer.toString((int) minutes) : String.format("%.1f", minutes);
    }

    private static String trimNumber(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : Double.toString(value);
    }
}
