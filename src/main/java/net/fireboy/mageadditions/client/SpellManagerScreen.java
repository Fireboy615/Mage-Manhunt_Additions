package net.fireboy.mageadditions.client;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * First stage of Mage Additions' spell balancing UI.
 *
 * This screen intentionally does not maintain a hard-coded spell list. It reads
 * Iron's live spell registry, so Iron's-compatible addon spells registered in
 * that registry automatically appear here too.
 *
 * The list is auto-discovered from Iron's live registry. Selecting a spell
 * shows a summary; the Edit Selected Spell button opens the functional editor.
 */
public final class SpellManagerScreen extends Screen {
    private static final int OUTER_MARGIN = 20;
    private static final int SEARCH_WIDTH = 300;
    private static final int SEARCH_HEIGHT = 20;
    private static final int ROW_HEIGHT = 30;
    private static final int ICON_SIZE = 16;

    private final Screen parent;

    private EditBox searchBox;
    private List<AbstractSpell> allSpells = List.of();
    private List<AbstractSpell> filteredSpells = List.of();
    private AbstractSpell selectedSpell;
    private int scrollOffset;
    private Button editButton;

    public SpellManagerScreen(Screen parent) {
        super(Component.literal("Mage Additions - Spell Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int searchX = Math.max(OUTER_MARGIN, this.width / 2 - SEARCH_WIDTH / 2);
        this.searchBox = this.addRenderableWidget(
                new EditBox(
                        this.font,
                        searchX,
                        38,
                        Math.min(SEARCH_WIDTH, this.width - OUTER_MARGIN * 2),
                        SEARCH_HEIGHT,
                        Component.literal("Search spells")
                )
        );
        this.searchBox.setMaxLength(128);
        this.searchBox.setHint(Component.literal("Search name, ID, school or mod..."));
        this.searchBox.setResponder(value -> rebuildFilter());

        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), button -> this.onClose())
                        .bounds(OUTER_MARGIN, this.height - 28, 90, 20)
                        .build()
        );

        this.editButton = this.addRenderableWidget(
                Button.builder(Component.literal("Edit Selected Spell"), button -> openSelectedEditor())
                        .bounds(this.width - OUTER_MARGIN - 150, this.height - 28, 150, 20)
                        .build()
        );

