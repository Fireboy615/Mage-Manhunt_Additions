package net.fireboy.mageadditions.client;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.config.SpellOverrideConfigService;
import net.fireboy.mageadditions.network.SpellConfigPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative per-spell editor for Iron's + Mage Additions.
 *
 * <p>Iron's owns its native balancing values. Mage Additions only exposes
 * additional behaviour here; duplicated Mage-side mana/cooldown overrides are
 * intentionally no longer part of this screen.</p>
 */
public final class SpellEditorScreen extends Screen {
    private static final int FIELD_WIDTH = 132;
    private static final int FIELD_HEIGHT = 20;
    private static final int WIDE_ROW_GAP = 23;
    private static final int MIN_ROW_GAP = 18;
    private static final int NARROW_LAYOUT_WIDTH = 680;
    private static final int SMALL_RESET_WIDTH = 46;
    private static final int SMALL_CONTROL_GAP = 4;
    private static final int MAGE_ROW_WIDTH = 210;
    private static final int HEIGHT_TOGGLE_WIDTH = 74;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 20;
    private static final int SCROLL_WHEEL_PIXELS = 28;

    private enum ScrollSection {
        IRON,
        MAGE
    }

    private final Screen parent;
    private final AbstractSpell spell;

    private boolean canEdit;
    private boolean waitingForServerSnapshot;
    private String backendName = "";
    private Component status = Component.empty();
    private int originalMaxLevel;
    private double originalCastTimeLevelOne;

    private boolean enabled;
    private boolean allowCrafting;
    private ResourceLocation schoolId;
    private SpellRarity rarity;
    private List<SchoolType> schools = List.of();

    private Button enabledButton;
    private Button schoolButton;
    private Button rarityButton;
    private Button craftingButton;
    private Button mageOverridesButton;
    private Button castResetButton;
    private Button rangeResetButton;
    private Button movementButton;
    private Button movementResetButton;
    private Button movementMultiplierResetButton;
    private Button maxHeightToggleButton;
    private Button maxHeightResetButton;
    private Button lineOfSightButton;
    private Button lineOfSightResetButton;
    private Button minCastDistanceResetButton;
    private Button maxCastDistanceResetButton;
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
    private int scrollViewportTop;
    private int scrollViewportBottom;
    private int ironContentBottomBase;
    private int mageContentBottomBase;
    private int ironScroll;
    private int mageScroll;
    private boolean scrollBarDragging;
    private ScrollSection scrollBarDragSection;
    private double scrollBarGrabOffset;
    private final Map<AbstractWidget, Integer> scrollBaseY = new IdentityHashMap<>();
    private final Map<AbstractWidget, ScrollSection> scrollSections = new IdentityHashMap<>();

    private EditBox maxLevelBox;
    private EditBox manaMultiplierBox;
    private EditBox powerMultiplierBox;
    private EditBox cooldownSecondsBox;
    private EditBox castValueBox;
    private EditBox castMultiplierBox;
    private EditBox rangeValueBox;
    private EditBox rangeMultiplierBox;
    private EditBox movementMultiplierBox;
    private EditBox maxHeightAboveGroundBox;
    private EditBox minCastDistanceBox;
    private EditBox maxCastDistanceBox;
    private LinkedNumericOverrideControl castControl;
    private LinkedNumericOverrideControl rangeControl;

    private boolean mageOverridesEnabled = false;
    private String movementMode = "default";
    private boolean maxHeightEnabled = false;
    private boolean lineOfSightValue = true;
    private boolean originalLineOfSight = true;
    private boolean lineOfSightOverrideActive = false;
    private double originalTargetRange = 32.0;
    private double originalMinCastDistance = 0.0;
    private double originalMaxCastDistance = 32.0;
    private boolean minCastDistanceOverrideActive = false;
    private boolean maxCastDistanceOverrideActive = false;
    private boolean updatingBehaviorFields = false;

    public SpellEditorScreen(Screen parent, AbstractSpell spell) {
        super(Component.literal("Edit Spell - " + spell.getSpellId()));
        this.parent = parent;
        this.spell = spell;
    }

