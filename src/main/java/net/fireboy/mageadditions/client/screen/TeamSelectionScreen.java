package net.fireboy.mageadditions.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.fireboy.mageadditions.network.payload.AdminAssignTeamPayload;
import net.fireboy.mageadditions.network.payload.CancelMinigamePayload;
import net.fireboy.mageadditions.network.payload.LaunchMinigamePayload;
import net.fireboy.mageadditions.network.payload.LobbyStatePayload;
import net.fireboy.mageadditions.network.payload.RandomizeTeamsPayload;
import net.fireboy.mageadditions.network.payload.SelectTeamPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Live review/team page. OPs can directly reassign players and randomise teams. */
public final class TeamSelectionScreen extends Screen {
    private final MinigameDefinition game;
    private final boolean teamsEnabled;
    private final int teamCount;
    private final boolean canManage;
    private final MinigameSettings settings;

    private final List<LobbyStatePayload.RosterEntry> roster = new ArrayList<>();
    private ResourceLocation selectedTeam;
    private int onlinePlayers;
    private boolean allReady;
    private Button launchButton;

    public TeamSelectionScreen(MinigameDefinition game, boolean teamsEnabled, int teamCount, boolean canManage, MinigameSettings settings) {
        super(Component.translatable("screen.mageadditions.team_selection.title", game.displayName()));
        this.game = game;
        this.teamsEnabled = teamsEnabled;
        this.teamCount = Math.max(0, Math.min(teamCount, game.teams().size()));
        this.canManage = canManage;
        this.settings = settings;
    }

