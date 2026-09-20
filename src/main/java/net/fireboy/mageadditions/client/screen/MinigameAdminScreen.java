package net.fireboy.mageadditions.client.screen;

import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** First host screen: pick the ruleset, then configure FFA/teams on the next page. */
public final class MinigameAdminScreen extends Screen {
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 22;

    public MinigameAdminScreen() {
        super(Component.translatable("screen.mageadditions.minigame_admin.title"));
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int y = Math.max(72, height / 2 - 78);

        for (MinigameDefinition game : MinigameRegistry.all()) {
            addRenderableWidget(Button.builder(game.displayName(), button -> configure(game))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += BUTTON_HEIGHT + 8;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(x + 40, Math.min(height - 34, y + 10), BUTTON_WIDTH - 80, 20)
            .build());
    }

    private void configure(MinigameDefinition game) {
        Minecraft.getInstance().setScreen(new MinigameSetupScreen(game));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, 24, 0xFFFFFF);
        graphics.drawCenteredString(
            font,
            Component.translatable("screen.mageadditions.minigame_admin.subtitle"),
            centerX,
            43,
            0xA0A0A0
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
