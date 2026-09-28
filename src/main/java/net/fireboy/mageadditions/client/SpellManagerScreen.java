package net.fireboy.mageadditions.client;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.fireboy.mageadditions.network.SpellConfigPayloads;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
    private static final int INFO_SCROLLBAR_WIDTH = 5;
    private static final int INFO_SCROLLBAR_MIN_THUMB_HEIGHT = 18;
    private static final int INFO_SCROLL_WHEEL_PIXELS = 28;

    private final Screen parent;
    private final String namespaceFilter;

    private EditBox searchBox;
    private List<AbstractSpell> allSpells = List.of();
    private List<AbstractSpell> filteredSpells = List.of();
    private AbstractSpell selectedSpell;
    private int scrollOffset;
    private Button editButton;
    private Set<ResourceLocation> modifiedSpellIds = Set.of();
    private boolean scrollBarDragging;
    private double scrollBarGrabOffset;
    private int infoScroll;
    private boolean infoScrollBarDragging;
    private double infoScrollBarGrabOffset;

    public SpellManagerScreen(Screen parent) {
        this(parent, null, Component.literal("Mage Additions - Spell Manager"));
    }

    /**
     * Creates a spell manager restricted to one mod namespace. This is used by
     * the Custom Spells tab so Mage Additions spells stay separate from the
     * general Iron's/addon spell tweaker.
     */
    public SpellManagerScreen(Screen parent, String namespaceFilter, Component title) {
        super(title);
        this.parent = parent;
        this.namespaceFilter = namespaceFilter == null || namespaceFilter.isBlank()
                ? null
                : namespaceFilter.toLowerCase(Locale.ROOT);
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
        requestModifiedStatus();
    }

    private void requestModifiedStatus() {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new SpellConfigPayloads.StatusRequest());
        }
    }

    /** Called by the client packet handler with the server-authoritative status list. */
    public void applyModifiedStatus(List<ResourceLocation> spellIds) {
        this.modifiedSpellIds = spellIds == null || spellIds.isEmpty()
                ? Set.of()
                : Set.copyOf(new HashSet<>(spellIds));
    }

    /**
     * Snapshot the live registry. This includes addon spells which register into
     * Iron's SpellRegistry, without Mage Additions needing to know their IDs.
     */
    private void loadSpellRegistry() {
        List<AbstractSpell> discovered = new ArrayList<>(SpellRegistry.REGISTRY.stream()
                .filter(spell -> this.namespaceFilter == null
                        || spell.getSpellResource().getNamespace().equalsIgnoreCase(this.namespaceFilter))
                .toList());
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
        this.infoScroll = 0;

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
        // getSchoolType() can read Iron's NeoForge server ConfigValue. On a
        // client that opens this screen before that spec has loaded, NeoForge
        // deliberately throws. The bridge is load-aware and safely supplies
        // the spell default until a live/server value is available.
        String school = IronsSpellConfigAccess.read(spell).school().toString().toLowerCase(Locale.ROOT);

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
                Component.literal(this.filteredSpells.size() + " / " + this.allSpells.size()
                        + (this.namespaceFilter == null ? " registered spells" : " custom spells")),
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
        graphics.drawString(
                this.font,
                Component.literal("* modified").withStyle(ChatFormatting.GOLD),
                Math.max(left + 58, right - 62),
                top - 13,
                0xFFFFFF
        );

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
            boolean modified = this.modifiedSpellIds.contains(spell.getSpellResource());
            int indicatorWidth = modified ? 12 : 0;
            int availableWidth = Math.max(30, right - textX - 6 - indicatorWidth);
            String name = trimToWidth(displayName(spell), availableWidth);
            String id = trimToWidth(spell.getSpellId(), availableWidth);

            int nameColor = IronsSpellConfigAccess.read(spell).enabled() ? 0xFFFFFF : 0x777777;
            graphics.drawString(this.font, name, textX, y + 4, nameColor);
            graphics.drawString(this.font, id, textX, y + 16, 0x888888);
            if (modified) {
                graphics.drawString(
                        this.font,
                        Component.literal("*").withStyle(ChatFormatting.GOLD),
                        right - 14,
                        y + 4,
                        0xFFFFFF
                );
            }
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

    private int scrollTrackLeft() {
        return listRight() - 6;
    }

    private int scrollTrackTop() {
        return listTop() + 2;
    }

    private int scrollTrackBottom() {
        return listBottom() - 2;
    }

    private int scrollThumbHeight() {
        int trackHeight = Math.max(1, scrollTrackBottom() - scrollTrackTop());
        if (this.filteredSpells.isEmpty()) {
            return trackHeight;
        }
        int height = Math.max(18, (int) Math.round(trackHeight * (visibleRows() / (double) this.filteredSpells.size())));
        return Math.min(trackHeight, height);
    }

    private int scrollThumbTop() {
        int max = maxScrollOffset();
        if (max <= 0) {
            return scrollTrackTop();
        }
        int travel = Math.max(0, scrollTrackBottom() - scrollTrackTop() - scrollThumbHeight());
        return scrollTrackTop() + (int) Math.round(travel * (this.scrollOffset / (double) max));
    }

    private void setScrollFromThumbTop(double thumbTop) {
        int max = maxScrollOffset();
        int travel = Math.max(0, scrollTrackBottom() - scrollTrackTop() - scrollThumbHeight());
        if (max <= 0 || travel <= 0) {
            this.scrollOffset = 0;
            return;
        }
        double fraction = (thumbTop - scrollTrackTop()) / travel;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        this.scrollOffset = (int) Math.round(fraction * max);
        clampScroll();
    }

    private void renderScrollBar(GuiGraphics graphics, int left, int right, int top, int bottom) {
        if (maxScrollOffset() <= 0) {
            return;
        }

        int trackLeft = scrollTrackLeft();
        int trackTop = scrollTrackTop();
        int trackBottom = scrollTrackBottom();
        int thumbTop = scrollThumbTop();
        int thumbHeight = scrollThumbHeight();

        graphics.fill(trackLeft, trackTop, right - 1, trackBottom, 0x66202020);
        graphics.fill(trackLeft, thumbTop, right - 1, thumbTop + thumbHeight,
                this.scrollBarDragging ? 0xFFE0E0E0 : 0xCCAAAAAA);
    }

    private int infoLeft() {
        return listRight() + 14;
    }

    private int infoRight() {
        return this.width - OUTER_MARGIN;
    }

    private int infoTop() {
        return listTop() - 17;
    }

    private int infoBottom() {
        return listBottom();
    }

    private int infoViewportTop() {
        return infoTop() + 1;
    }

    private int infoViewportBottom() {
        return infoBottom() - 1;
    }

    private int infoContentWidth() {
        return Math.max(40, infoRight() - (infoLeft() + 10) - 12);
    }

    private Component infoHelpText() {
        return Component.literal(
                "Use Edit Selected Spell to change Iron's native spell config plus Mage Additions spell overrides."
        ).withStyle(ChatFormatting.GRAY);
    }

    private int infoContentHeight() {
        if (this.selectedSpell == null) return 0;

        int y = 10;
        y += 34;
        y += 12;
        y += 13;
        y += 13;
        y += 20;
        y += 13;
        y += 13;
        y += 13;
        y += 13;
        y += 13;
        y += 13;
        y += 13;
        y += 24;

        int helpLines = Math.max(1, this.font.split(infoHelpText(), infoContentWidth()).size());
        y += helpLines * this.font.lineHeight;
        return y + 10;
    }

    private int maxInfoScroll() {
        int viewportHeight = Math.max(1, infoViewportBottom() - infoViewportTop());
        return Math.max(0, infoContentHeight() - viewportHeight);
    }

    private void setInfoScroll(int value) {
        this.infoScroll = Math.max(0, Math.min(value, maxInfoScroll()));
    }

    private int infoScrollTrackLeft() {
        return infoRight() - INFO_SCROLLBAR_WIDTH - 2;
    }

    private int infoScrollTrackTop() {
        return infoTop() + 2;
    }

    private int infoScrollTrackBottom() {
        return infoBottom() - 2;
    }

    private int infoScrollThumbHeight() {
        int trackHeight = Math.max(1, infoScrollTrackBottom() - infoScrollTrackTop());
        int max = maxInfoScroll();
        if (max <= 0) return trackHeight;

        int viewportHeight = Math.max(1, infoViewportBottom() - infoViewportTop());
        int contentHeight = Math.max(viewportHeight, infoContentHeight());
        int height = (int) Math.round(trackHeight * (viewportHeight / (double) contentHeight));
        return Math.max(INFO_SCROLLBAR_MIN_THUMB_HEIGHT, Math.min(trackHeight, height));
    }

    private int infoScrollThumbTop() {
        int max = maxInfoScroll();
        if (max <= 0) return infoScrollTrackTop();
        int travel = Math.max(0, infoScrollTrackBottom() - infoScrollTrackTop() - infoScrollThumbHeight());
        return infoScrollTrackTop() + (int) Math.round(travel * (this.infoScroll / (double) max));
    }

    private void setInfoScrollFromThumbTop(double thumbTop) {
        int max = maxInfoScroll();
        int travel = Math.max(0, infoScrollTrackBottom() - infoScrollTrackTop() - infoScrollThumbHeight());
        if (max <= 0 || travel <= 0) {
            this.infoScroll = 0;
            return;
        }
        double fraction = (thumbTop - infoScrollTrackTop()) / travel;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        setInfoScroll((int) Math.round(fraction * max));
    }

    private void renderInfoScrollBar(GuiGraphics graphics) {
        if (maxInfoScroll() <= 0) return;

        int trackLeft = infoScrollTrackLeft();
        int trackTop = infoScrollTrackTop();
        int trackBottom = infoScrollTrackBottom();
        int thumbTop = infoScrollThumbTop();
        int thumbHeight = infoScrollThumbHeight();

        graphics.fill(trackLeft, trackTop, infoRight() - 1, trackBottom, 0x66202020);
        graphics.fill(trackLeft, thumbTop, infoRight() - 1, thumbTop + thumbHeight,
                this.infoScrollBarDragging ? 0xFFE0E0E0 : 0xCCAAAAAA);
    }

    private void renderSelectedSpell(GuiGraphics graphics) {
        int left = infoLeft();
        int right = infoRight();
        int top = infoTop();
        int bottom = infoBottom();

        graphics.fill(left, top, right, bottom, 0x66000000);

        if (this.selectedSpell == null) {
            this.infoScroll = 0;
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Select a spell").withStyle(ChatFormatting.GRAY),
                    (left + right) / 2,
                    top + 18,
                    0xFFFFFF
            );
            return;
        }

        setInfoScroll(this.infoScroll);

        AbstractSpell spell = this.selectedSpell;
        IronsSpellConfigAccess.Settings config = IronsSpellConfigAccess.read(spell);

        int x = left + 10;
        int y = top + 10 - this.infoScroll;

        graphics.enableScissor(left + 1, infoViewportTop(), right - 1, infoViewportBottom());

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
        drawValue(graphics, x, y, "School", schoolDisplayName(config.school()));
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

        graphics.drawWordWrap(
                this.font,
                infoHelpText(),
                x,
                y,
                infoContentWidth(),
                0xFFFFFF
        );

        graphics.disableScissor();
        renderInfoScrollBar(graphics);
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

    private static String schoolDisplayName(ResourceLocation schoolId) {
        if (schoolId == null) {
            return "Unknown";
        }
        String path = schoolId.getPath().replace('_', ' ');
        StringBuilder result = new StringBuilder(path.length());
        boolean capitalize = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (capitalize && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
            if (c == ' ') {
                capitalize = true;
            }
        }
        return result.toString();
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
                && mouseX >= infoScrollTrackLeft() - 1
                && mouseX < infoRight()
                && mouseY >= infoScrollTrackTop()
                && mouseY < infoScrollTrackBottom()
                && maxInfoScroll() > 0) {

            int thumbTop = infoScrollThumbTop();
            int thumbHeight = infoScrollThumbHeight();
            if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                this.infoScrollBarGrabOffset = mouseY - thumbTop;
            } else {
                this.infoScrollBarGrabOffset = thumbHeight / 2.0;
                setInfoScrollFromThumbTop(mouseY - this.infoScrollBarGrabOffset);
            }
            this.infoScrollBarDragging = true;
            return true;
        }

        if (button == 0
                && mouseX >= scrollTrackLeft() - 1
                && mouseX < listRight()
                && mouseY >= scrollTrackTop()
                && mouseY < scrollTrackBottom()
                && maxScrollOffset() > 0) {

            int thumbTop = scrollThumbTop();
            int thumbHeight = scrollThumbHeight();
            if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                this.scrollBarGrabOffset = mouseY - thumbTop;
            } else {
                this.scrollBarGrabOffset = thumbHeight / 2.0;
                setScrollFromThumbTop(mouseY - this.scrollBarGrabOffset);
            }
            this.scrollBarDragging = true;
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
                this.infoScroll = 0;
                updateEditButton();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.infoScrollBarDragging) {
            setInfoScrollFromThumbTop(mouseY - this.infoScrollBarGrabOffset);
            return true;
        }
        if (button == 0 && this.scrollBarDragging) {
            setScrollFromThumbTop(mouseY - this.scrollBarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.infoScrollBarDragging) {
            this.infoScrollBarDragging = false;
            return true;
        }
        if (button == 0 && this.scrollBarDragging) {
            this.scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

        if (mouseX >= infoLeft()
                && mouseX < infoRight()
                && mouseY >= infoViewportTop()
                && mouseY < infoViewportBottom()
                && maxInfoScroll() > 0
                && scrollY != 0.0) {

            int delta = (int) Math.round(scrollY * INFO_SCROLL_WHEEL_PIXELS);
            if (delta == 0) delta = scrollY > 0.0 ? 1 : -1;
            setInfoScroll(this.infoScroll - delta);
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
