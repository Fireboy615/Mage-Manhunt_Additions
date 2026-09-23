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
import net.minecraft.client.gui.components.Renderable;
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
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;

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
        // Do not use Screen#renderBackground here: in-game it applies Minecraft's menu blur.
        // A translucent overlay keeps the world visible and sharp behind the instructions.
        graphics.fill(0, 0, width, height, 0x88000000);
        int panelWidth = Math.min(760, width - 40);
        int left = (width - panelWidth) / 2;
        int top = 48;
        int bottom = height - 42;
        int contentWidth = panelWidth - 52;

        graphics.drawCenteredString(font, page.title == null || page.title.isBlank() ? game.displayName() : Component.literal(page.title), width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.mageadditions.how_to_play.scroll_hint"), width / 2, 35, 0x888888);
        graphics.fill(left, top, left + panelWidth, bottom, 0x77000000);

        int scrollbarX = left + panelWidth - 14;
        graphics.enableScissor(left + 8, top + 8, scrollbarX - 6, bottom - 8);
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

        drawScrollbar(graphics, scrollbarX, top + 8, bottom - 8, viewport, maxScroll, mouseX, mouseY);
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewport = Math.max(1, height - 42 - 48 - 36);
        int maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY * 28.0)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ScrollbarGeometry bar = scrollbarGeometry();
            if (bar != null && mouseX >= bar.x && mouseX < bar.x + bar.width
                && mouseY >= bar.trackTop && mouseY < bar.trackBottom) {
                if (mouseY >= bar.thumbTop && mouseY < bar.thumbTop + bar.thumbHeight) {
                    draggingScrollbar = true;
                    scrollbarDragOffset = (int) mouseY - bar.thumbTop;
                } else {
                    setScrollFromThumbTop((int) mouseY - bar.thumbHeight / 2, bar);
                    draggingScrollbar = true;
                    scrollbarDragOffset = bar.thumbHeight / 2;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            ScrollbarGeometry bar = scrollbarGeometry();
            if (bar != null) {
                setScrollFromThumbTop((int) mouseY - scrollbarDragOffset, bar);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int trackTop, int trackBottom, int viewport, int maxScroll, int mouseX, int mouseY) {
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(24, (int) Math.round(trackHeight * (viewport / (double) Math.max(viewport, contentHeight))));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbTop = trackTop + (int) Math.round(travel * (scroll / (double) maxScroll));
        boolean hovered = mouseX >= x && mouseX < x + 8 && mouseY >= thumbTop && mouseY < thumbTop + thumbHeight;

        graphics.fill(x, trackTop, x + 8, trackBottom, 0x66000000);
        graphics.fill(x, thumbTop, x + 8, thumbTop + thumbHeight,
            draggingScrollbar || hovered ? 0xFFD0D0D0 : 0xFF909090);
    }

    private ScrollbarGeometry scrollbarGeometry() {
        int panelWidth = Math.min(760, width - 40);
        int left = (width - panelWidth) / 2;
        int top = 48;
        int bottom = height - 42;
        int viewport = Math.max(1, bottom - top - 36);
        int maxScroll = Math.max(0, contentHeight - viewport);
        if (maxScroll <= 0) {
            return null;
        }

        int x = left + panelWidth - 14;
        int trackTop = top + 8;
        int trackBottom = bottom - 8;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(24, (int) Math.round(trackHeight * (viewport / (double) Math.max(viewport, contentHeight))));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbTop = trackTop + (int) Math.round(travel * (scroll / (double) maxScroll));
        return new ScrollbarGeometry(x, 8, trackTop, trackBottom, thumbTop, thumbHeight, maxScroll);
    }

    private void setScrollFromThumbTop(int requestedThumbTop, ScrollbarGeometry bar) {
        int travel = Math.max(1, (bar.trackBottom - bar.trackTop) - bar.thumbHeight);
        int clampedThumbTop = Math.max(bar.trackTop, Math.min(bar.trackTop + travel, requestedThumbTop));
        double fraction = (clampedThumbTop - bar.trackTop) / (double) travel;
        scroll = Math.max(0, Math.min(bar.maxScroll, (int) Math.round(fraction * bar.maxScroll)));
    }

    private record ScrollbarGeometry(
        int x, int width, int trackTop, int trackBottom, int thumbTop, int thumbHeight, int maxScroll
    ) {}

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
