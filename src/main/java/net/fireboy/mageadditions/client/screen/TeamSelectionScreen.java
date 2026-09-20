package net.fireboy.mageadditions.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.fireboy.mageadditions.network.payload.CancelMinigamePayload;
import net.fireboy.mageadditions.network.payload.LaunchMinigamePayload;
import net.fireboy.mageadditions.network.payload.LobbyStatePayload;
import net.fireboy.mageadditions.network.payload.SelectTeamPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** Live waiting-room screen shown to every player during pre-game setup. */
public final class TeamSelectionScreen extends Screen {
    private static final int PANEL_GAP = 14;

    private final MinigameDefinition game;
    private final boolean teamsEnabled;
    private final int teamCount;
    private final boolean canManage;
    private final MinigameSettings settings;
    private final Map<ResourceLocation, Button> teamButtons = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<String>> roster = new LinkedHashMap<>();

    private ResourceLocation selectedTeam;
    private int onlinePlayers;
    private boolean allReady;
    private Button launchButton;

    public TeamSelectionScreen(
        MinigameDefinition game,
        boolean teamsEnabled,
        int teamCount,
        boolean canManage,
        MinigameSettings settings
    ) {
        super(Component.translatable("screen.mageadditions.team_selection.title", game.displayName()));
        this.game = game;
        this.teamsEnabled = teamsEnabled;
        this.teamCount = Math.max(0, Math.min(teamCount, game.teams().size()));
        this.canManage = canManage;
        this.settings = settings;
    }

