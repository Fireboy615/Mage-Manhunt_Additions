package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.registry.ModEffects;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Client-only rendering hooks for Mirror Image. */
public final class ClientMirrorImageEvents {
    private ClientMirrorImageEvents() {}

    /**
     * Vanilla invisibility still renders armor and held items. While the hidden
     * Mirror Cloak marker is active, suppress the whole player render instead.
     */
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().hasEffect(ModEffects.MIRROR_CLOAK)) {
            event.setCanceled(true);
        }
    }
}
