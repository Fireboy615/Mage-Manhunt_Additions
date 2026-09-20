package net.fireboy.mageadditions.client.screen;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;

/** Scrollable, resource-pack editable how-to-play page with optional images. */
public final class HowToPlayScreen extends Screen {
    private static final Gson GSON = new Gson();
    private final Screen parent;
    private final MinigameDefinition game;
    private PageData page;
    private int scroll;
    private int contentHeight;
    private boolean scrollBarDragging;
    private double scrollBarGrabOffset;

    public HowToPlayScreen(Screen parent, MinigameDefinition game) {
        super(Component.translatable("screen.mageadditions.how_to_play"));
        this.parent = parent;
        this.game = game;
        this.page = loadPage(game);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> Minecraft.getInstance().setScreen(parent))
            .bounds(width / 2 - 80, height - 30, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(760, width - 40);
        int left = (width - panelWidth) / 2;
        int top = 48;
        int bottom = height - 42;
        int contentWidth = panelWidth - 48;

        graphics.drawCenteredString(font, page.title == null || page.title.isBlank() ? game.displayName() : Component.literal(page.title), width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.how_to_play.scroll_hint"), width / 2, 35, 0x888888);
        graphics.fill(left, top, left + panelWidth, bottom, 0x77000000);

        graphics.enableScissor(left + 8, top + 8, left + panelWidth - 18, bottom - 8);
        int y = top + 18 - scroll;
        int x = left + 18;

        if (page.sections == null || page.sections.isEmpty()) {
            y = drawWrapped(graphics, Component.translatable("screen.mageadditions.how_to_play.empty"), x, y, contentWidth, 0xC8C8C8) + 8;
        } else {
            for (SectionData section : page.sections) {
                if (section.heading != null && !section.heading.isBlank()) {
                    graphics.drawString(font, Component.literal(section.heading), x, y, 0x7FDBFF, false);
                    y += font.lineHeight + 7;
                }
                if (section.body != null) {
                    for (String paragraph : section.body) {
                        y = drawWrapped(graphics, Component.literal(paragraph), x, y, contentWidth, 0xD8D8D8) + 7;
                    }
                }
                if (section.image != null && !section.image.isBlank()) {
                    y = drawImage(graphics, section, x, y, contentWidth);
                    if (section.caption != null && !section.caption.isBlank()) {
                        y = drawWrapped(graphics, Component.literal(section.caption), x, y + 4, contentWidth, 0x999999) + 4;
                    }
                }
                y += 10;
            }
        }
        contentHeight = Math.max(0, y + scroll - (top + 18));
        graphics.disableScissor();

        int viewport = Math.max(1, bottom - top - 36);
        int maxScroll = Math.max(0, contentHeight - viewport);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }

        renderScrollBar(graphics, left, top, left + panelWidth, bottom);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int viewportHeight() {
        return Math.max(1, this.height - 42 - 48 - 36);
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - viewportHeight());
    }

    private int scrollTrackLeft(int panelRight) {
        return panelRight - 13;
    }

    private int scrollTrackTop(int top) {
        return top + 9;
    }

    private int scrollTrackBottom(int bottom) {
        return bottom - 9;
    }

    private int scrollThumbHeight(int top, int bottom) {
        int trackHeight = Math.max(1, scrollTrackBottom(bottom) - scrollTrackTop(top));
        if (this.contentHeight <= 0) {
            return trackHeight;
        }
        int height = Math.max(20, (int) Math.round(trackHeight * (viewportHeight() / (double) this.contentHeight)));
        return Math.min(trackHeight, height);
    }

    private int scrollThumbTop(int top, int bottom) {
        int max = maxScroll();
        if (max <= 0) {
            return scrollTrackTop(top);
        }
        int travel = Math.max(0, scrollTrackBottom(bottom) - scrollTrackTop(top) - scrollThumbHeight(top, bottom));
        return scrollTrackTop(top) + (int) Math.round(travel * (this.scroll / (double) max));
    }