    @Override
    protected void init() {
        LobbyStatePayload cached = ClientMinigameState.lobbyState();
        if (cached != null && cached.gameId().equals(game.id())) {
            updateState(cached);
        }

        Layout layout = layout();
        int teamButtonY = layout.top + 38;
        if (teamsEnabled) {
            int cols = 2;
            int gap = 6;
            int buttonW = (layout.leftW - 20 - gap) / cols;
            for (int i = 0; i < teamCount; i++) {
                MinigameDefinition.TeamDefinition team = game.teams().get(i);
                int x = layout.left + 10 + (i % cols) * (buttonW + gap);
                int y = teamButtonY + (i / cols) * 26;
                Component label = team.id().equals(selectedTeam) ? Component.literal("✓ ").append(team.displayName()) : team.displayName();
                addRenderableWidget(Button.builder(label, b -> chooseTeam(team.id())).bounds(x, y, buttonW, 22).build());
            }
        }

        int rosterY = teamButtonY + (teamsEnabled ? ((teamCount + 1) / 2) * 26 + 18 : 8);
        int maxRows = Math.max(1, (layout.bottom - rosterY - 14) / 25);
        for (int i = 0; i < Math.min(roster.size(), maxRows); i++) {
            LobbyStatePayload.RosterEntry entry = roster.get(i);
            int y = rosterY + i * 25;
            if (canManage && teamsEnabled) {
                int teamW = Math.min(120, layout.leftW / 3);
                addRenderableWidget(Button.builder(teamName(entry.teamId()), b -> cyclePlayerTeam(entry))
                    .bounds(layout.left + layout.leftW - teamW - 10, y - 5, teamW, 20).build());
            }
        }

        if (canManage) {
            int cx = layout.centerX - 70;
            int cy = layout.top + 76;
            if (teamsEnabled) {
                addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.team_selection.randomise"), b ->
                    PacketDistributor.sendToServer(RandomizeTeamsPayload.INSTANCE)
                ).bounds(cx, cy, 140, 22).build());
                cy += 48;
            }
            launchButton = addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.team_selection.start_match"), b ->
                PacketDistributor.sendToServer(LaunchMinigamePayload.INSTANCE)
            ).bounds(cx, cy, 140, 24).build());
            launchButton.active = allReady;
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b ->
                PacketDistributor.sendToServer(CancelMinigamePayload.INSTANCE)
            ).bounds(cx, cy + 34, 140, 22).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.how_to_play"), b ->
            Minecraft.getInstance().setScreen(new HowToPlayScreen(this, game))
        ).bounds(layout.right + 10, layout.bottom - 32, Math.min(170, layout.rightW - 20), 22).build());
    }

    private void chooseTeam(ResourceLocation teamId) {
        PacketDistributor.sendToServer(new SelectTeamPayload(game.id(), teamId));
    }

    private void cyclePlayerTeam(LobbyStatePayload.RosterEntry entry) {
        ResourceLocation next = nextTeam(entry.teamId());
        PacketDistributor.sendToServer(new AdminAssignTeamPayload(entry.playerId(), next));
    }

    private ResourceLocation nextTeam(ResourceLocation current) {
        if (!teamsEnabled || teamCount <= 0) return MinigameRegistry.FFA_TEAM_ID;
        for (int i = 0; i < teamCount; i++) {
            if (game.teams().get(i).id().equals(current)) return game.teams().get((i + 1) % teamCount).id();
        }
        return game.teams().get(0).id();
    }

    public void applyState(LobbyStatePayload state) {
        if (!state.gameId().equals(game.id())) return;
        updateState(state);
        rebuildWidgets();
    }

    private void updateState(LobbyStatePayload state) {
        onlinePlayers = state.onlinePlayers();
        allReady = state.allReady();
        roster.clear();
        roster.addAll(state.roster());
        Minecraft minecraft = Minecraft.getInstance();
        String localName = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        selectedTeam = null;
        for (LobbyStatePayload.RosterEntry entry : roster) {
            if (entry.playerName().equals(localName)) {
                selectedTeam = entry.teamId().equals(MinigameRegistry.UNASSIGNED_TEAM_ID) ? null : entry.teamId();
                break;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        graphics.fill(layout.left, layout.top, layout.left + layout.leftW, layout.bottom, 0x76000000);
        graphics.fill(layout.right, layout.top, layout.right + layout.rightW, layout.bottom, 0x76000000);

        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.mageadditions.team_selection.teams_header"), layout.left + 10, layout.top + 10, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.team_selection.info_header"), layout.right + 10, layout.top + 10, 0xFFFFFF, false);

        int rosterY = layout.top + 38 + (teamsEnabled ? ((teamCount + 1) / 2) * 26 + 18 : 8);
        graphics.drawString(font, Component.translatable("screen.mageadditions.team_selection.ready_count", readyCount(), onlinePlayers), layout.left + 10, rosterY - 15, allReady ? 0x77FF77 : 0xFFD966, false);
        int maxRows = Math.max(1, (layout.bottom - rosterY - 14) / 25);
        for (int i = 0; i < Math.min(roster.size(), maxRows); i++) {
            LobbyStatePayload.RosterEntry entry = roster.get(i);
            int y = rosterY + i * 25;
            graphics.drawString(font, entry.playerName(), layout.left + 12, y, 0xE0E0E0, false);
            if (!canManage || !teamsEnabled) {
                graphics.drawString(font, teamName(entry.teamId()), layout.left + layout.leftW - 12 - font.width(teamName(entry.teamId())), y, 0xA0A0A0, false);
            }
        }

        int iy = layout.top + 38;
        iy = drawInfoLine(graphics, Component.translatable("screen.mageadditions.settings.duration"), settings.durationSeconds() <= 0 ? "∞" : formatTime(settings.durationSeconds()), layout.right + 12, iy);
        iy = drawInfoLine(graphics, Component.translatable("screen.mageadditions.settings.start_radius"), trimNumber(settings.initialBorderSize()), layout.right + 12, iy);
        iy = drawInfoLine(graphics, Component.translatable("screen.mageadditions.settings.end_radius"), trimNumber(settings.finalBorderSize()), layout.right + 12, iy);
        iy = drawInfoLine(graphics, Component.translatable("screen.mageadditions.team_selection.spawn"), settings.randomTeleport() ? "Random" : "Current", layout.right + 12, iy);
        Component equipment = settings.hasCustomEquipmentPreset() ? Component.literal(settings.customEquipmentPreset()) : Component.translatable(settings.kitPreset().translationKey());
        graphics.drawString(font, Component.translatable("screen.mageadditions.team_selection.equipment_info", equipment), layout.right + 12, iy, 0xFFD966, false);
        iy += 28;
        drawWrapped(graphics, game.description(), layout.right + 12, iy, layout.rightW - 24, 0xB8B8B8);

        if (canManage) {
            graphics.drawCenteredString(font, Component.translatable(allReady ? "screen.mageadditions.team_selection.host_ready" : "screen.mageadditions.team_selection.host_waiting"), layout.centerX, layout.bottom - 12, allReady ? 0x77FF77 : 0xFFAA55);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int drawInfoLine(GuiGraphics graphics, Component label, String value, int x, int y) {
        graphics.drawString(font, label, x, y, 0xA0A0A0, false);
        graphics.drawString(font, value, x + Math.min(150, layout().rightW / 2), y, 0xFFFFFF, false);
        return y + 22;
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    private int readyCount() {
        if (!teamsEnabled) return onlinePlayers;
        int count = 0;
        for (LobbyStatePayload.RosterEntry entry : roster) {
            if (!entry.teamId().equals(MinigameRegistry.UNASSIGNED_TEAM_ID)) count++;
        }
        return count;
    }

    private Component teamName(ResourceLocation id) {
        if (id == null || id.equals(MinigameRegistry.FFA_TEAM_ID)) return Component.translatable("screen.mageadditions.team_selection.ffa_short");
        if (id.equals(MinigameRegistry.UNASSIGNED_TEAM_ID)) return Component.translatable("screen.mageadditions.team_selection.unassigned");
        MinigameDefinition.TeamDefinition team = game.team(id);
        return team == null ? Component.literal(id.getPath()) : team.displayName();
    }

    private Layout layout() {
        int total = Math.min(930, width - 28);
        int centerW = canManage ? 160 : 20;
        int side = (total - centerW - 24) / 2;
        int left = (width - total) / 2;
        int top = 52;
        int bottom = height - 32;
        int centerX = left + side + 12 + centerW / 2;
        int right = left + side + 24 + centerW;
        return new Layout(left, side, centerX, right, side, top, bottom);
    }

    private static String formatTime(int seconds) { return String.format("%d:%02d", seconds / 60, seconds % 60); }
    private static String trimNumber(double value) { return Math.rint(value) == value ? Long.toString((long)value) : String.format("%.1f", value); }

    @Override public boolean isPauseScreen() { return false; }

    private record Layout(int left, int leftW, int centerX, int right, int rightW, int top, int bottom) {}
}
