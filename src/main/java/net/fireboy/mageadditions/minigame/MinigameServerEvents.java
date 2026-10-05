package net.fireboy.mageadditions.minigame;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
    private static boolean moduleWasEnabled = true;

    private MinigameServerEvents() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!CastTimeOverrides.minigameEnabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            MinigameManager.onPlayerLoggedIn(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!CastTimeOverrides.minigameEnabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            MinigameManager.onPlayerLoggedOut(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!CastTimeOverrides.minigameEnabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            MinigameManager.onPlayerRespawn(player);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (CastTimeOverrides.minigameEnabled() && !event.getPlayer().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (CastTimeOverrides.minigameEnabled() && !event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (CastTimeOverrides.minigameEnabled() && !event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (CastTimeOverrides.minigameEnabled() && !event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (CastTimeOverrides.minigameEnabled() && !event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (CastTimeOverrides.minigameEnabled() && !event.getEntity().level().isClientSide && MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!CastTimeOverrides.minigameEnabled()) return;
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide) {
            return;
        }
        if (MinigameManager.isPregameProtected()) {
            event.setCanceled(true);
            return;
        }
    }

    /**
     * Record scoreboard damage after Minecraft has applied armor, resistance,
     * blocking and the rest of the damage pipeline. This represents actual
     * health lost instead of the pre-mitigation incoming hit.
     */
    public static void onDamageApplied(LivingDamageEvent.Post event) {
        if (!CastTimeOverrides.minigameEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide) {
            return;
        }
        float appliedDamage = event.getNewDamage();
        MinigameManager.recordDamageTaken(victim, appliedDamage);
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer attacker) {
            MinigameManager.recordDamage(attacker, victim, appliedDamage);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!CastTimeOverrides.minigameEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide) {
            return;
        }
        MinigameManager.onPracticePlayerDeath(victim);
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer killer) {
            MinigameManager.recordKill(killer, victim);
        }
        MinigameManager.onPlayerDeath(victim);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        boolean enabled = CastTimeOverrides.minigameEnabled();
        if (!enabled) {
            if (moduleWasEnabled) {
                MinigameManager.disableSystem(event.getServer());
            }
            moduleWasEnabled = false;
            return;
        }

        moduleWasEnabled = true;
        MinigameManager.onServerTick(event.getServer());
    }

    public static void onServerStarted(ServerStartedEvent event) {
        moduleWasEnabled = CastTimeOverrides.minigameEnabled();
        if (moduleWasEnabled) {
            MinigameManager.onServerStarted(event.getServer());
        } else {
            MinigameManager.disableSystem(event.getServer());
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        if (CastTimeOverrides.minigameEnabled()) {
            MinigameManager.onServerStopping(event.getServer());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MinigameManager.reset();
        moduleWasEnabled = true;
    }
}
