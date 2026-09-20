package net.fireboy.mageadditions.client.screen;

import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.network.payload.ContinueMinigamePayload;
import net.fireboy.mageadditions.network.payload.OpenMatchControlPayload;
import net.fireboy.mageadditions.network.payload.PauseMinigamePayload;
import net.fireboy.mageadditions.network.payload.RevivePlayerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** OP-only controls available while a match is running or paused. */
public final class MatchControlScreen extends Screen {
    private static final int BUTTON_WIDTH = 240;
    private final OpenMatchControlPayload state;

    public MatchControlScreen(OpenMatchControlPayload state) {
        super(Component.translatable("screen.mageadditions.match_control.title"));
        this.state = state;
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int y = 92;

        if (state.paused()) {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.mageadditions.match_control.continue"),
                    button -> PacketDistributor.sendToServer(ContinueMinigamePayload.INSTANCE)
            ).bounds(x, y, BUTTON_WIDTH, 24).build());
        } else {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.mageadditions.match_control.pause"),
                    button -> PacketDistributor.sendToServer(PauseMinigamePayload.INSTANCE)
            ).bounds(x, y, BUTTON_WIDTH, 24).build());
        }

        y += 44;
        int shown = 0;
        for (OpenMatchControlPayload.DeadPlayer deadPlayer : state.deadPlayers()) {
            if (shown >= 8 || y > height - 58) {
                break;
            }
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.mageadditions.match_control.revive_player", deadPlayer.name()),
                    button -> PacketDistributor.sendToServer(new RevivePlayerPayload(deadPlayer.uuid()))
            ).bounds(x, y, BUTTON_WIDTH, 22).build());
            y += 26;
            shown++;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 70, height - 34, 140, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        MinigameDefinition game = MinigameRegistry.get(state.gameId());
        Component gameName = game == null ? Component.literal(state.gameId().toString()) : game.displayName();
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFF);
        graphics.drawCenteredString(font, gameName, width / 2, 42, 0xFFD966);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        state.paused() ? "screen.mageadditions.match_control.status.paused" : "screen.mageadditions.match_control.status.running",
                        formatTime(state.secondsRemaining()),
                        (int) Math.round(state.currentBorderSize())
                ),
                width / 2,
                60,
                state.paused() ? 0xFFCC55 : 0x77FF77
        );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "screen.mageadditions.match_control.connected",
                        state.onlineParticipants(),
                        state.totalParticipants()
                ),
                width / 2,
                78,
                state.onlineParticipants() >= state.totalParticipants() ? 0x77FF77 : 0xFFD966
        );

        graphics.drawCenteredString(
                font,
                Component.translatable("screen.mageadditions.match_control.revive_header"),
                width / 2,
                124,
                0xA0D8FF
        );
        if (state.deadPlayers().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.mageadditions.match_control.no_dead_players"),
                    width / 2,
                    145,
                    0xA0A0A0
            );
        }
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
