package net.fireboy.mageadditions.client;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.network.SpellConfigPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Functional per-spell editor for Iron's 3.14.8 + Mage Additions generic balance rules.
 */
public final class SpellEditorScreen extends Screen {
    private static final int FIELD_WIDTH = 132;
    private static final int FIELD_HEIGHT = 20;
    private static final int WIDE_ROW_GAP = 27;
    private static final int MIN_ROW_GAP = 20;
    private static final int NARROW_LAYOUT_WIDTH = 680;

    private final Screen parent;
    private final AbstractSpell spell;

    private boolean canEdit;
    private boolean waitingForServerSnapshot;
    private String backendName = "";
    private Component status = Component.empty();
    private int originalMaxLevel;

    private boolean enabled;
    private boolean allowCrafting;
    private ResourceLocation schoolId;
    private SpellRarity rarity;
    private List<SchoolType> schools = List.of();

    private OverrideMode castMode;
    private OverrideMode manaMode;
    private OverrideMode cooldownMode;

    private Button enabledButton;
    private Button schoolButton;
    private Button rarityButton;
    private Button craftingButton;
    private Button castModeButton;
    private Button manaModeButton;
    private Button cooldownModeButton;
    private Button saveButton;
    private Button counterspellButton;
    private Button ironsTabButton;
    private Button mageTabButton;

    private boolean narrowLayout;
    private boolean showMageSection;
    private int rowGap = WIDE_ROW_GAP;
    private int panelLeft;
    private int panelRight;
    private int leftColumnX;
    private int rightColumnX;
    private int columnWidth;
    private int contentY;

    private EditBox maxLevelBox;
    private EditBox manaMultiplierBox;
    private EditBox powerMultiplierBox;
    private EditBox cooldownSecondsBox;
    private EditBox castValueBox;
    private EditBox manaValueBox;
    private EditBox cooldownValueBox;

    public SpellEditorScreen(Screen parent, AbstractSpell spell) {
        super(Component.literal("Edit Spell - " + spell.getSpellId()));
        this.parent = parent;
        this.spell = spell;
    }

