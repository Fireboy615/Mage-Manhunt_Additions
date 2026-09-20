package net.fireboy.mageadditions.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Keeps reconnecting clients aligned with the server's current live spell config. */
public final class SpellConfigServerEvents {
    private SpellConfigServerEvents() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellConfigSyncService.sendAllTo(player);
        }
    }
}