        loadSpellRegistry();
        rebuildFilter();
        updateEditButton();
    }

    /**
     * Snapshot the live registry. This includes addon spells which register into
     * Iron's SpellRegistry, without Mage Additions needing to know their IDs.
     */
    private void loadSpellRegistry() {
        List<AbstractSpell> discovered = new ArrayList<>(SpellRegistry.REGISTRY.stream().toList());
        discovered.sort(
                Comparator
                        .comparing((AbstractSpell spell) -> displayName(spell).toLowerCase(Locale.ROOT))
                        .thenComparing(AbstractSpell::getSpellId)
        );
        this.allSpells = List.copyOf(discovered);
    }

    private void rebuildFilter() {
        String query = this.searchBox == null
                ? ""
                : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        List<AbstractSpell> matches;
        if (query.isEmpty()) {
            matches = this.allSpells;
        } else {
            matches = this.allSpells.stream()
                    .filter(spell -> matchesSearch(spell, query))
                    .toList();
        }

        this.filteredSpells = matches;
        this.scrollOffset = 0;

        if (this.selectedSpell == null || !this.filteredSpells.contains(this.selectedSpell)) {
            this.selectedSpell = this.filteredSpells.isEmpty() ? null : this.filteredSpells.getFirst();
        }
        updateEditButton();
    }

    private void updateEditButton() {
        if (this.editButton != null) {
            this.editButton.active = this.selectedSpell != null;
        }
    }

    private void openSelectedEditor() {
        if (this.minecraft != null && this.selectedSpell != null) {
            this.minecraft.setScreen(new SpellEditorScreen(this, this.selectedSpell));
        }
    }

    private boolean matchesSearch(AbstractSpell spell, String query) {
        String name = displayName(spell).toLowerCase(Locale.ROOT);
        String id = spell.getSpellId().toLowerCase(Locale.ROOT);
        String namespace = spell.getSpellResource().getNamespace().toLowerCase(Locale.ROOT);
        String school = spell.getSchoolType().getDisplayName().getString().toLowerCase(Locale.ROOT);

        return name.contains(query)
                || id.contains(query)
                || namespace.contains(query)
                || school.contains(query);
    }

    private String displayName(AbstractSpell spell) {
        return spell.getDisplayName(this.minecraft == null ? null : this.minecraft.player).getString();
    }

    private int listLeft() {
        return OUTER_MARGIN;
    }

    private int listRight() {
        int preferred = Math.min(360, Math.max(250, this.width / 2 - 30));
        return Math.min(this.width - OUTER_MARGIN - 240, listLeft() + preferred);
    }

    private int listTop() {
        return 74;
    }

    private int listBottom() {
        return this.height - 42;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private int maxScrollOffset() {
        return Math.max(0, this.filteredSpells.size() - visibleRows());
    }

    private void clampScroll() {
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScrollOffset()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.literal(this.filteredSpells.size() + " / " + this.allSpells.size() + " registered spells"),
                this.width / 2,
                26,
                0x909090
        );

        renderSpellList(graphics, mouseX, mouseY);
        renderSelectedSpell(graphics);

        graphics.drawString(
                this.font,
                Component.literal("Mouse wheel: scroll   •   Click a spell: inspect   •   Edit Selected Spell: configure")
                        .withStyle(ChatFormatting.DARK_GRAY),
                122,
                this.height - 23,
                0xFFFFFF
        );
    }

    private void renderSpellList(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = listLeft();
        int right = listRight();
        int top = listTop();
        int bottom = listBottom();

        graphics.fill(left - 1, top - 17, right + 1, bottom + 1, 0x66000000);
        graphics.drawString(this.font, Component.literal("Spells"), left + 5, top - 13, 0xFFFFFF);

        graphics.enableScissor(left, top, right, bottom);

        int end = Math.min(this.filteredSpells.size(), this.scrollOffset + visibleRows() + 1);
        for (int index = this.scrollOffset; index < end; index++) {
            AbstractSpell spell = this.filteredSpells.get(index);
            int row = index - this.scrollOffset;
            int y = top + row * ROW_HEIGHT;

            boolean selected = spell == this.selectedSpell;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;

            int background = selected
                    ? 0xAA3A506B
                    : hovered ? 0x88404040 : 0x55202020;
            graphics.fill(left, y, right, y + ROW_HEIGHT - 2, background);

            graphics.blit(
                    spell.getSpellIconResource(),
                    left + 6,
                    y + 6,
                    0,
                    0,
                    ICON_SIZE,
                    ICON_SIZE,
                    ICON_SIZE,
                    ICON_SIZE
            );

            int textX = left + 28;
            int availableWidth = Math.max(30, right - textX - 6);
            String name = trimToWidth(displayName(spell), availableWidth);
            String id = trimToWidth(spell.getSpellId(), availableWidth);

            int nameColor = IronsSpellConfigAccess.read(spell).enabled() ? 0xFFFFFF : 0x777777;
            graphics.drawString(this.font, name, textX, y + 4, nameColor);
            graphics.drawString(this.font, id, textX, y + 16, 0x888888);
        }

        graphics.disableScissor();
        renderScrollBar(graphics, left, right, top, bottom);

        if (this.filteredSpells.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("No spells match your search.").withStyle(ChatFormatting.GRAY),
                    (left + right) / 2,
                    top + 16,
                    0xFFFFFF
            );
        }
    }

    private void renderScrollBar(GuiGraphics graphics, int left, int right, int top, int bottom) {
        int max = maxScrollOffset();
        if (max <= 0) {
            return;
        }

        int trackLeft = right - 5;
        int trackTop = top + 2;
        int trackBottom = bottom - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(18, (int) Math.round(trackHeight * (visibleRows() / (double) this.filteredSpells.size())));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + (int) Math.round(travel * (this.scrollOffset / (double) max));

        graphics.fill(trackLeft, trackTop, right - 1, trackBottom, 0x66202020);
        graphics.fill(trackLeft, thumbY, right - 1, thumbY + thumbHeight, 0xCCAAAAAA);
    }

    private void renderSelectedSpell(GuiGraphics graphics) {
        int left = listRight() + 14;
        int right = this.width - OUTER_MARGIN;
        int top = listTop() - 17;
        int bottom = listBottom();

        graphics.fill(left, top, right, bottom, 0x66000000);

        if (this.selectedSpell == null) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Select a spell").withStyle(ChatFormatting.GRAY),
                    (left + right) / 2,
                    top + 18,
                    0xFFFFFF
            );
            return;
        }

        AbstractSpell spell = this.selectedSpell;
        IronsSpellConfigAccess.Settings config = IronsSpellConfigAccess.read(spell);

        int x = left + 10;
        int y = top + 10;

        graphics.blit(
                spell.getSpellIconResource(),
                x,
                y,
                0,
                0,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE
        );

        graphics.drawString(this.font, spell.getDisplayName(this.minecraft == null ? null : this.minecraft.player), x + 24, y + 1, 0xFFFFFF);
        graphics.drawString(this.font, spell.getSpellId(), x + 24, y + 12, 0x888888);
        y += 34;

        graphics.drawString(this.font, Component.literal("Detected source"), x, y, 0xA0A0A0);
        y += 12;
        drawValue(graphics, x, y, "Mod namespace", spell.getSpellResource().getNamespace());
        y += 13;
        drawValue(graphics, x, y, "School", spell.getSchoolType().getDisplayName().getString());
        y += 13;
        drawValue(graphics, x, y, "Cast type", spell.getCastType().name().toLowerCase(Locale.ROOT));
        y += 20;

        graphics.drawString(this.font, Component.literal("Iron's current spell config"), x, y, 0xA0A0A0);
        y += 13;
        drawValue(graphics, x, y, "Enabled", config.enabled() ? "Yes" : "No");
        y += 13;
        drawValue(graphics, x, y, "Max level", Integer.toString(config.maxLevel()));
        y += 13;
        drawValue(graphics, x, y, "Minimum rarity", config.minRarity().getDisplayName().getString());
        y += 13;
        drawValue(graphics, x, y, "Mana multiplier", formatDecimal(config.manaMultiplier()));
        y += 13;
        drawValue(graphics, x, y, "Power multiplier", formatDecimal(config.powerMultiplier()));
        y += 13;
        drawValue(graphics, x, y, "Cooldown", formatDecimal(config.cooldownSeconds()) + " s");
        y += 13;
        drawValue(graphics, x, y, "Craftable", config.allowCrafting() ? "Yes" : "No");
        y += 24;

        int availableWidth = Math.max(40, right - x - 10);
        graphics.drawWordWrap(
                this.font,
                Component.literal(
                        "Use Edit Selected Spell to change Iron's native spell config plus Mage Additions cast-time, mana and cooldown overrides."
                ).withStyle(ChatFormatting.GRAY),
                x,
                y,
                availableWidth,
                0xFFFFFF
        );
    }

    private void drawValue(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(this.font, Component.literal(label + ":").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
        int labelWidth = this.font.width(label + ": ");
        graphics.drawString(this.font, value, x + labelWidth, y, 0xFFFFFF);
    }

    private String trimToWidth(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }

        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        return this.font.plainSubstrByWidth(text, Math.max(0, width - suffixWidth)) + suffix;
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= listRight() - 7
                && mouseX < listRight()
                && mouseY >= listTop()
                && mouseY < listBottom()
                && maxScrollOffset() > 0) {

            double fraction = (mouseY - listTop()) / Math.max(1.0, listBottom() - listTop());
            this.scrollOffset = (int) Math.round(fraction * maxScrollOffset());
            clampScroll();
            return true;
        }

        if (button == 0
                && mouseX >= listLeft()
                && mouseX < listRight() - 7
                && mouseY >= listTop()
                && mouseY < listBottom()) {

            int row = (int) ((mouseY - listTop()) / ROW_HEIGHT);
            int index = this.scrollOffset + row;
            if (index >= 0 && index < this.filteredSpells.size()) {
                this.selectedSpell = this.filteredSpells.get(index);
                updateEditButton();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listLeft()
                && mouseX < listRight()
                && mouseY >= listTop()
                && mouseY < listBottom()) {

            if (scrollY > 0.0) {
                this.scrollOffset--;
            } else if (scrollY < 0.0) {
                this.scrollOffset++;
            }
            clampScroll();
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
}
