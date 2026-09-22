package net.fireboy.mageadditions.client.screen;

import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.network.payload.AdminAssignTeamPayload;
import net.fireboy.mageadditions.network.payload.CancelMinigamePayload;
import net.fireboy.mageadditions.network.payload.ContinueMinigamePayload;
import net.fireboy.mageadditions.network.payload.OpenMatchControlPayload;
import net.fireboy.mageadditions.network.payload.PauseMinigamePayload;
import net.fireboy.mageadditions.network.payload.RequestMatchControlRefreshPayload;
import net.fireboy.mageadditions.network.payload.RevivePlayerPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** OP-only live match dashboard. State refreshes while the screen stays open. */
public final class MatchControlScreen extends Screen {
    private OpenMatchControlPayload state;
    private int refreshTicks;

    public MatchControlScreen(OpenMatchControlPayload state) {
        super(Component.translatable("screen.mageadditions.match_control.title"));
        this.state = state;
    }

    public void applyState(OpenMatchControlPayload next) {
        boolean rebuild = state == null
            || state.paused() != next.paused()
            || state.teamsEnabled() != next.teamsEnabled()
            || state.teamCount() != next.teamCount()
            || !state.deadPlayers().equals(next.deadPlayers())
            || !state.players().equals(next.players());
        this.state = next;
        if (rebuild) rebuildWidgets();
    }

    @Override
    protected void init() {
        Layout l = layout();
        int actionX = l.right + 12;
        int actionW = l.rightW - 24;
        int y = l.top + 40;

        if (state.paused()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.match_control.continue"), b ->
                PacketDistributor.sendToServer(ContinueMinigamePayload.INSTANCE)
            ).bounds(actionX, y, actionW, 24).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.match_control.pause"), b ->
                PacketDistributor.sendToServer(PauseMinigamePayload.INSTANCE)
            ).bounds(actionX, y, actionW, 24).build());
        }
        y += 32;
        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.match_control.cancel").withStyle(ChatFormatting.RED), b -> {
            PacketDistributor.sendToServer(CancelMinigamePayload.INSTANCE);
            onClose();
        }).bounds(actionX, y, actionW, 22).build());

        y += 42;
        int maxRevives = Math.max(0, (l.bottom - y - 26) / 24);
        for (int i = 0; i < Math.min(maxRevives, state.deadPlayers().size()); i++) {
            OpenMatchControlPayload.DeadPlayer dead = state.deadPlayers().get(i);
            int rowY = y + i * 24;
            addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.match_control.revive_player", dead.name()), b ->
                PacketDistributor.sendToServer(new RevivePlayerPayload(dead.uuid()))
            ).bounds(actionX, rowY, actionW, 20).build());
        }

        if (state.teamsEnabled()) {
            int teamY = l.top + 100;
            int maxRows = Math.max(0, (l.bottom - teamY - 8) / 24);
            for (int i = 0; i < Math.min(maxRows, state.players().size()); i++) {
                OpenMatchControlPayload.PlayerTeamEntry entry = state.players().get(i);
                int rowY = teamY + i * 24;
                int teamW = Math.min(128, l.leftW / 3);
                addRenderableWidget(Button.builder(teamName(entry.teamId()), b ->
                    PacketDistributor.sendToServer(new AdminAssignTeamPayload(entry.uuid(), nextTeam(entry.teamId())))
                ).bounds(l.left + l.leftW - teamW - 10, rowY - 5, teamW, 20).build());
            }
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 70, height - 27, 140, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        refreshTicks++;
        if (refreshTicks >= 10) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(RequestMatchControlRefreshPayload.INSTANCE);
        }
    }

    private ResourceLocation nextTeam(ResourceLocation current) {
        MinigameDefinition game = MinigameRegistry.get(state.gameId());
        if (game == null || state.teamCount() <= 0) return MinigameRegistry.FFA_TEAM_ID;
        int count = Math.min(state.teamCount(), game.teams().size());
        for (int i = 0; i < count; i++) {
            if (game.teams().get(i).id().equals(current)) return game.teams().get((i + 1) % count).id();
        }
        return game.teams().get(0).id();
    }

    private Component teamName(ResourceLocation teamId) {
        MinigameDefinition game = MinigameRegistry.get(state.gameId());
        if (game == null || teamId.equals(MinigameRegistry.FFA_TEAM_ID)) return Component.literal("FFA");
        MinigameDefinition.TeamDefinition team = game.team(teamId);
        return team == null ? Component.literal(teamId.getPath()) : team.displayName();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout l = layout();
        graphics.fill(l.left, l.top, l.left + l.leftW, l.bottom, 0x76000000);
        graphics.fill(l.right, l.top, l.right + l.rightW, l.bottom, 0x76000000);

        MinigameDefinition game = MinigameRegistry.get(state.gameId());
        Component gameName = game == null ? Component.literal(state.gameId().toString()) : game.displayName();
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, gameName, width / 2, 29, 0xFFD966);

        int x = l.left + 12;
        int y = l.top + 12;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.live_info"), x, y, 0x7FDBFF, false);
        y += 22;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.state", state.paused() ? "PAUSED" : "RUNNING"), x, y, state.paused() ? 0xFFCC55 : 0x77FF77, false);
        y += 18;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.time", formatTime(state.secondsRemaining())), x, y, 0xFFFFFF, false);
        y += 18;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.radius_current", trimNumber(state.currentBorderRadius())), x, y, 0xFFFFFF, false);
        y += 18;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.radius_range", trimNumber(state.startBorderRadius()), trimNumber(state.endBorderRadius())), x, y, 0xA0A0A0, false);
        y += 18;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.connected", state.onlineParticipants(), state.totalParticipants()), x, y, state.onlineParticipants() >= state.totalParticipants() ? 0x77FF77 : 0xFFD966, false);

        int teamY = l.top + 100;
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.players"), x, teamY - 20, 0xFFFFFF, false);
        int maxRows = Math.max(0, (l.bottom - teamY - 8) / 24);
        for (int i = 0; i < Math.min(maxRows, state.players().size()); i++) {
            OpenMatchControlPayload.PlayerTeamEntry entry = state.players().get(i);
            int rowY = teamY + i * 24;
            int color = entry.dead() ? 0xFF7777 : 0xDDDDDD;
            graphics.drawString(font, entry.name(), x, rowY, color, false);
            if (!state.teamsEnabled()) {
                graphics.drawString(font, "FFA", l.left + l.leftW - 36, rowY, 0xA0A0A0, false);
            }
        }

        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.actions"), l.right + 12, l.top + 12, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.revive_header"), l.right + 12, l.top + 118, 0xA0D8FF, false);
        if (state.deadPlayers().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.mageadditions.match_control.no_dead_players"), l.right + 12, l.top + 140, 0xA0A0A0, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Layout layout() {
        int total = Math.min(860, width - 28);
        int gap = 16;
        int leftW = (total - gap) * 3 / 5;
        int rightW = total - gap - leftW;
        int left = (width - total) / 2;
        int right = left + leftW + gap;
        return new Layout(left, leftW, right, rightW, 48, height - 36);
    }

    private static String formatTime(int seconds) { return String.format("%d:%02d", seconds / 60, seconds % 60); }
    private static String trimNumber(double value) { return Math.rint(value) == value ? Long.toString((long)value) : String.format("%.1f", value); }
    @Override public boolean isPauseScreen() { return false; }

    private record Layout(int left, int leftW, int right, int rightW, int top, int bottom) {}
}