    private void setScrollFromThumbTop(double thumbTop, int top, int bottom) {
        int max = maxScroll();
        int travel = Math.max(0, scrollTrackBottom(bottom) - scrollTrackTop(top) - scrollThumbHeight(top, bottom));
        if (max <= 0 || travel <= 0) {
            this.scroll = 0;
            return;
        }
        double fraction = (thumbTop - scrollTrackTop(top)) / travel;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        this.scroll = (int) Math.round(fraction * max);
    }

    private void renderScrollBar(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (maxScroll() <= 0) {
            return;
        }

        int trackLeft = scrollTrackLeft(right);
        int trackTop = scrollTrackTop(top);
        int trackBottom = scrollTrackBottom(bottom);
        int thumbTop = scrollThumbTop(top, bottom);
        int thumbHeight = scrollThumbHeight(top, bottom);

        graphics.fill(trackLeft, trackTop, trackLeft + 6, trackBottom, 0x66202020);
        graphics.fill(trackLeft, thumbTop, trackLeft + 6, thumbTop + thumbHeight,
                this.scrollBarDragging ? 0xFFE0E0E0 : 0xCCAAAAAA);
    }

    private int drawImage(GuiGraphics graphics, SectionData section, int x, int y, int maxWidth) {
        try {
            ResourceLocation texture = ResourceLocation.parse(section.image);
            int textureWidth = positive(section.textureWidth, 256);
            int textureHeight = positive(section.textureHeight, 256);
            int drawWidth = positive(section.width, textureWidth);
            int drawHeight = positive(section.height, textureHeight);
            if (drawWidth > maxWidth) {
                double scale = maxWidth / (double) drawWidth;
                drawWidth = maxWidth;
                drawHeight = Math.max(1, (int) Math.round(drawHeight * scale));
            }
            graphics.blit(texture, x, y, drawWidth, drawHeight, 0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
            return y + drawHeight;
        } catch (RuntimeException ex) {
            MageAdditions.LOGGER.warn("Invalid How to Play image '{}' for {}", section.image, game.id(), ex);
            return y;
        }
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(40, width));
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0) {
            int panelWidth = Math.min(760, width - 40);
            int left = (width - panelWidth) / 2;
            int right = left + panelWidth;
            int top = 48;
            int bottom = height - 42;
            int trackLeft = scrollTrackLeft(right);
            int thumbTop = scrollThumbTop(top, bottom);
            int thumbHeight = scrollThumbHeight(top, bottom);

            if (mouseX >= trackLeft - 1 && mouseX < trackLeft + 7
                    && mouseY >= scrollTrackTop(top) && mouseY < scrollTrackBottom(bottom)) {
                if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                    this.scrollBarGrabOffset = mouseY - thumbTop;
                } else {
                    this.scrollBarGrabOffset = thumbHeight / 2.0;
                    setScrollFromThumbTop(mouseY - this.scrollBarGrabOffset, top, bottom);
                }
                this.scrollBarDragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.scrollBarDragging) {
            int top = 48;
            int bottom = height - 42;
            setScrollFromThumbTop(mouseY - this.scrollBarGrabOffset, top, bottom);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.scrollBarDragging) {
            this.scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.round(scrollY * 28.0)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static PageData loadPage(MinigameDefinition game) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(game.howToPlayPage()).orElse(null);
            if (resource == null) {
                return fallback(game);
            }
            try (Reader reader = resource.openAsReader()) {
                PageData page = GSON.fromJson(reader, PageData.class);
                return page == null ? fallback(game) : page;
            }
        } catch (Exception ex) {
            MageAdditions.LOGGER.warn("Could not load How to Play page {}", game.howToPlayPage(), ex);
            return fallback(game);
        }
    }

    private static PageData fallback(MinigameDefinition game) {
        PageData page = new PageData();
        page.title = game.displayName().getString() + " — How to Play";
        SectionData section = new SectionData();
        section.heading = "Instructions";
        section.body = List.of("Edit " + game.howToPlayPage() + " to add the full rules, strategy notes, and images for this mode.");
        page.sections = List.of(section);
        return page;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static final class PageData {
        String title;
        List<SectionData> sections = new ArrayList<>();
    }

    private static final class SectionData {
        String heading;
        List<String> body = new ArrayList<>();
        String image;
        String caption;
        int width;
        int height;
        int textureWidth;
        int textureHeight;
    }
}
