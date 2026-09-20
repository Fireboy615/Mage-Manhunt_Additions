package net.fireboy.mageadditions.client.screen;

import java.util.List;
import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.fireboy.mageadditions.network.payload.StartMinigamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Host page for choosing lobby format and opening per-match rule overrides. */
public final class MinigameSetupScreen extends Screen {
    private static final int MAX_TEAMS = 8;

    private final MinigameDefinition game;
    private boolean teamsEnabled = true;
    private int teamCount = 2;
    private MinigameSettings settings;
    private Button formatButton;
    private Button minusButton;
    private Button plusButton;
    private Button teamCountButton;

    public MinigameSetupScreen(MinigameDefinition game) {
        super(Component.translatable("screen.mageadditions.minigame_setup.title", game.displayName()));
        this.game = game;
        this.settings = MinigameSettings.defaults(game);
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = Math.max(96, height / 2 - 62);

        formatButton = addRenderableWidget(Button.builder(formatLabel(), button -> {
            teamsEnabled = !teamsEnabled;
            refreshControls();
        }).bounds(center - 110, y, 220, 22).build());

        y += 32;
        minusButton = addRenderableWidget(Button.builder(Component.literal("−"), button -> {
            teamCount = Math.max(2, teamCount - 1);
            refreshControls();
        }).bounds(center - 110, y, 34, 22).build());

        teamCountButton = addRenderableWidget(Button.builder(teamCountLabel(), button -> {})
            .bounds(center - 68, y, 136, 22).build());
        teamCountButton.active = false;

        plusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            teamCount = Math.min(MAX_TEAMS, teamCount + 1);
            refreshControls();
        }).bounds(center + 76, y, 34, 22).build());

        y += 34;
        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.minigame_setup.game_settings"), button ->
            Minecraft.getInstance().setScreen(new MinigameSettingsScreen(this, game, settings))
        ).bounds(center - 110, y, 220, 22).build());

        y += 34;
        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.minigame_setup.open_lobby"), button -> openLobby())
            .bounds(center - 110, y, 220, 22).build());

        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> Minecraft.getInstance().setScreen(new MinigameAdminScreen()))
            .bounds(center - 80, y, 160, 20).build());

        refreshControls();
    }

    void setSettings(MinigameSettings settings) {
        this.settings = settings.validated();
    }

    private void refreshControls() {
        if (formatButton == null) {
            return;
        }
        formatButton.setMessage(formatLabel());
        teamCountButton.setMessage(teamCountLabel());
        minusButton.active = teamsEnabled && teamCount > 2;
        plusButton.active = teamsEnabled && teamCount < MAX_TEAMS;
        teamCountButton.visible = teamsEnabled;
        minusButton.visible = teamsEnabled;
        plusButton.visible = teamsEnabled;
    }

    private Component formatLabel() {
        return Component.translatable(
            teamsEnabled ? "screen.mageadditions.minigame_setup.format.teams" : "screen.mageadditions.minigame_setup.format.ffa"
        );
    }

    private Component teamCountLabel() {
        return Component.translatable("screen.mageadditions.minigame_setup.team_count", teamCount);
    }

    private void openLobby() {
        PacketDistributor.sendToServer(new StartMinigamePayload(game.id(), teamsEnabled, teamsEnabled ? teamCount : 0, settings));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, title, center, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, game.description(), center, 40, 0xC8C8C8);

        String duration = settings.durationSeconds() <= 0 ? "∞" : formatMinutes(settings.durationSeconds());
        graphics.drawCenteredString(
            font,
            Component.translatable(
                "screen.mageadditions.minigame_setup.summary",
                duration,
                (int) settings.initialBorderSize(),
                (int) settings.finalBorderSize()
            ),
            center,
            60,
            0xFFD966
        );

        int infoWidth = Math.min(600, this.width - 48);
        int y = 75;
        List<FormattedCharSequence> lines = font.split(Component.translatable("screen.mageadditions.minigame_setup.help"), infoWidth);
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, center, y, 0x909090);
            y += font.lineHeight + 1;
        }
    }

    private static String formatMinutes(int seconds) {
        if (seconds % 60 == 0) {
            return (seconds / 60) + " min";
        }
        return String.format("%.1f min", seconds / 60.0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
