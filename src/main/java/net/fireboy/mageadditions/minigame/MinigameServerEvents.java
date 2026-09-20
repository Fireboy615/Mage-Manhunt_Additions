package net.fireboy.mageadditions.minigame;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Pregame protection plus lifecycle hooks for the native minigame session. */
public final class MinigameServerEvents {
    private MinigameServerEvents() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinigameManager.onPlayerLoggedIn(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinigameManager.onPlayerLoggedOut(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinigameManager.onPlayerRespawn(player);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.level().isClientSide
                && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinigameManager.onServerTick(event.getServer());
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinigameManager.onServerStarted(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        MinigameManager.onServerStopping(event.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MinigameManager.reset();
    }
}