    @Override
    protected void init() {
        IronsSpellConfigAccess.Settings iron = IronsSpellConfigAccess.read(this.spell);
        IronsSpellConfigAccess.Settings original = IronsSpellConfigAccess.defaults(this.spell);

        this.originalMaxLevel = original.maxLevel();
        this.originalCastTimeLevelOne = Math.max(0.0, this.spell.getCastTime(1));

        this.enabled = iron.enabled();
        this.allowCrafting = iron.allowCrafting();
        this.schoolId = iron.school();
        this.rarity = iron.minRarity();

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

            int availableForRows = Math.max(160, bottomY - this.contentY - FIELD_HEIGHT - 8);
            this.rowGap = Math.max(MIN_ROW_GAP, Math.min(WIDE_ROW_GAP, availableForRows / 14));

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

        this.scrollViewportTop = this.contentY - 2;
        this.scrollViewportBottom = Math.max(this.scrollViewportTop + 48, this.height - 56);

        int ironFieldX = fieldX(this.leftColumnX);
        int mageFieldX = this.rightColumnX + this.columnWidth - Math.min(MAGE_ROW_WIDTH, this.columnWidth);

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

        registerScrollable(this.enabledButton, ScrollSection.IRON);
        registerScrollable(this.schoolButton, ScrollSection.IRON);
        registerScrollable(this.maxLevelBox, ScrollSection.IRON);
        registerScrollable(this.rarityButton, ScrollSection.IRON);
        registerScrollable(this.manaMultiplierBox, ScrollSection.IRON);
        registerScrollable(this.powerMultiplierBox, ScrollSection.IRON);
        registerScrollable(this.cooldownSecondsBox, ScrollSection.IRON);
        registerScrollable(this.craftingButton, ScrollSection.IRON);
        this.ironContentBottomBase = this.craftingButton.getY() + this.craftingButton.getHeight() + 10;

        // Mage Additions: linked absolute/multiplier control. Whichever field is
        // edited last becomes the persisted authority for future-proof behaviour.
        int mageRowWidth = Math.min(MAGE_ROW_WIDTH, this.columnWidth);
        int mageControlWidth = mageRowWidth - SMALL_RESET_WIDTH - SMALL_CONTROL_GAP;
        int mageResetX = mageFieldX + mageControlWidth + SMALL_CONTROL_GAP;

        y = this.contentY;
        this.mageOverridesButton = addRenderableWidget(Button.builder(mageOverridesLabel(), b -> {
            this.mageOverridesEnabled = !this.mageOverridesEnabled;
            refreshBehaviorButtons();
            setEditingEnabled(this.canEdit);
        }).bounds(mageFieldX, y, mageRowWidth, FIELD_HEIGHT).build());
        y += this.rowGap;

        // CASTING section header occupies this row; controls begin on the next row.
        y += this.rowGap;

        this.castResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.castControl.resetToDefault();
            updateCastControlPresentation();
        }).bounds(mageFieldX, y, mageRowWidth, FIELD_HEIGHT).build());
        y += this.rowGap;

        // Original/reference row is render-only.
        y += this.rowGap;

        this.castValueBox = numericBox(mageFieldX, y, format(this.originalCastTimeLevelOne), mageRowWidth);
        y += this.rowGap;
        this.castMultiplierBox = numericBox(mageFieldX, y, this.originalCastTimeLevelOne == 0.0 ? "N/A" : "1", mageRowWidth);

        this.castControl = new LinkedNumericOverrideControl(
                this.castValueBox,
                this.castMultiplierBox,
                this.originalCastTimeLevelOne,
                0.0,
                1_000_000.0,
                0.0,
                1_000_000.0,
                this::updateCastControlPresentation
        );

        // MOVEMENT section.
        y += this.rowGap;
        y += this.rowGap;

        this.movementButton = addRenderableWidget(Button.builder(movementLabel(), b -> cycleMovementMode())
                .bounds(mageFieldX, y, mageControlWidth, FIELD_HEIGHT).build());
        this.movementResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> resetMovementMode())
                .bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.movementMultiplierBox = numericBox(mageFieldX, y, "0.5", mageControlWidth);
        this.movementMultiplierResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.movementMultiplierBox.setValue("0.5");
        }).bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        int heightValueX = mageFieldX + HEIGHT_TOGGLE_WIDTH + SMALL_CONTROL_GAP;
        int heightValueWidth = Math.max(44, mageRowWidth - HEIGHT_TOGGLE_WIDTH - SMALL_RESET_WIDTH - SMALL_CONTROL_GAP * 2);
        this.maxHeightToggleButton = addRenderableWidget(Button.builder(maxHeightToggleLabel(), b -> {
            this.maxHeightEnabled = !this.maxHeightEnabled;
            refreshBehaviorButtons();
            setEditingEnabled(this.canEdit);
        }).bounds(mageFieldX, y, HEIGHT_TOGGLE_WIDTH, FIELD_HEIGHT).build());
        this.maxHeightAboveGroundBox = numericBox(heightValueX, y, "10", heightValueWidth);
        this.maxHeightResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.maxHeightEnabled = false;
            this.maxHeightAboveGroundBox.setValue("10");
            refreshBehaviorButtons();
            setEditingEnabled(this.canEdit);
        }).bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());

        // TARGETING section.
        y += this.rowGap;
        y += this.rowGap;

        // Original range is render-only. It is supplied by the server because
        // Iron's passes this value dynamically to its generic target helper.
        y += this.rowGap;

        this.rangeValueBox = numericBox(mageFieldX, y, format(this.originalTargetRange), mageControlWidth);
        this.rangeResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.rangeControl.resetToDefault();
            updateRangeControlPresentation();
        }).bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.rangeMultiplierBox = numericBox(mageFieldX, y, "1", mageControlWidth);
        this.rangeControl = new LinkedNumericOverrideControl(
                this.rangeValueBox,
                this.rangeMultiplierBox,
                this.originalTargetRange,
                0.0,
                1_000_000.0,
                0.0,
                1_000_000.0,
                this::updateRangeControlPresentation
        );
        y += this.rowGap;

        this.lineOfSightButton = addRenderableWidget(Button.builder(lineOfSightLabel(), b -> toggleLineOfSight())
                .bounds(mageFieldX, y, mageControlWidth, FIELD_HEIGHT).build());
        this.lineOfSightResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> resetLineOfSight())
                .bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.minCastDistanceBox = numericBox(mageFieldX, y, format(this.originalMinCastDistance), mageControlWidth);
        this.minCastDistanceBox.setResponder(value -> {
            if (!this.updatingBehaviorFields) {
                this.minCastDistanceOverrideActive = true;
                if (this.minCastDistanceResetButton != null) this.minCastDistanceResetButton.active = this.canEdit && this.mageOverridesEnabled;
            }
        });
        this.minCastDistanceResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> resetMinCastDistance())
                .bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());
        y += this.rowGap;

        this.maxCastDistanceBox = numericBox(mageFieldX, y, format(this.originalMaxCastDistance), mageControlWidth);
        this.maxCastDistanceBox.setResponder(value -> {
            if (!this.updatingBehaviorFields) {
                this.maxCastDistanceOverrideActive = true;
                if (this.maxCastDistanceResetButton != null) this.maxCastDistanceResetButton.active = this.canEdit && this.mageOverridesEnabled;
            }
        });
        this.maxCastDistanceResetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), b -> resetMaxCastDistance())
                .bounds(mageResetX, y, SMALL_RESET_WIDTH, FIELD_HEIGHT).build());

        y += this.rowGap * 2;
        if (this.spell.getSpellId().equals("irons_spellbooks:counterspell")) {
            this.counterspellButton = addRenderableWidget(Button.builder(Component.literal("Counterspell Rework Settings..."), b -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new CounterspellEditorScreen(this));
                }
            }).bounds(this.rightColumnX, y, this.columnWidth, FIELD_HEIGHT).build());
        } else {
            this.counterspellButton = null;
        }

        registerScrollable(this.mageOverridesButton, ScrollSection.MAGE);
        registerScrollable(this.castResetButton, ScrollSection.MAGE);
        registerScrollable(this.castValueBox, ScrollSection.MAGE);
        registerScrollable(this.castMultiplierBox, ScrollSection.MAGE);
        registerScrollable(this.movementButton, ScrollSection.MAGE);
        registerScrollable(this.movementResetButton, ScrollSection.MAGE);
        registerScrollable(this.movementMultiplierBox, ScrollSection.MAGE);
        registerScrollable(this.movementMultiplierResetButton, ScrollSection.MAGE);
        registerScrollable(this.maxHeightToggleButton, ScrollSection.MAGE);
        registerScrollable(this.maxHeightAboveGroundBox, ScrollSection.MAGE);
        registerScrollable(this.maxHeightResetButton, ScrollSection.MAGE);
        registerScrollable(this.rangeValueBox, ScrollSection.MAGE);
        registerScrollable(this.rangeResetButton, ScrollSection.MAGE);
        registerScrollable(this.rangeMultiplierBox, ScrollSection.MAGE);
        registerScrollable(this.lineOfSightButton, ScrollSection.MAGE);
        registerScrollable(this.lineOfSightResetButton, ScrollSection.MAGE);
        registerScrollable(this.minCastDistanceBox, ScrollSection.MAGE);
        registerScrollable(this.minCastDistanceResetButton, ScrollSection.MAGE);
        registerScrollable(this.maxCastDistanceBox, ScrollSection.MAGE);
        registerScrollable(this.maxCastDistanceResetButton, ScrollSection.MAGE);
        registerScrollable(this.counterspellButton, ScrollSection.MAGE);

        int lastMageWidgetBottom = this.maxCastDistanceResetButton.getY() + this.maxCastDistanceResetButton.getHeight();
        if (this.counterspellButton != null) {
            lastMageWidgetBottom = Math.max(lastMageWidgetBottom, this.counterspellButton.getY() + this.counterspellButton.getHeight());
        }
        // Leave enough room to scroll the optional warning/help text fully into view.
        this.mageContentBottomBase = lastMageWidgetBottom + this.rowGap * 2 + 34;

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
        setEditingEnabled(false);
        updateCastControlPresentation();
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

    private void registerScrollable(AbstractWidget widget, ScrollSection section) {
        if (widget == null) return;
        this.scrollBaseY.put(widget, widget.getY());
        this.scrollSections.put(widget, section);
    }

    private int scrollFor(ScrollSection section) {
        return section == ScrollSection.IRON ? this.ironScroll : this.mageScroll;
    }

    private void setScroll(ScrollSection section, int value) {
        int clamped = Math.max(0, Math.min(maxScroll(section), value));
        if (section == ScrollSection.IRON) {
            this.ironScroll = clamped;
        } else {
            this.mageScroll = clamped;
        }
        updateScrollableWidgetPositions();
    }

    private int maxScroll(ScrollSection section) {
        int contentBottom = section == ScrollSection.IRON ? this.ironContentBottomBase : this.mageContentBottomBase;
        return Math.max(0, contentBottom - this.scrollViewportBottom);
    }

    private boolean sectionVisible(ScrollSection section) {
        if (!this.narrowLayout) return true;
        return section == ScrollSection.MAGE ? this.showMageSection : !this.showMageSection;
    }

    private int sectionLeft(ScrollSection section) {
        if (this.narrowLayout) return this.panelLeft;
        return section == ScrollSection.IRON ? this.leftColumnX - 8 : this.rightColumnX - 8;
    }

    private int sectionRight(ScrollSection section) {
        if (this.narrowLayout) return this.panelRight;
        return section == ScrollSection.IRON
                ? this.leftColumnX + this.columnWidth + 8
                : this.rightColumnX + this.columnWidth + 8;
    }

    private ScrollSection sectionAt(double mouseX, double mouseY) {
        if (mouseY < this.scrollViewportTop || mouseY >= this.scrollViewportBottom) return null;

        if (this.narrowLayout) {
            if (mouseX >= this.panelLeft && mouseX < this.panelRight) {
                return this.showMageSection ? ScrollSection.MAGE : ScrollSection.IRON;
            }
            return null;
        }

        if (mouseX >= sectionLeft(ScrollSection.IRON) && mouseX < sectionRight(ScrollSection.IRON)) {
            return ScrollSection.IRON;
        }
        if (mouseX >= sectionLeft(ScrollSection.MAGE) && mouseX < sectionRight(ScrollSection.MAGE)) {
            return ScrollSection.MAGE;
        }
        return null;
    }

    private void updateScrollableWidgetPositions() {
        for (Map.Entry<AbstractWidget, Integer> entry : this.scrollBaseY.entrySet()) {
            AbstractWidget widget = entry.getKey();
            ScrollSection section = this.scrollSections.get(widget);
            if (section == null) continue;

            int y = entry.getValue() - scrollFor(section);
            widget.setY(y);
            boolean fullyInsideViewport = y >= this.scrollViewportTop
                    && y + widget.getHeight() <= this.scrollViewportBottom;
            widget.visible = sectionVisible(section) && fullyInsideViewport;
        }
    }

    private int scrollTrackLeft(ScrollSection section) {
        return sectionRight(section) - SCROLLBAR_WIDTH - 2;
    }

    private int scrollTrackTop() {
        return this.scrollViewportTop;
    }

    private int scrollTrackBottom() {
        return this.scrollViewportBottom;
    }

    private int scrollThumbHeight(ScrollSection section) {
        int trackHeight = scrollTrackBottom() - scrollTrackTop();
        int max = maxScroll(section);
        if (max <= 0) return trackHeight;
        int contentHeight = trackHeight + max;
        return Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, Math.min(trackHeight,
                (int) Math.round(trackHeight * (trackHeight / (double) contentHeight))));
    }

    private int scrollThumbTop(ScrollSection section) {
        int max = maxScroll(section);
        if (max <= 0) return scrollTrackTop();
        int travel = (scrollTrackBottom() - scrollTrackTop()) - scrollThumbHeight(section);
        return scrollTrackTop() + (int) Math.round(travel * (scrollFor(section) / (double) max));
    }

    private void setScrollFromThumbTop(ScrollSection section, double thumbTop) {
        int max = maxScroll(section);
        if (max <= 0) {
            setScroll(section, 0);
            return;
        }
        int travel = (scrollTrackBottom() - scrollTrackTop()) - scrollThumbHeight(section);
        if (travel <= 0) {
            setScroll(section, 0);
            return;
        }
        double clamped = Math.max(scrollTrackTop(), Math.min(scrollTrackTop() + travel, thumbTop));
        double fraction = (clamped - scrollTrackTop()) / travel;
        setScroll(section, (int) Math.round(fraction * max));
    }

    private void renderScrollbar(GuiGraphics graphics, ScrollSection section) {
        if (!sectionVisible(section) || maxScroll(section) <= 0) return;

        int left = scrollTrackLeft(section);
        int top = scrollTrackTop();
        int bottom = scrollTrackBottom();
        int thumbTop = scrollThumbTop(section);
        int thumbHeight = scrollThumbHeight(section);

        graphics.fill(left, top, left + SCROLLBAR_WIDTH, bottom, 0x66000000);
        int thumbColor = this.scrollBarDragging && this.scrollBarDragSection == section ? 0xFFE0E0E0 : 0xFF9A9A9A;
        graphics.fill(left, thumbTop, left + SCROLLBAR_WIDTH, thumbTop + thumbHeight, thumbColor);
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

        setVisible(this.castResetButton, showMage);
        if (this.castControl != null) this.castControl.setVisible(showMage);
        setVisible(this.movementButton, showMage);
        setVisible(this.movementResetButton, showMage);
        setVisible(this.movementMultiplierBox, showMage);
        setVisible(this.movementMultiplierResetButton, showMage);
        setVisible(this.maxHeightToggleButton, showMage);
        setVisible(this.maxHeightAboveGroundBox, showMage);
        setVisible(this.maxHeightResetButton, showMage);
        if (this.rangeControl != null) this.rangeControl.setVisible(showMage);
        setVisible(this.rangeResetButton, showMage);
        setVisible(this.lineOfSightButton, showMage);
        setVisible(this.lineOfSightResetButton, showMage);
        setVisible(this.minCastDistanceBox, showMage);
        setVisible(this.minCastDistanceResetButton, showMage);
        setVisible(this.maxCastDistanceBox, showMage);
        setVisible(this.maxCastDistanceResetButton, showMage);
        setVisible(this.counterspellButton, showMage);

        if (this.ironsTabButton != null) {
            this.ironsTabButton.active = this.showMageSection;
        }
        if (this.mageTabButton != null) {
            this.mageTabButton.active = !this.showMageSection;
        }

        updateScrollableWidgetPositions();
    }

    private static void setVisible(net.minecraft.client.gui.components.AbstractWidget widget, boolean visible) {
        if (widget != null) {
            widget.visible = visible;
        }
    }

    private EditBox numericBox(int x, int y, String value) {
        return numericBox(x, y, value, FIELD_WIDTH);
    }

    private EditBox numericBox(int x, int y, String value, int width) {
        EditBox box = addRenderableWidget(new EditBox(this.font, x, y, width, FIELD_HEIGHT, Component.empty()));
        box.setMaxLength(32);
        box.setValue(value);
        return box;
    }

    private void updateCastControlPresentation() {
        if (this.castResetButton == null || this.castControl == null) return;

        this.castResetButton.setMessage(Component.literal("Reset"));
        this.castResetButton.active = this.canEdit && this.mageOverridesEnabled
                && this.castControl.authority() != LinkedNumericOverrideControl.Authority.DEFAULT;
    }

    private void updateRangeControlPresentation() {
        if (this.rangeResetButton == null || this.rangeControl == null) return;
        this.rangeResetButton.active = this.canEdit && this.mageOverridesEnabled
                && this.rangeControl.authority() != LinkedNumericOverrideControl.Authority.DEFAULT;
    }

    private void setEditingEnabled(boolean enabled) {
        // Iron's native config remains editable independently of Mage Additions.
        this.enabledButton.active = enabled;
        this.schoolButton.active = enabled;
        this.maxLevelBox.active = enabled;
        this.rarityButton.active = enabled;
        this.manaMultiplierBox.active = enabled;
        this.powerMultiplierBox.active = enabled;
        this.cooldownSecondsBox.active = enabled;
        this.craftingButton.active = enabled;

        if (this.mageOverridesButton != null) this.mageOverridesButton.active = enabled;
        boolean mageEnabled = enabled && this.mageOverridesEnabled;

        if (this.castControl != null) this.castControl.setActive(mageEnabled);
        if (this.rangeControl != null) this.rangeControl.setActive(mageEnabled);
        if (this.rangeResetButton != null) {
            this.rangeResetButton.active = mageEnabled
                    && this.rangeControl != null
                    && this.rangeControl.authority() != LinkedNumericOverrideControl.Authority.DEFAULT;
        }
        if (this.movementButton != null) this.movementButton.active = mageEnabled;
        if (this.movementResetButton != null) this.movementResetButton.active = mageEnabled && !"default".equals(this.movementMode);
        if (this.movementMultiplierBox != null) this.movementMultiplierBox.active = mageEnabled && "slowed".equals(this.movementMode);
        if (this.movementMultiplierResetButton != null) this.movementMultiplierResetButton.active = mageEnabled && "slowed".equals(this.movementMode);
        if (this.maxHeightToggleButton != null) this.maxHeightToggleButton.active = mageEnabled;
        if (this.maxHeightAboveGroundBox != null) this.maxHeightAboveGroundBox.active = mageEnabled && this.maxHeightEnabled;
        if (this.maxHeightResetButton != null) this.maxHeightResetButton.active = mageEnabled
                && (this.maxHeightEnabled || !"10".equals(this.maxHeightAboveGroundBox.getValue().trim()));
        if (this.lineOfSightButton != null) this.lineOfSightButton.active = mageEnabled;
        if (this.lineOfSightResetButton != null) this.lineOfSightResetButton.active = mageEnabled && this.lineOfSightOverrideActive;
        if (this.minCastDistanceBox != null) this.minCastDistanceBox.active = mageEnabled;
        if (this.minCastDistanceResetButton != null) this.minCastDistanceResetButton.active = mageEnabled && this.minCastDistanceOverrideActive;
        if (this.maxCastDistanceBox != null) this.maxCastDistanceBox.active = mageEnabled;
        if (this.maxCastDistanceResetButton != null) this.maxCastDistanceResetButton.active = mageEnabled && this.maxCastDistanceOverrideActive;
        if (this.counterspellButton != null) this.counterspellButton.active = enabled;

        this.saveButton.active = enabled;
        updateCastControlPresentation();
        updateRangeControlPresentation();
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

        this.mageOverridesEnabled = false;
        this.castControl.resetToDefault();
        this.rangeControl.resetToDefault();
        this.movementMode = "default";
        this.movementMultiplierBox.setValue("0.5");
        this.maxHeightEnabled = false;
        this.maxHeightAboveGroundBox.setValue("10");
        this.lineOfSightValue = this.originalLineOfSight;
        this.lineOfSightOverrideActive = false;
        this.minCastDistanceOverrideActive = false;
        this.maxCastDistanceOverrideActive = false;
        setBehaviorBoxValue(this.minCastDistanceBox, format(this.originalMinCastDistance));
        setBehaviorBoxValue(this.maxCastDistanceBox, format(this.originalMaxCastDistance));
        refreshBehaviorButtons();
        updateCastControlPresentation();
        updateRangeControlPresentation();
        setEditingEnabled(this.canEdit);
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

            SpellOverrideConfigService.RuleState castRule = this.castControl.toRuleState("Cast time");
            SpellOverrideConfigService.RuleState rangeRule = this.rangeControl.toRuleState("Range");
            double movementMultiplier = parseDouble(this.movementMultiplierBox, "Movement multiplier", 0.0, 10.0);
            double maxHeightAboveGround = parseDouble(this.maxHeightAboveGroundBox, "Maximum height above ground", 0.0, 1_000_000.0);
            double minCastDistance = parseDouble(this.minCastDistanceBox, "Minimum cast distance", 0.0, 1_000_000.0);
            double maxCastDistance = parseDouble(this.maxCastDistanceBox, "Maximum cast distance", 0.0, 1_000_000.0);
            if (minCastDistance > maxCastDistance) {
                throw new IllegalArgumentException("Minimum cast distance cannot be greater than maximum cast distance.");
            }

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
                    this.mageOverridesEnabled,
                    castRule.enabled() ? castRule.mode() : "off",
                    castRule.enabled() ? castRule.value() : 0.0,
                    rangeRule.enabled() ? rangeRule.mode() : "off",
                    rangeRule.enabled() ? rangeRule.value() : 0.0,
                    this.movementMode,
                    movementMultiplier,
                    this.maxHeightEnabled,
                    maxHeightAboveGround,
                    this.lineOfSightOverrideActive,
                    this.lineOfSightValue,
                    this.minCastDistanceOverrideActive,
                    minCastDistance,
                    this.maxCastDistanceOverrideActive,
                    maxCastDistance
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

        this.enabledButton.setMessage(toggleLabel("Enabled", this.enabled));
        this.schoolButton.setMessage(schoolLabel());
        this.maxLevelBox.setValue(Integer.toString(snapshot.maxLevel()));
        this.rarityButton.setMessage(rarityLabel());
        this.manaMultiplierBox.setValue(format(snapshot.manaMultiplier()));
        this.powerMultiplierBox.setValue(format(snapshot.powerMultiplier()));
        this.cooldownSecondsBox.setValue(format(snapshot.cooldownSeconds()));
        this.craftingButton.setMessage(toggleLabel("Craftable", this.allowCrafting));

        this.mageOverridesEnabled = snapshot.mageOverridesEnabled();
        this.castControl.applyState(snapshot.castMode(), snapshot.castValue());
        this.originalTargetRange = snapshot.originalTargetRange();
        this.rangeControl.applyStateWithOriginal(
                this.originalTargetRange,
                snapshot.rangeMode(),
                snapshot.rangeValue()
        );
        this.movementMode = normalizeMovementMode(snapshot.movementMode());
        this.movementMultiplierBox.setValue(format(snapshot.movementMultiplier()));
        this.maxHeightEnabled = snapshot.maxHeightEnabled();
        this.maxHeightAboveGroundBox.setValue(format(snapshot.maxHeightAboveGround()));

        this.originalLineOfSight = snapshot.originalLineOfSight();
        this.lineOfSightOverrideActive = snapshot.hasLineOfSightOverride();
        this.lineOfSightValue = snapshot.lineOfSightValue();
        this.originalMinCastDistance = snapshot.originalMinCastDistance();
        this.originalMaxCastDistance = snapshot.originalMaxCastDistance();
        this.minCastDistanceOverrideActive = snapshot.hasMinCastDistance();
        this.maxCastDistanceOverrideActive = snapshot.hasMaxCastDistance();
        setBehaviorBoxValue(this.minCastDistanceBox, format(snapshot.minCastDistance()));
        setBehaviorBoxValue(this.maxCastDistanceBox, format(snapshot.maxCastDistance()));
        refreshBehaviorButtons();

        setEditingEnabled(this.canEdit);
        updateSectionVisibility();

        ChatFormatting color = snapshot.success() ? ChatFormatting.GREEN : ChatFormatting.RED;
        this.status = Component.literal(snapshot.message()).withStyle(color);
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


    private void cycleMovementMode() {
        this.movementMode = switch (this.movementMode) {
            case "default" -> "normal";
            case "normal" -> "slowed";
            case "slowed" -> "rooted";
            default -> "default";
        };
        refreshBehaviorButtons();
        setEditingEnabled(this.canEdit);
    }

    private void resetMovementMode() {
        this.movementMode = "default";
        refreshBehaviorButtons();
        setEditingEnabled(this.canEdit);
    }

    private void toggleLineOfSight() {
        this.lineOfSightValue = !this.lineOfSightValue;
        this.lineOfSightOverrideActive = true;
        refreshBehaviorButtons();
        setEditingEnabled(this.canEdit);
    }

    private void resetLineOfSight() {
        this.lineOfSightValue = this.originalLineOfSight;
        this.lineOfSightOverrideActive = false;
        refreshBehaviorButtons();
        setEditingEnabled(this.canEdit);
    }

    private void resetMinCastDistance() {
        this.minCastDistanceOverrideActive = false;
        setBehaviorBoxValue(this.minCastDistanceBox, format(this.originalMinCastDistance));
        setEditingEnabled(this.canEdit);
    }

    private void resetMaxCastDistance() {
        this.maxCastDistanceOverrideActive = false;
        setBehaviorBoxValue(this.maxCastDistanceBox, format(this.originalMaxCastDistance));
        setEditingEnabled(this.canEdit);
    }

    private void setBehaviorBoxValue(EditBox box, String value) {
        this.updatingBehaviorFields = true;
        try {
            box.setValue(value);
        } finally {
            this.updatingBehaviorFields = false;
        }
    }

    private void refreshBehaviorButtons() {
        if (this.mageOverridesButton != null) this.mageOverridesButton.setMessage(mageOverridesLabel());
        if (this.movementButton != null) this.movementButton.setMessage(movementLabel());
        if (this.maxHeightToggleButton != null) this.maxHeightToggleButton.setMessage(maxHeightToggleLabel());
        if (this.lineOfSightButton != null) this.lineOfSightButton.setMessage(lineOfSightLabel());
    }

    private Component mageOverridesLabel() {
        return Component.literal(this.mageOverridesEnabled ? "Enabled" : "Disabled")
                .withStyle(this.mageOverridesEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private Component movementLabel() {
        String value = switch (this.movementMode) {
            case "normal" -> "Normal";
            case "slowed" -> "Slowed";
            case "rooted" -> "Rooted";
            default -> "Default";
        };
        return Component.literal(value);
    }

    private Component maxHeightToggleLabel() {
        return Component.literal(this.maxHeightEnabled ? "Enabled" : "Disabled")
                .withStyle(this.maxHeightEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private Component lineOfSightLabel() {
        return Component.literal(this.lineOfSightValue ? "True" : "False");
    }

    private static String normalizeMovementMode(String mode) {
        if (mode == null) return "default";
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "normal", "slowed", "rooted" -> mode.trim().toLowerCase(Locale.ROOT);
            default -> "default";
        };
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        updateScrollableWidgetPositions();
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.spell.getDisplayName(this.minecraft == null ? null : this.minecraft.player), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, this.spell.getSpellId(), this.width / 2, 25, 0x888888);

        int panelBottom = Math.max(96, this.height - 38);

        if (this.narrowLayout) {
            graphics.fill(this.panelLeft, 70, this.panelRight, panelBottom, 0x55000000);

            if (this.showMageSection) {
                graphics.drawString(this.font, Component.literal("Mage Additions overrides").withStyle(ChatFormatting.LIGHT_PURPLE),
                        this.leftColumnX, 66, 0xFFFFFF);
                graphics.enableScissor(this.panelLeft, this.scrollViewportTop, this.panelRight, this.scrollViewportBottom);
                renderMageLabels(graphics, this.leftColumnX, this.mageScroll);
                graphics.disableScissor();
                renderScrollbar(graphics, ScrollSection.MAGE);
            } else {
                graphics.drawString(this.font, Component.literal("Iron's spell config").withStyle(ChatFormatting.AQUA),
                        this.leftColumnX, 66, 0xFFFFFF);
                graphics.enableScissor(this.panelLeft, this.scrollViewportTop, this.panelRight, this.scrollViewportBottom);
                renderIronLabels(graphics, this.leftColumnX, this.ironScroll);
                graphics.disableScissor();
                renderScrollbar(graphics, ScrollSection.IRON);
            }
        } else {
            graphics.fill(this.leftColumnX - 8, 56, this.leftColumnX + this.columnWidth + 8, panelBottom, 0x55000000);
            graphics.fill(this.rightColumnX - 8, 56, this.rightColumnX + this.columnWidth + 8, panelBottom, 0x55000000);

            graphics.drawString(this.font, Component.literal("Iron's spell config").withStyle(ChatFormatting.AQUA),
                    this.leftColumnX, 62, 0xFFFFFF);
            graphics.drawString(this.font, Component.literal("Mage Additions overrides").withStyle(ChatFormatting.LIGHT_PURPLE),
                    this.rightColumnX, 62, 0xFFFFFF);

            graphics.enableScissor(sectionLeft(ScrollSection.IRON), this.scrollViewportTop, sectionRight(ScrollSection.IRON), this.scrollViewportBottom);
            renderIronLabels(graphics, this.leftColumnX, this.ironScroll);
            graphics.disableScissor();
            graphics.enableScissor(sectionLeft(ScrollSection.MAGE), this.scrollViewportTop, sectionRight(ScrollSection.MAGE), this.scrollViewportBottom);
            renderMageLabels(graphics, this.rightColumnX, this.mageScroll);
            graphics.disableScissor();
            renderScrollbar(graphics, ScrollSection.IRON);
            renderScrollbar(graphics, ScrollSection.MAGE);
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

    private void renderIronLabels(GuiGraphics graphics, int x, int scroll) {
        int y = this.contentY + 6 - scroll;
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

    private void renderMageLabels(GuiGraphics graphics, int x, int scroll) {
        int y = this.contentY + 6 - scroll;

        drawLabel(graphics, x, y, "Overrides");
        y += this.rowGap;
        graphics.drawString(this.font, Component.literal("CASTING").withStyle(ChatFormatting.LIGHT_PURPLE), x, y, 0xFFFFFF);
        y += this.rowGap;
        drawLabel(graphics, x, y, "Cast time override");
        y += this.rowGap;

        Component original = Component.literal("Original (level 1): " + format(this.originalCastTimeLevelOne) + " ticks")
                .withStyle(ChatFormatting.DARK_GRAY);
        graphics.drawString(this.font, original, x, y, 0xFFFFFF);
        y += this.rowGap;

        drawLabel(graphics, x, y, "Value (ticks)");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Multiplier");
        y += this.rowGap;

        graphics.drawString(this.font, Component.literal("MOVEMENT").withStyle(ChatFormatting.LIGHT_PURPLE), x, y, 0xFFFFFF);
        y += this.rowGap;
        drawLabel(graphics, x, y, "Movement while casting");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Movement multiplier");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Max height above ground");
        y += this.rowGap;

        graphics.drawString(this.font, Component.literal("TARGETING").withStyle(ChatFormatting.LIGHT_PURPLE), x, y, 0xFFFFFF);
        y += this.rowGap;
        graphics.drawString(
                this.font,
                Component.literal("Original range: " + format(this.originalTargetRange) + " blocks")
                        .withStyle(ChatFormatting.DARK_GRAY),
                x,
                y,
                0xFFFFFF
        );
        y += this.rowGap;
        drawLabel(graphics, x, y, "Range value (blocks)");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Range multiplier");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Require line of sight");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Min cast distance (blocks)");
        y += this.rowGap;
        drawLabel(graphics, x, y, "Max cast distance (blocks)");
        y += this.rowGap;

        int helpY = y + this.rowGap;
        if (this.counterspellButton != null) {
            helpY += FIELD_HEIGHT + 8;
        }
        int helpWidth = Math.max(160, this.columnWidth);

        if (!CastTimeOverrides.balanceTweaksEnabled()) {
            graphics.drawWordWrap(
                    this.font,
                    Component.literal("Balance Tweaks is OFF. Saved overrides will apply when the module is enabled.")
                            .withStyle(ChatFormatting.YELLOW),
                    x,
                    helpY,
                    helpWidth,
                    0xFFFFFF
            );
            helpY += 24;
        }

        if (this.spell.getCastType().name().equals("INSTANT")
                && this.castControl.authority() != LinkedNumericOverrideControl.Authority.DEFAULT) {
            graphics.drawWordWrap(
                    this.font,
                    Component.literal("Instant-spell delays require explicit support or allow_instant_spell_delays.")
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ScrollSection section : ScrollSection.values()) {
                if (!sectionVisible(section) || maxScroll(section) <= 0) continue;
                int left = scrollTrackLeft(section);
                if (mouseX >= left - 1 && mouseX < left + SCROLLBAR_WIDTH + 1
                        && mouseY >= scrollTrackTop() && mouseY < scrollTrackBottom()) {
                    int thumbTop = scrollThumbTop(section);
                    int thumbHeight = scrollThumbHeight(section);
                    if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                        this.scrollBarGrabOffset = mouseY - thumbTop;
                    } else {
                        this.scrollBarGrabOffset = thumbHeight / 2.0;
                        setScrollFromThumbTop(section, mouseY - this.scrollBarGrabOffset);
                    }
                    this.scrollBarDragging = true;
                    this.scrollBarDragSection = section;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.scrollBarDragging && this.scrollBarDragSection != null) {
            setScrollFromThumbTop(this.scrollBarDragSection, mouseY - this.scrollBarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.scrollBarDragging) {
            this.scrollBarDragging = false;
            this.scrollBarDragSection = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScrollSection section = sectionAt(mouseX, mouseY);
        if (section != null && maxScroll(section) > 0 && scrollY != 0.0) {
            int delta = (int) Math.round(scrollY * SCROLL_WHEEL_PIXELS);
            if (delta == 0) delta = scrollY > 0.0 ? 1 : -1;
            setScroll(section, scrollFor(section) - delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static String format(double value) {
        return LinkedNumericOverrideControl.format(value);
    }

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 3)) + "...";
    }
}
