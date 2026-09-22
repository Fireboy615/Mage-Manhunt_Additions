package net.fireboy.mageadditions.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.minigame.MinigameSettings;
import net.fireboy.mageadditions.network.payload.DeleteEquipmentPresetPayload;
import net.fireboy.mageadditions.network.payload.RequestEquipmentPresetsPayload;
import net.fireboy.mageadditions.network.payload.StartMinigamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Single host setup page. Presets at the top populate editable rules/equipment values. */
public final class MinigameSetupScreen extends Screen {
    private static final int MAX_TEAMS = 8;

    private MinigameDefinition game;
    private boolean teamsEnabled = true;
    private int teamCount = 2;
    private MinigameSettings settings;
    private List<String> customPresets = List.of();

    private EditBox durationBox;
    private EditBox startRadiusBox;
    private EditBox endRadiusBox;
    private Button formatButton;
    private Button teamCountButton;
    private Button minusButton;
    private Button plusButton;
    private Button randomSpawnButton;
    private Button equipmentButton;
    private Button deletePresetButton;

    public MinigameSetupScreen() {
        this(defaultPreset());
    }

    public MinigameSetupScreen(MinigameDefinition game) {
        super(Component.translatable("screen.mageadditions.minigame_setup.title"));
        this.game = game == null ? defaultPreset() : game;
        this.settings = MinigameSettings.defaults(this.game);
        this.customPresets = ClientMinigameState.equipmentPresets();
    }