    @Override
    protected void init() {
        teamButtons.clear();
        int totalWidth = Math.min(820, width - 34);
        int left = (width - totalWidth) / 2;
        int columnWidth = (totalWidth - PANEL_GAP) / 2;
        int buttonAreaTop = 108;
        int panelBottom = height - (canManage ? 48 : 24);

        if (teamsEnabled) {
            int buttonWidth = Math.min(154, (columnWidth - 18) / 2);
            for (int i = 0; i < teamCount; i++) {
                MinigameDefinition.TeamDefinition team = game.teams().get(i);
                int col = i % 2;
                int row = i / 2;
                int x = left + col * (buttonWidth + 8);
                int y = buttonAreaTop + row * 30;
                Button button = addRenderableWidget(Button.builder(team.displayName(), b -> chooseTeam(team.id()))
                    .bounds(x, y, buttonWidth, 22)
                    .build());
                teamButtons.put(team.id(), button);
            }
        } else {
            Button ffa = addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.team_selection.ffa_ready"), b -> {})
                .bounds(left, buttonAreaTop, Math.min(260, columnWidth - 12), 22)
                .build());
            ffa.active = false;
            teamButtons.put(MinigameRegistry.FFA_TEAM_ID, ffa);
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.how_to_play"), b ->
            Minecraft.getInstance().setScreen(new HowToPlayScreen(this, game))
        ).bounds(left, panelBottom - 30, Math.min(180, columnWidth - 12), 22).build());

        if (canManage) {
            launchButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.mageadditions.team_selection.start_match"),
                b -> PacketDistributor.sendToServer(LaunchMinigamePayload.INSTANCE)
            ).bounds(width / 2 - 156, height - 34, 148, 22).build());
            launchButton.active = allReady;
            addRenderableWidget(Button.builder(
                Component.translatable("screen.mageadditions.team_selection.cancel_lobby"),
                b -> PacketDistributor.sendToServer(CancelMinigamePayload.INSTANCE)
            ).bounds(width / 2 + 8, height - 34, 148, 22).build());
        }

        LobbyStatePayload cached = ClientMinigameState.lobbyState();
        if (cached != null) {
            applyState(cached);
        } else {
            refreshButtonLabels();
        }
    }

    private void chooseTeam(ResourceLocation teamId) {
        PacketDistributor.sendToServer(new SelectTeamPayload(game.id(), teamId));
    }

    public void applyState(LobbyStatePayload state) {
        if (!state.gameId().equals(game.id())) {
            return;
        }
        onlinePlayers = state.onlinePlayers();
        allReady = state.allReady();
        roster.clear();
        for (LobbyStatePayload.RosterEntry entry : state.roster()) {
            roster.computeIfAbsent(entry.teamId(), ignored -> new ArrayList<>()).add(entry.playerName());
        }

        Minecraft minecraft = Minecraft.getInstance();
        String localName = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        selectedTeam = null;
        for (LobbyStatePayload.RosterEntry entry : state.roster()) {
            if (entry.playerName().equals(localName)) {
                selectedTeam = entry.teamId();
                break;
            }
        }

        refreshButtonLabels();
        if (launchButton != null) {
            launchButton.active = allReady;
        }
    }

    private void refreshButtonLabels() {
        for (Map.Entry<ResourceLocation, Button> entry : teamButtons.entrySet()) {
            ResourceLocation teamId = entry.getKey();
            int count = roster.getOrDefault(teamId, List.of()).size();
            Component base;
            if (teamId.equals(MinigameRegistry.FFA_TEAM_ID)) {
                base = Component.translatable("screen.mageadditions.team_selection.ffa_players", count);
            } else {
                MinigameDefinition.TeamDefinition team = game.team(teamId);
                base = team == null ? Component.literal(teamId.getPath()) : Component.literal("").append(team.displayName()).append(" (" + count + ")");
            }
            if (teamId.equals(selectedTeam)) {
                base = Component.literal("✓ ").append(base);
            }
            entry.getValue().setMessage(base);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int totalWidth = Math.min(820, width - 34);
        int left = (width - totalWidth) / 2;
        int top = 20;
        int bodyTop = 68;
        int columnWidth = (totalWidth - PANEL_GAP) / 2;
        int right = left + columnWidth + PANEL_GAP;
        int bottom = height - (canManage ? 48 : 24);

        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.team_selection.subtitle"), width / 2, top + 18, 0xA0A0A0);

        graphics.fill(left - 8, bodyTop - 8, left + columnWidth, bottom, 0x66000000);
        graphics.fill(right - 8, bodyTop - 8, right + columnWidth, bottom, 0x66000000);

        Component ready = Component.translatable("screen.mageadditions.team_selection.ready_count", rosterSize(), onlinePlayers);
        graphics.drawString(font, ready, left, bodyTop, allReady ? 0x77FF77 : 0xFFD966, false);
        graphics.drawString(
            font,
            Component.translatable(teamsEnabled ? "screen.mageadditions.choose_team" : "screen.mageadditions.team_selection.ffa_mode"),
            left,
            bodyTop + 18,
            0xFFFFFF,
            false
        );

        int infoY = teamsEnabled ? 108 + ((teamCount + 1) / 2) * 30 + 8 : 144;
        infoY = drawWrapped(graphics, game.description(), left, infoY, columnWidth - 12, 0xC8C8C8) + 8;
        drawWrapped(
            graphics,
            Component.translatable(
                "screen.mageadditions.team_selection.rules_summary",
                settings.durationSeconds() <= 0 ? "∞" : formatTime(settings.durationSeconds()),
                (int) settings.initialBorderSize(),
                (int) settings.finalBorderSize(),
                Component.translatable(settings.kitPreset().translationKey())
            ),
            left,
            infoY,
            columnWidth - 12,
            0xFFD966
        );

        graphics.drawString(font, Component.translatable("screen.mageadditions.team_selection.live_roster"), right, bodyTop, 0x7FDBFF, false);
        drawRoster(graphics, right, bodyTop + 22, columnWidth - 12);

        if (canManage) {
            graphics.drawCenteredString(
                font,
                Component.translatable(allReady ? "screen.mageadditions.team_selection.host_ready" : "screen.mageadditions.team_selection.host_waiting"),
                width / 2,
                height - 47,
                allReady ? 0x77FF77 : 0xFFAA55
            );
        }

        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawRoster(GuiGraphics graphics, int x, int y, int width) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (teamsEnabled) {
            for (int i = 0; i < teamCount; i++) {
                ids.add(game.teams().get(i).id());
            }
        } else {
            ids.add(MinigameRegistry.FFA_TEAM_ID);
        }

        int columns = ids.size() > 4 ? 2 : 1;
        int cellWidth = columns == 1 ? width : (width - 10) / 2;
        int rows = (ids.size() + columns - 1) / columns;
        int availableHeight = Math.max(100, height - y - (canManage ? 72 : 42));
        int cellHeight = Math.max(44, availableHeight / Math.max(1, rows));

        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ids.get(i);
            int col = columns == 1 ? 0 : i % 2;
            int row = columns == 1 ? i : i / 2;
            int cellX = x + col * (cellWidth + 10);
            int cellY = y + row * cellHeight;
            List<String> names = roster.getOrDefault(id, List.of());

            Component header;
            if (id.equals(MinigameRegistry.FFA_TEAM_ID)) {
                header = Component.translatable("screen.mageadditions.team_selection.ffa_players", names.size());
            } else {
                MinigameDefinition.TeamDefinition team = game.team(id);
                header = team == null ? Component.literal(id.getPath()) : Component.literal("").append(team.displayName()).append(" (" + names.size() + ")");
            }
            graphics.drawString(font, header, cellX, cellY, 0xFFFFFF, false);

            String namesText = names.isEmpty() ? "—" : String.join(", ", names);
            drawWrapped(graphics, Component.literal(namesText), cellX, cellY + 14, cellWidth - 4, 0xB8B8B8);
        }
    }

    private int rosterSize() {
        int count = 0;
        for (List<String> names : roster.values()) {
            count += names.size();
        }
        return count;
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(40, maxWidth));
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