    @Override
    protected void init() {
        IronsSpellConfigAccess.Settings iron = IronsSpellConfigAccess.read(this.spell);
        IronsSpellConfigAccess.Settings original = IronsSpellConfigAccess.defaults(this.spell);
        MageAdditionsConfigEditor.SpellRules mage = MageAdditionsConfigEditor.readSpellRules(this.spell.getSpellId());

        this.originalMaxLevel = original.maxLevel();

        this.enabled = iron.enabled();
        this.allowCrafting = iron.allowCrafting();
        this.schoolId = iron.school();
        this.rarity = iron.minRarity();
        this.castMode = OverrideMode.from(mage.castTime());
        this.manaMode = OverrideMode.from(mage.mana());
        this.cooldownMode = OverrideMode.from(mage.cooldown());

        List<SchoolType> discoveredSchools = new ArrayList<>(SchoolRegistry.REGISTRY.stream().toList());
        discoveredSchools.sort(Comparator.comparing(s -> s.getDisplayName().getString().toLowerCase(Locale.ROOT)));
        this.schools = List.copyOf(discoveredSchools);

        this.narrowLayout = this.width < NARROW_LAYOUT_WIDTH;
        int bottomY = Math.max(110, this.height - 28);

        if (this.narrowLayout) {
            int panelWidth = Math.min(470, Math.max(260, this.width - 24));
            this.panelLeft = this.width / 2 - panelWidth / 2;
            this.panelRight = this.panelLeft + panelWidth;
            this.leftColumnX = this.panelLeft + 8;
            this.rightColumnX = this.leftColumnX;
            this.columnWidth = panelWidth - 16;
            this.contentY = 76;

            int availableForRows = Math.max(140, bottomY - this.contentY - FIELD_HEIGHT - 8);
            this.rowGap = Math.max(MIN_ROW_GAP, Math.min(WIDE_ROW_GAP, availableForRows / 7));

            int tabWidth = Math.min(150, Math.max(100, (panelWidth - 10) / 2));
            int tabsTotal = tabWidth * 2 + 6;
            int tabX = this.width / 2 - tabsTotal / 2;

            this.ironsTabButton = addRenderableWidget(Button.builder(Component.literal("Iron's Config"), b -> {
                this.showMageSection = false;
                updateSectionVisibility();
            }).bounds(tabX, 45, tabWidth, FIELD_HEIGHT).build());

            this.mageTabButton = addRenderableWidget(Button.builder(Component.literal("Mage Additions"), b -> {
                this.showMageSection = true;
                updateSectionVisibility();
            }).bounds(tabX + tabWidth + 6, 45, tabWidth, FIELD_HEIGHT).build());
        } else {
            int totalWidth = Math.min(760, this.width - 32);
            int gap = 24;
            this.columnWidth = (totalWidth - gap) / 2;
            this.leftColumnX = this.width / 2 - totalWidth / 2;
            this.rightColumnX = this.leftColumnX + this.columnWidth + gap;
            this.panelLeft = this.leftColumnX - 8;
            this.panelRight = this.rightColumnX + this.columnWidth + 8;
            this.contentY = 78;
            this.rowGap = WIDE_ROW_GAP;
        }

        int ironFieldX = fieldX(this.leftColumnX);
        int mageFieldX = fieldX(this.rightColumnX);

        int y = this.contentY;
        this.enabledButton = addRenderableWidget(Button.builder(toggleLabel("Enabled", this.enabled), b -> {
            this.enabled = !this.enabled;
            b.setMessage(toggleLabel("Enabled", this.enabled));
        }).bounds(ironFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.schoolButton = addRenderableWidget(Button.builder(schoolLabel(), b -> cycleSchool())
                .bounds(ironFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.maxLevelBox = numericBox(ironFieldX, y, Integer.toString(iron.maxLevel()));
        y += this.rowGap;

        this.rarityButton = addRenderableWidget(Button.builder(rarityLabel(), b -> cycleRarity())
                .bounds(ironFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.manaMultiplierBox = numericBox(ironFieldX, y, format(iron.manaMultiplier()));
        y += this.rowGap;

        this.powerMultiplierBox = numericBox(ironFieldX, y, format(iron.powerMultiplier()));
        y += this.rowGap;

        this.cooldownSecondsBox = numericBox(ironFieldX, y, format(iron.cooldownSeconds()));
        y += this.rowGap;

        this.craftingButton = addRenderableWidget(Button.builder(toggleLabel("Craftable", this.allowCrafting), b -> {
            this.allowCrafting = !this.allowCrafting;
            b.setMessage(toggleLabel("Craftable", this.allowCrafting));
        }).bounds(ironFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());

        y = this.contentY;
        this.castModeButton = addRenderableWidget(Button.builder(modeLabel("Cast", this.castMode), b -> {
            this.castMode = this.castMode.next();
            b.setMessage(modeLabel("Cast", this.castMode));
            updateOverrideFields();
        }).bounds(mageFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.castValueBox = numericBox(mageFieldX, y, format(mage.castTime().value()));
        y += this.rowGap;

        this.manaModeButton = addRenderableWidget(Button.builder(modeLabel("Mana", this.manaMode), b -> {
            this.manaMode = this.manaMode.next();
            b.setMessage(modeLabel("Mana", this.manaMode));
            updateOverrideFields();
        }).bounds(mageFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.manaValueBox = numericBox(mageFieldX, y, format(mage.mana().value()));
        y += this.rowGap;

        this.cooldownModeButton = addRenderableWidget(Button.builder(modeLabel("Cooldown", this.cooldownMode), b -> {
            this.cooldownMode = this.cooldownMode.next();
            b.setMessage(modeLabel("Cooldown", this.cooldownMode));
            updateOverrideFields();
        }).bounds(mageFieldX, y, FIELD_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.cooldownValueBox = numericBox(mageFieldX, y, format(mage.cooldown().value()));

        if (this.spell.getSpellId().equals("irons_spellbooks:counterspell")) {
            int buttonY = y + this.rowGap + 4;
            this.counterspellButton = addRenderableWidget(Button.builder(Component.literal("Counterspell Rework Settings..."), b -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new CounterspellEditorScreen(this));
                }
            }).bounds(this.rightColumnX, buttonY, this.columnWidth, FIELD_HEIGHT).build());
        } else {
            this.counterspellButton = null;
        }

        int buttonGap = 6;
        int saveWidth = this.narrowLayout ? 82 : 100;
        int resetWidth = this.narrowLayout ? 112 : 150;
        int totalButtons = resetWidth + saveWidth * 2 + buttonGap * 2;
        int buttonX = this.width / 2 - totalButtons / 2;

        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> resetToDefaults())
                .bounds(buttonX, bottomY, resetWidth, FIELD_HEIGHT).build());
        buttonX += resetWidth + buttonGap;

        this.saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(buttonX, bottomY, saveWidth, FIELD_HEIGHT).build());
        buttonX += saveWidth + buttonGap;

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(buttonX, bottomY, saveWidth, FIELD_HEIGHT).build());

        this.canEdit = false;
        updateOverrideFields();
        setEditingEnabled(false);
        updateSectionVisibility();

        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.waitingForServerSnapshot = true;
            this.status = Component.literal("Loading live server values...").withStyle(ChatFormatting.YELLOW);
            PacketDistributor.sendToServer(new SpellConfigPayloads.Request(this.spell.getSpellResource()));
        } else {
            this.waitingForServerSnapshot = false;
            this.status = Component.literal("Load a world/server to edit spell balance live.").withStyle(ChatFormatting.YELLOW);
        }
    }

    private int fieldX(int columnX) {
        return columnX + this.columnWidth - FIELD_WIDTH;
    }

    private void updateSectionVisibility() {
        boolean showIron = !this.narrowLayout || !this.showMageSection;
        boolean showMage = !this.narrowLayout || this.showMageSection;

        setVisible(this.enabledButton, showIron);
        setVisible(this.schoolButton, showIron);
        setVisible(this.maxLevelBox, showIron);
        setVisible(this.rarityButton, showIron);
        setVisible(this.manaMultiplierBox, showIron);
        setVisible(this.powerMultiplierBox, showIron);
        setVisible(this.cooldownSecondsBox, showIron);
        setVisible(this.craftingButton, showIron);

        setVisible(this.castModeButton, showMage);
        setVisible(this.castValueBox, showMage);
        setVisible(this.manaModeButton, showMage);
        setVisible(this.manaValueBox, showMage);
        setVisible(this.cooldownModeButton, showMage);
        setVisible(this.cooldownValueBox, showMage);
        setVisible(this.counterspellButton, showMage);

        if (this.ironsTabButton != null) {
            this.ironsTabButton.active = this.showMageSection;
        }
        if (this.mageTabButton != null) {
            this.mageTabButton.active = !this.showMageSection;
        }
    }

    private static void setVisible(net.minecraft.client.gui.components.AbstractWidget widget, boolean visible) {
        if (widget != null) {
            widget.visible = visible;
        }
    }

    private EditBox numericBox(int x, int y, String value) {
        EditBox box = addRenderableWidget(new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty()));
        box.setMaxLength(32);
        box.setValue(value);
        return box;
    }

    private void updateOverrideFields() {
        if (this.castValueBox != null) this.castValueBox.active = this.castMode != OverrideMode.OFF && this.canEdit;
        if (this.manaValueBox != null) this.manaValueBox.active = this.manaMode != OverrideMode.OFF && this.canEdit;
        if (this.cooldownValueBox != null) this.cooldownValueBox.active = this.cooldownMode != OverrideMode.OFF && this.canEdit;
    }

    private void setEditingEnabled(boolean enabled) {
        this.enabledButton.active = enabled;
        this.schoolButton.active = enabled;
        this.maxLevelBox.active = enabled;
        this.rarityButton.active = enabled;
        this.manaMultiplierBox.active = enabled;
        this.powerMultiplierBox.active = enabled;
        this.cooldownSecondsBox.active = enabled;
        this.craftingButton.active = enabled;
        this.castModeButton.active = enabled;
        this.manaModeButton.active = enabled;
        this.cooldownModeButton.active = enabled;
        this.saveButton.active = enabled;
        updateOverrideFields();
    }

    private void cycleSchool() {
        if (this.schools.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < this.schools.size(); i++) {
            if (this.schools.get(i).getId().equals(this.schoolId)) {
                current = i;
                break;
            }
        }
        int next = (current + 1 + this.schools.size()) % this.schools.size();
        this.schoolId = this.schools.get(next).getId();
        this.schoolButton.setMessage(schoolLabel());
    }

    private void cycleRarity() {
        SpellRarity[] values = SpellRarity.values();
        this.rarity = values[(this.rarity.ordinal() + 1) % values.length];
        this.rarityButton.setMessage(rarityLabel());
    }

    private void resetToDefaults() {
        IronsSpellConfigAccess.Settings defaults = IronsSpellConfigAccess.defaults(this.spell);
        this.enabled = defaults.enabled();
        this.allowCrafting = defaults.allowCrafting();
        this.schoolId = defaults.school();
        this.rarity = defaults.minRarity();

        this.enabledButton.setMessage(toggleLabel("Enabled", this.enabled));
        this.schoolButton.setMessage(schoolLabel());
        this.maxLevelBox.setValue(Integer.toString(defaults.maxLevel()));
        this.rarityButton.setMessage(rarityLabel());
        this.manaMultiplierBox.setValue("1");
        this.powerMultiplierBox.setValue("1");
        this.cooldownSecondsBox.setValue(format(defaults.cooldownSeconds()));
        this.craftingButton.setMessage(toggleLabel("Craftable", this.allowCrafting));

        this.castMode = OverrideMode.OFF;
        this.manaMode = OverrideMode.OFF;
        this.cooldownMode = OverrideMode.OFF;
        this.castModeButton.setMessage(modeLabel("Cast", this.castMode));
        this.manaModeButton.setMessage(modeLabel("Mana", this.manaMode));
        this.cooldownModeButton.setMessage(modeLabel("Cooldown", this.cooldownMode));
        this.castValueBox.setValue("0");
        this.manaValueBox.setValue("0");
        this.cooldownValueBox.setValue("0");
        updateOverrideFields();
        this.status = Component.literal("Defaults selected - press Save to apply.").withStyle(ChatFormatting.YELLOW);
    }

    private void save() {
        try {
            if (this.minecraft == null || this.minecraft.getConnection() == null) {
                throw new IllegalStateException("Load a world/server before saving live spell settings.");
            }
            if (!this.canEdit) {
                throw new IllegalStateException("You do not have permission to edit server spell balance.");
            }

            int maxLevel = parseInt(this.maxLevelBox, "Max level", 1, 1000);
            double manaMultiplier = parseDouble(this.manaMultiplierBox, "Mana multiplier", 0.0, 1_000_000.0);
            double powerMultiplier = parseDouble(this.powerMultiplierBox, "Power multiplier", 0.0, 1_000_000.0);
            double cooldownSeconds = parseDouble(this.cooldownSecondsBox, "Cooldown", 0.0, 3600.0);

            double castValue = parseOverrideValue(this.castValueBox, this.castMode, "Cast override");
            double manaValue = parseOverrideValue(this.manaValueBox, this.manaMode, "Mana override");
            double cooldownValue = parseOverrideValue(this.cooldownValueBox, this.cooldownMode, "Cooldown override");

            this.waitingForServerSnapshot = true;
            this.status = Component.literal("Saving to server...").withStyle(ChatFormatting.YELLOW);
            setEditingEnabled(false);

            PacketDistributor.sendToServer(new SpellConfigPayloads.Update(
                    this.spell.getSpellResource(),
                    this.enabled,
                    this.schoolId,
                    maxLevel,
                    this.rarity.name(),
                    manaMultiplier,
                    powerMultiplier,
                    cooldownSeconds,
                    this.allowCrafting,
                    this.castMode.wireName,
                    castValue,
                    this.manaMode.wireName,
                    manaValue,
                    this.cooldownMode.wireName,
                    cooldownValue
            ));
        } catch (Exception exception) {
            this.waitingForServerSnapshot = false;
            this.status = Component.literal(exception.getMessage() == null ? "Save failed" : exception.getMessage())
                    .withStyle(ChatFormatting.RED);
            setEditingEnabled(this.canEdit);
        }
    }

    /** Applies the authoritative values returned by the server. */
    public void applyServerSnapshot(SpellConfigPayloads.Snapshot snapshot) {
        if (!snapshot.spellId().equals(this.spell.getSpellResource())) {
            return;
        }

        this.waitingForServerSnapshot = false;
        this.backendName = snapshot.backendName();
        this.canEdit = snapshot.canEdit();

        this.enabled = snapshot.enabled();
        this.allowCrafting = snapshot.allowCrafting();
        this.schoolId = snapshot.school();
        try {
            this.rarity = SpellRarity.valueOf(snapshot.minRarity().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            this.rarity = SpellRarity.COMMON;
        }

        this.castMode = OverrideMode.fromWire(snapshot.castMode());
        this.manaMode = OverrideMode.fromWire(snapshot.manaMode());
        this.cooldownMode = OverrideMode.fromWire(snapshot.cooldownMode());

        this.enabledButton.setMessage(toggleLabel("Enabled", this.enabled));
        this.schoolButton.setMessage(schoolLabel());
        this.maxLevelBox.setValue(Integer.toString(snapshot.maxLevel()));
        this.rarityButton.setMessage(rarityLabel());
        this.manaMultiplierBox.setValue(format(snapshot.manaMultiplier()));
        this.powerMultiplierBox.setValue(format(snapshot.powerMultiplier()));
        this.cooldownSecondsBox.setValue(format(snapshot.cooldownSeconds()));
        this.craftingButton.setMessage(toggleLabel("Craftable", this.allowCrafting));

        this.castModeButton.setMessage(modeLabel("Cast", this.castMode));
        this.castValueBox.setValue(format(snapshot.castValue()));
        this.manaModeButton.setMessage(modeLabel("Mana", this.manaMode));
        this.manaValueBox.setValue(format(snapshot.manaValue()));
        this.cooldownModeButton.setMessage(modeLabel("Cooldown", this.cooldownMode));
        this.cooldownValueBox.setValue(format(snapshot.cooldownValue()));

        setEditingEnabled(this.canEdit);
        updateSectionVisibility();

        ChatFormatting color = snapshot.success() ? ChatFormatting.GREEN : ChatFormatting.RED;
        this.status = Component.literal(snapshot.message()).withStyle(color);
    }

    private double parseOverrideValue(EditBox box, OverrideMode mode, String label) {
        if (mode == OverrideMode.OFF) return 0.0;
        return parseDouble(box, label, 0.0, 1_000_000.0);
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

    private Component schoolLabel() {
        SchoolType school = SchoolRegistry.getSchool(this.schoolId);
        String value = school != null ? school.getDisplayName().getString() : this.schoolId.toString();
        return Component.literal(trim(value, 18));
    }

    private Component rarityLabel() {
        return this.rarity.getDisplayName();
    }

    private static Component toggleLabel(String label, boolean value) {
        return Component.literal(label + ": ").append(
                Component.literal(value ? "ON" : "OFF")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)
        );
    }

    private static Component modeLabel(String label, OverrideMode mode) {
        return Component.literal(label + ": " + mode.displayName);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.spell.getDisplayName(this.minecraft == null ? null : this.minecraft.player), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, this.spell.getSpellId(), this.width / 2, 25, 0x888888);

        int panelBottom = Math.max(96, this.height - 38);

        if (this.narrowLayout) {
            graphics.fill(this.panelLeft, 70, this.panelRight, panelBottom, 0x55000000);

            if (this.showMageSection) {
                graphics.drawString(this.font, Component.literal("Mage Additions overrides").withStyle(ChatFormatting.LIGHT_PURPLE),
                        this.leftColumnX, 66, 0xFFFFFF);
                renderMageLabels(graphics, this.leftColumnX);
            } else {
                graphics.drawString(this.font, Component.literal("Iron's spell config").withStyle(ChatFormatting.AQUA),
                        this.leftColumnX, 66, 0xFFFFFF);
                renderIronLabels(graphics, this.leftColumnX);
            }
        } else {
            graphics.fill(this.leftColumnX - 8, 56, this.leftColumnX + this.columnWidth + 8, panelBottom, 0x55000000);
            graphics.fill(this.rightColumnX - 8, 56, this.rightColumnX + this.columnWidth + 8, panelBottom, 0x55000000);

            graphics.drawString(this.font, Component.literal("Iron's spell config").withStyle(ChatFormatting.AQUA),
                    this.leftColumnX, 62, 0xFFFFFF);
            graphics.drawString(this.font, Component.literal("Mage Additions overrides").withStyle(ChatFormatting.LIGHT_PURPLE),
                    this.rightColumnX, 62, 0xFFFFFF);

            renderIronLabels(graphics, this.leftColumnX);
            renderMageLabels(graphics, this.rightColumnX);
        }

        int messageY = Math.max(38, this.height - 45);
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, messageY, 0xFFFFFF);
        }
        if (!this.backendName.isBlank() && this.height >= 220) {
            graphics.drawCenteredString(this.font,
                    Component.literal(this.backendName).withStyle(ChatFormatting.DARK_GRAY),
                    this.width / 2, 37, 0xFFFFFF);
        }
    }

    private void renderIronLabels(GuiGraphics graphics, int x) {
        int y = this.contentY + 6;
        drawLabel(graphics, x, y, "Enabled"); y += this.rowGap;
        drawLabel(graphics, x, y, "School"); y += this.rowGap;

        boolean aboveOriginalMax = currentMaxLevel() > this.originalMaxLevel;
        Component maxLevelLabel = Component.literal(aboveOriginalMax ? "Max level - EXPERIMENTAL" : "Max level")
                .withStyle(aboveOriginalMax ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        graphics.drawString(this.font, maxLevelLabel, x, y, 0xFFFFFF);
        graphics.drawString(
                this.font,
                Component.literal("Original max: " + this.originalMaxLevel)
                        .withStyle(aboveOriginalMax ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY),
                x,
                y + 9,
                0xFFFFFF
        );
        y += this.rowGap;

        drawLabel(graphics, x, y, "Minimum rarity"); y += this.rowGap;
        drawLabel(graphics, x, y, "Mana multiplier"); y += this.rowGap;
        drawLabel(graphics, x, y, "Power multiplier"); y += this.rowGap;
        drawLabel(graphics, x, y, "Cooldown (seconds)"); y += this.rowGap;
        drawLabel(graphics, x, y, "Allow crafting");
    }

    private int currentMaxLevel() {
        if (this.maxLevelBox == null) {
            return this.originalMaxLevel;
        }

        try {
            return Integer.parseInt(this.maxLevelBox.getValue().trim());
        } catch (NumberFormatException ignored) {
            return this.originalMaxLevel;
        }
    }

    private void renderMageLabels(GuiGraphics graphics, int x) {
        int y = this.contentY + 6;
        drawLabel(graphics, x, y, "Cast override mode"); y += this.rowGap;
        drawLabel(graphics, x, y, castValueLabel()); y += this.rowGap;
        drawLabel(graphics, x, y, "Mana override mode"); y += this.rowGap;
        drawLabel(graphics, x, y, manaValueLabel()); y += this.rowGap;
        drawLabel(graphics, x, y, "Cooldown override mode"); y += this.rowGap;
        drawLabel(graphics, x, y, cooldownValueLabel());

        int helpY = y + this.rowGap + 8;
        if (this.counterspellButton != null) {
            helpY += FIELD_HEIGHT + 8;
        }
        int helpWidth = Math.max(160, this.columnWidth);

        if (!CastTimeOverrides.balanceTweaksEnabled()) {
            graphics.drawWordWrap(
                    this.font,
                    Component.literal("Balance Tweaks is OFF. Overrides can be saved, but will not apply until the module is enabled.")
                            .withStyle(ChatFormatting.YELLOW),
                    x,
                    helpY,
                    helpWidth,
                    0xFFFFFF
            );
            helpY += 32;
        }

        if (this.spell.getCastType().name().equals("INSTANT") && this.castMode != OverrideMode.OFF) {
            graphics.drawWordWrap(
                    this.font,
                    Component.literal("Instant-spell cast delays are blocked unless Mage Additions explicitly supports that spell or allow_instant_spell_delays is enabled.")
                            .withStyle(ChatFormatting.GRAY),
                    x,
                    helpY,
                    helpWidth,
                    0xFFFFFF
            );
        }
    }

    private void drawLabel(GuiGraphics graphics, int x, int y, String label) {
        graphics.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
    }

    private String castValueLabel() {
        return this.castMode == OverrideMode.MULTIPLIER ? "Cast multiplier" : "Cast time (ticks)";
    }

    private String manaValueLabel() {
        return this.manaMode == OverrideMode.MULTIPLIER ? "Mana multiplier" : "Mana cost (points)";
    }

    private String cooldownValueLabel() {
        return this.cooldownMode == OverrideMode.MULTIPLIER ? "Cooldown multiplier" : "Cooldown (seconds)";
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 3)) + "...";
    }

    private enum OverrideMode {
        OFF("Off", "off"),
        ABSOLUTE("Absolute", "absolute"),
        MULTIPLIER("Multiplier", "multiplier");

        private final String displayName;
        private final String wireName;

        OverrideMode(String displayName, String wireName) {
            this.displayName = displayName;
            this.wireName = wireName;
        }

        private OverrideMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        private static OverrideMode from(MageAdditionsConfigEditor.RuleState state) {
            if (state == null || !state.enabled()) return OFF;
            return "multiplier".equalsIgnoreCase(state.mode()) ? MULTIPLIER : ABSOLUTE;
        }

        private static OverrideMode fromWire(String mode) {
            if ("multiplier".equalsIgnoreCase(mode)) return MULTIPLIER;
            if ("absolute".equalsIgnoreCase(mode)) return ABSOLUTE;
            return OFF;
        }
    }
}
