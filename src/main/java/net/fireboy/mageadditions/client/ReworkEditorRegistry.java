package net.fireboy.mageadditions.client;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Function;

/**
 * Single registration point for all dedicated spell-rework editors.
 *
 * Future reworks should register one Entry here; MageAdditionsConfigScreen reads
 * this list automatically, so the menu itself does not need another one-off edit.
 */
public final class ReworkEditorRegistry {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("Counterspell", CounterspellEditorScreen::new),
            new Entry("Arrow Volley", ArrowVolleyEditorScreen::new),
            new Entry("Feather Flight", FeatherFlightEditorScreen::new)
    );

    private ReworkEditorRegistry() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public record Entry(String displayName, Function<Screen, Screen> screenFactory) {
        public Screen createScreen(Screen parent) {
            return screenFactory.apply(parent);
        }
    }
}