    private static MinigameDefinition defaultPreset() {
        MinigameDefinition preferred = MinigameRegistry.get(MinigameRegistry.BLITZ_15_ID);
        if (preferred != null) return preferred;
        return MinigameRegistry.all().stream().findFirst().orElseThrow(() -> new IllegalStateException("No minigame presets registered"));
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(RequestEquipmentPresetsPayload.INSTANCE);

        int panelWidth = Math.min(760, width - 36);
        int left = (width - panelWidth) / 2;
        int presetY = 54;
        addPresetButtons(left, panelWidth, presetY);

        int gap = 28;
        int columnWidth = (panelWidth - gap) / 2;
        int right = left + columnWidth + gap;
        int top = Math.max(118, height / 2 - 92);
        int fieldX = left + Math.min(150, columnWidth - 132);
        int fieldWidth = Math.max(100, columnWidth - (fieldX - left));

        durationBox = numericBox(fieldX, top + 6, fieldWidth, formatMinutes(settings.durationSeconds()));
        startRadiusBox = numericBox(fieldX, top + 36, fieldWidth, trimNumber(settings.initialBorderSize()));
        endRadiusBox = numericBox(fieldX, top + 66, fieldWidth, trimNumber(settings.finalBorderSize()));

        formatButton = addRenderableWidget(Button.builder(formatLabel(), b -> {
            teamsEnabled = !teamsEnabled;
            refreshControls();
        }).bounds(left, top + 106, columnWidth, 22).build());

        minusButton = addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            teamCount = Math.max(2, teamCount - 1);
            refreshControls();
        }).bounds(left, top + 136, 32, 22).build());
        teamCountButton = addRenderableWidget(Button.builder(teamCountLabel(), b -> {})
            .bounds(left + 40, top + 136, columnWidth - 80, 22).build());
        teamCountButton.active = false;
        plusButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            teamCount = Math.min(Math.min(MAX_TEAMS, game.teams().size()), teamCount + 1);
            refreshControls();
        }).bounds(left + columnWidth - 32, top + 136, 32, 22).build());

        equipmentButton = addRenderableWidget(Button.builder(equipmentLabel(), b -> cycleEquipment())
            .bounds(right, top + 6, columnWidth, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.loadout.save_current"), b -> openSavePreset())
            .bounds(right, top + 36, columnWidth, 22).build());
        deletePresetButton = addRenderableWidget(Button.builder(Component.translatable("screen.mageadditions.loadout.delete_selected"), b -> deleteSelectedPreset())
            .bounds(right, top + 66, columnWidth, 22).build());

        randomSpawnButton = addRenderableWidget(Button.builder(randomSpawnLabel(), b -> {
            settings = new MinigameSettings(settings.durationSeconds(), settings.initialBorderSize(), settings.finalBorderSize(),
                !settings.randomTeleport(), settings.kitPreset(), settings.customEquipmentPreset());
            refreshControls();
        }).bounds(right, top + 106, columnWidth, 22).build());

        int bottomY = Math.min(height - 36, top + 188);
        addRenderableWidget(Button.builder(Component.translatable("gui.next"), b -> openLobby())
            .bounds(width / 2 - 120, bottomY, 116, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(width / 2 + 4, bottomY, 116, 22).build());

        refreshControls();
    }

    private void addPresetButtons(int left, int panelWidth, int y) {
        List<MinigameDefinition> presets = List.copyOf(MinigameRegistry.all());
        if (presets.isEmpty()) return;
        int gap = 6;
        int columns = panelWidth >= 560 ? presets.size() : Math.min(2, presets.size());
        int buttonW = (panelWidth - gap * (columns - 1)) / columns;
        for (int i = 0; i < presets.size(); i++) {
            MinigameDefinition preset = presets.get(i);
            int x = left + (i % columns) * (buttonW + gap);
            int buttonY = y + (i / columns) * 26;
            boolean selected = preset.id().equals(game.id());
            Component label = selected
                ? Component.literal("✓ ").append(presetLabel(preset))
                : presetLabel(preset);
            addRenderableWidget(Button.builder(label, b -> applyPreset(preset))
                .bounds(x, buttonY, buttonW, 22)
                .build());
        }
    }

    private Component presetLabel(MinigameDefinition preset) {
        ResourceLocation id = preset.id();
        if (id.equals(MinigameRegistry.BLITZ_15_ID)) return Component.translatable("screen.mageadditions.minigame_setup.preset.blitz");
        if (id.equals(MinigameRegistry.STANDARD_30_ID)) return Component.translatable("screen.mageadditions.minigame_setup.preset.standard_30");
        if (id.equals(MinigameRegistry.STANDARD_60_ID)) return Component.translatable("screen.mageadditions.minigame_setup.preset.standard_60");
        if (id.equals(MinigameRegistry.PRACTICE_ARENA_ID)) return Component.translatable("screen.mageadditions.minigame_setup.preset.practice");
        return preset.displayName();
    }

    private void applyPreset(MinigameDefinition preset) {
        game = preset;
        settings = MinigameSettings.defaults(preset);
        rebuildWidgets();
    }

    public void applyEquipmentPresets(List<String> names) {
        customPresets = List.copyOf(names);
        if (settings.hasCustomEquipmentPreset() && customPresets.stream().noneMatch(n -> n.equalsIgnoreCase(settings.customEquipmentPreset()))) {
            settings = new MinigameSettings(settings.durationSeconds(), settings.initialBorderSize(), settings.finalBorderSize(),
                settings.randomTeleport(), MinigameSettings.KitPreset.MODE_DEFAULT, "");
        }
        refreshControls();
    }

    void setSettings(MinigameSettings settings) {
        this.settings = settings.validated();
        rebuildWidgets();
    }

    private EditBox numericBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 22, Component.empty());
        box.setMaxLength(12);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9]*\\.?[0-9]*"));
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void openSavePreset() {
        syncTextFieldsIntoSettings();
        Minecraft.getInstance().setScreen(new SaveEquipmentPresetScreen(this));
    }

    private void syncTextFieldsIntoSettings() {
        if (durationBox == null || startRadiusBox == null || endRadiusBox == null) return;
        double minutes = parseDouble(durationBox.getValue(), settings.durationSeconds() / 60.0);
        double startRadius = parseDouble(startRadiusBox.getValue(), settings.initialBorderSize());
        double endRadius = parseDouble(endRadiusBox.getValue(), settings.finalBorderSize());
        settings = new MinigameSettings((int)Math.round(minutes * 60.0), startRadius, endRadius,
            settings.randomTeleport(), settings.kitPreset(), settings.customEquipmentPreset()).validated();
    }

    private void cycleEquipment() {
        List<PresetChoice> choices = equipmentChoices();
        int current = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).matches(settings)) { current = i; break; }
        }
        PresetChoice next = choices.get((current + 1) % choices.size());
        settings = new MinigameSettings(settings.durationSeconds(), settings.initialBorderSize(), settings.finalBorderSize(),
            settings.randomTeleport(), next.kit(), next.customName());
        refreshControls();
    }

    private List<PresetChoice> equipmentChoices() {
        List<PresetChoice> choices = new ArrayList<>();
        for (MinigameSettings.KitPreset kit : MinigameSettings.KitPreset.values()) choices.add(new PresetChoice(kit, ""));
        for (String name : customPresets) choices.add(new PresetChoice(MinigameSettings.KitPreset.NONE, name));
        return choices;
    }

    private void deleteSelectedPreset() {
        if (!settings.hasCustomEquipmentPreset()) return;
        PacketDistributor.sendToServer(new DeleteEquipmentPresetPayload(settings.customEquipmentPreset()));
        settings = new MinigameSettings(settings.durationSeconds(), settings.initialBorderSize(), settings.finalBorderSize(),
            settings.randomTeleport(), MinigameSettings.KitPreset.MODE_DEFAULT, "");
        refreshControls();
    }

    private void refreshControls() {
        if (formatButton == null) return;
        formatButton.setMessage(formatLabel());
        teamCountButton.setMessage(teamCountLabel());
        boolean teams = teamsEnabled;
        minusButton.visible = teams;
        plusButton.visible = teams;
        teamCountButton.visible = teams;
        minusButton.active = teams && teamCount > 2;
        plusButton.active = teams && teamCount < Math.min(MAX_TEAMS, game.teams().size());
        equipmentButton.setMessage(equipmentLabel());
        randomSpawnButton.setMessage(randomSpawnLabel());
        deletePresetButton.active = settings.hasCustomEquipmentPreset();
    }

    private Component formatLabel() {
        return Component.translatable(teamsEnabled ? "screen.mageadditions.minigame_setup.format.teams" : "screen.mageadditions.minigame_setup.format.ffa");
    }

    private Component teamCountLabel() {
        return Component.translatable("screen.mageadditions.minigame_setup.team_count", teamCount);
    }

    private Component randomSpawnLabel() {
        return Component.translatable(settings.randomTeleport() ? "screen.mageadditions.settings.random_spawn.on" : "screen.mageadditions.settings.random_spawn.off");
    }

    private Component equipmentLabel() {
        if (settings.hasCustomEquipmentPreset()) {
            return Component.translatable("screen.mageadditions.loadout.selected_custom", settings.customEquipmentPreset());
        }
        return Component.translatable("screen.mageadditions.settings.kit", Component.translatable(settings.kitPreset().translationKey()));
    }

    private void openLobby() {
        syncTextFieldsIntoSettings();
        PacketDistributor.sendToServer(new StartMinigamePayload(game.id(), teamsEnabled, teamsEnabled ? teamCount : 0, settings));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(760, width - 36);
        int left = (width - panelWidth) / 2;
        int gap = 28;
        int columnWidth = (panelWidth - gap) / 2;
        int right = left + columnWidth + gap;
        int top = Math.max(118, height / 2 - 92);

        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.minigame_setup.presets"), width / 2, 38, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.mageadditions.minigame_setup.rules"), left, top - 18, 0xFFD966, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.minigame_setup.equipment"), right, top - 18, 0x7FDBFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.settings.duration"), left, top + 12, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.settings.start_radius"), left, top + 42, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.settings.end_radius"), left, top + 72, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.mageadditions.loadout.capture_hint"), right, top + 140, 0x909090, false);
        graphics.drawCenteredString(font, game.description(), width / 2, Math.min(height - 58, top + 168), 0x909090);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return fallback; }
    }
    private static String formatMinutes(int seconds) {
        if (seconds <= 0) return "0";
        double minutes = seconds / 60.0;
        return Math.rint(minutes) == minutes ? Integer.toString((int)minutes) : String.format("%.1f", minutes);
    }
    private static String trimNumber(double value) {
        return Math.rint(value) == value ? Long.toString((long)value) : Double.toString(value);
    }

    private record PresetChoice(MinigameSettings.KitPreset kit, String customName) {
        boolean matches(MinigameSettings settings) {
            if (!customName.isBlank()) return settings.hasCustomEquipmentPreset() && customName.equalsIgnoreCase(settings.customEquipmentPreset());
            return !settings.hasCustomEquipmentPreset() && settings.kitPreset() == kit;
        }
    }
}
