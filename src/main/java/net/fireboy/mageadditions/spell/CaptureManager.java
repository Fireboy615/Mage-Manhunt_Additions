package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.network.payload.CaptureStatePayload;
import net.fireboy.mageadditions.registry.ModSpells;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime storage for the Capture spell. */
public final class CaptureManager {
    private static final int STRUGGLES_TO_ESCAPE = 14;
    private static final double MAX_RELEASE_DISTANCE = 8.0D;
    private static final double RELEASE_BACKOFF = 0.75D;
    private static final double PLAYER_STORAGE_Y_OFFSET = 64.0D;

    private static final Map<UUID, Capture> BY_TARGET = new HashMap<>();
    private static final Map<UUID, UUID> BY_CASTER = new HashMap<>();

    private CaptureManager() {
    }

    /**
     * Stores a target in the spell. Connected players remain real server players,
     * but are moved into a spectator holding state below the caster's world.
     * Other entities are serialized to NBT and removed from the level.
     */
    public static boolean capture(ServerPlayer caster, Entity target, int durationTicks) {
        if (!canCaptureTarget(target) || target == caster || !target.isAlive()) {
            return false;
        }
        if (BY_TARGET.containsKey(target.getUUID())) {
            caster.sendSystemMessage(Component.translatable("message.mageadditions.capture.already_captured")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID previousTarget = BY_CASTER.get(caster.getUUID());
        if (previousTarget != null) {
            release(previousTarget, null, RecastResult.USER_CANCEL);
        }

        ResourceKey<Level> originalDimension = target.level().dimension();
        Vec3 originalPosition = target.position();
        float originalYaw = target.getYRot();
        float originalPitch = target.getXRot();

        Capture capture;
        if (target instanceof ServerPlayer capturedPlayer && !(capturedPlayer instanceof FakePlayer)) {
            GameType originalGameType = capturedPlayer.gameMode.getGameModeForPlayer();
            capture = Capture.player(
                    caster.getUUID(),
                    capturedPlayer.getUUID(),
                    capturedPlayer.getDisplayName().getString(),
                    originalDimension,
                    originalPosition,
                    originalYaw,
                    originalPitch,
                    durationTicks,
                    originalGameType
            );

            BY_TARGET.put(capturedPlayer.getUUID(), capture);
            BY_CASTER.put(caster.getUUID(), capturedPlayer.getUUID());

            storePlayer(caster, capturedPlayer);
            PacketDistributor.sendToPlayer(capturedPlayer, new CaptureStatePayload(true));
            capturedPlayer.sendSystemMessage(Component.translatable("message.mageadditions.capture.captured_player"));
        } else {
            // A passenger cannot save as a root entity, so detach it first. Do not
            // capture its riders/passengers implicitly; Capture always stores the
            // specifically selected entity.
            target.stopRiding();
            target.ejectPassengers();

            CompoundTag storedEntity = new CompoundTag();
            if (!target.save(storedEntity)) {
                caster.sendSystemMessage(Component.translatable("message.mageadditions.capture.cannot_store")
                        .withStyle(ChatFormatting.RED));
                return false;
            }

            capture = Capture.entity(
                    caster.getUUID(),
                    target.getUUID(),
                    target.getDisplayName().getString(),
                    originalDimension,
                    originalPosition,
                    originalYaw,
                    originalPitch,
                    durationTicks,
                    storedEntity
            );

            BY_TARGET.put(target.getUUID(), capture);
            BY_CASTER.put(caster.getUUID(), target.getUUID());

            // This is the important semantic difference from Root/Stun: the entity
            // is no longer present in the world at all while it is captured.
            target.discard();
        }

        caster.sendSystemMessage(Component.translatable("message.mageadditions.capture.captured"));
        sendHoldingStatus(caster, capture);
        return true;
    }

    public static void releaseByCaster(ServerPlayer caster, UUID expectedTarget) {
        UUID targetId = BY_CASTER.get(caster.getUUID());
        if (targetId == null || (expectedTarget != null && !expectedTarget.equals(targetId))) {
            return;
        }
        release(targetId, Component.translatable("message.mageadditions.capture.released_by_caster"), RecastResult.USER_CANCEL);
    }

    public static void releaseTimedOut(ServerPlayer caster, UUID expectedTarget) {
        UUID targetId = BY_CASTER.get(caster.getUUID());
        if (targetId != null && targetId.equals(expectedTarget)) {
            release(targetId, null, RecastResult.TIMEOUT);
        }
    }

    /** Called from the client whenever the captured player presses the jump/space key. */
    public static void struggle(ServerPlayer player) {
        Capture capture = BY_TARGET.get(player.getUUID());
        if (capture == null || !capture.player) {
            return;
        }

        capture.struggles++;
        if (capture.struggles >= STRUGGLES_TO_ESCAPE) {
            UUID casterId = capture.caster;
            release(player.getUUID(), Component.translatable("message.mageadditions.capture.escaped"), RecastResult.USER_CANCEL);
            clearCasterRecast(casterId, RecastResult.USER_CANCEL);
        }
    }

    public static boolean canCaptureTarget(Entity entity) {
        return entity != null
                && entity.isAlive()
                && !(entity instanceof FakePlayer)
                && !BY_TARGET.containsKey(entity.getUUID());
    }

    public static boolean isCaptured(Entity entity) {
        return entity != null && BY_TARGET.containsKey(entity.getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<UUID> timedOut = new ArrayList<>();
        List<UUID> broken = new ArrayList<>();

        for (Capture capture : BY_TARGET.values()) {
            if (--capture.ticks <= 0) {
                timedOut.add(capture.target);
                continue;
            }

            ServerPlayer caster = server.getPlayerList().getPlayer(capture.caster);
            if (caster != null && server.getTickCount() % 10 == 0) {
                sendHoldingStatus(caster, capture);
            }

            if (!capture.player) {
                // Serialized entities truly do not exist in the world while stored.
                continue;
            }

            ServerPlayer capturedPlayer = server.getPlayerList().getPlayer(capture.target);
            if (capturedPlayer == null || caster == null) {
                broken.add(capture.target);
                continue;
            }

            holdPlayerInsideSpell(caster, capturedPlayer);
        }

        for (UUID targetId : broken) {
            Capture capture = BY_TARGET.get(targetId);
            UUID casterId = capture == null ? null : capture.caster;
            release(targetId, null, RecastResult.USER_CANCEL);
            if (casterId != null) {
                clearCasterRecast(casterId, RecastResult.USER_CANCEL);
            }
        }

        for (UUID targetId : timedOut) {
            Capture capture = BY_TARGET.get(targetId);
            UUID casterId = capture == null ? null : capture.caster;
            release(targetId, null, RecastResult.TIMEOUT);
            if (casterId != null) {
                clearCasterRecast(casterId, RecastResult.TIMEOUT);
            }
        }
    }

    /** Restore captures before worlds are saved, so serialized mobs can never be lost on shutdown. */
    public static void onServerStopping(ServerStoppingEvent event) {
        List<UUID> captures = new ArrayList<>(BY_TARGET.keySet());
        for (UUID targetId : captures) {
            release(targetId, null, RecastResult.USER_CANCEL);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        BY_TARGET.clear();
        BY_CASTER.clear();
    }

    /**
     * A stored player must have their original game mode restored before their
     * connection closes, otherwise spectator mode would be saved to player data.
     * Likewise, a caster leaving releases whatever they were carrying.
     */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Capture captured = BY_TARGET.get(player.getUUID());
        if (captured != null && captured.player) {
            UUID casterId = captured.caster;
            release(player.getUUID(), null, RecastResult.USER_CANCEL);
            clearCasterRecast(casterId, RecastResult.USER_CANCEL);
        }

        UUID carriedTarget = BY_CASTER.get(player.getUUID());
        if (carriedTarget != null) {
            release(carriedTarget, null, RecastResult.USER_CANCEL);
        }
    }

    private static void storePlayer(ServerPlayer caster, ServerPlayer capturedPlayer) {
        capturedPlayer.stopRiding();
        capturedPlayer.ejectPassengers();
        capturedPlayer.setDeltaMovement(Vec3.ZERO);
        capturedPlayer.fallDistance = 0.0F;
        capturedPlayer.setGameMode(GameType.SPECTATOR);
        holdPlayerInsideSpell(caster, capturedPlayer);
    }

    private static void holdPlayerInsideSpell(ServerPlayer caster, ServerPlayer capturedPlayer) {
        ServerLevel level = caster.serverLevel();
        double storageY = level.getMinBuildHeight() - PLAYER_STORAGE_Y_OFFSET;
        double x = caster.getX();
        double z = caster.getZ();

        capturedPlayer.setDeltaMovement(Vec3.ZERO);
        capturedPlayer.fallDistance = 0.0F;

        if (capturedPlayer.serverLevel() != level) {
            capturedPlayer.teleportTo(level, x, storageY, z, caster.getYRot(), 0.0F);
        } else {
            capturedPlayer.teleportTo(x, storageY, z);
        }
    }

    private static void release(UUID targetId, Component playerMessage, RecastResult reason) {
        Capture capture = BY_TARGET.remove(targetId);
        if (capture == null) {
            return;
        }
        BY_CASTER.remove(capture.caster);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer caster = server.getPlayerList().getPlayer(capture.caster);
        ReleasePoint releasePoint = releasePoint(server, capture, caster);

        if (capture.player) {
            ServerPlayer capturedPlayer = server.getPlayerList().getPlayer(capture.target);
            if (capturedPlayer != null) {
                restorePlayer(capture, capturedPlayer, releasePoint);
                PacketDistributor.sendToPlayer(capturedPlayer, new CaptureStatePayload(false));
                if (playerMessage != null) {
                    capturedPlayer.sendSystemMessage(playerMessage);
                }
            }
        } else {
            restoreStoredEntity(capture, releasePoint);
        }
    }

    private static void restorePlayer(Capture capture, ServerPlayer player, ReleasePoint point) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.teleportTo(point.level, point.position.x, point.position.y, point.position.z, point.yaw, point.pitch);
        player.setGameMode(capture.originalGameType == null ? GameType.SURVIVAL : capture.originalGameType);
    }

    private static void restoreStoredEntity(Capture capture, ReleasePoint point) {
        if (capture.entityData == null) {
            return;
        }

        Entity restored = EntityType.loadEntityRecursive(capture.entityData.copy(), point.level, entity -> entity);
        if (restored == null) {
            MageAdditions.LOGGER.error("Capture failed to restore stored entity {}", capture.target);
            return;
        }

        restored.stopRiding();
        restored.setDeltaMovement(Vec3.ZERO);
        restored.fallDistance = 0.0F;
        restored.moveTo(point.position.x, point.position.y, point.position.z, point.yaw, point.pitch);

        if (!point.level.addFreshEntity(restored)) {
            MageAdditions.LOGGER.error("Capture restored entity {} from NBT but the level rejected it", capture.target);
        }
    }

    private static ReleasePoint releasePoint(MinecraftServer server, Capture capture, ServerPlayer caster) {
        if (caster != null) {
            ServerLevel level = caster.serverLevel();
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getLookAngle().normalize();
            HitResult hit = caster.pick(MAX_RELEASE_DISTANCE, 1.0F, false);

            Vec3 pos;
            if (hit.getType() == HitResult.Type.MISS) {
                pos = eye.add(look.scale(MAX_RELEASE_DISTANCE));
            } else {
                // Put the released entity just in front of the surface being looked
                // at instead of embedding its centre inside the selected block.
                pos = hit.getLocation().subtract(look.scale(RELEASE_BACKOFF));
            }

            return new ReleasePoint(level, pos, caster.getYRot(), caster.getXRot());
        }

        ServerLevel originalLevel = server.getLevel(capture.originalDimension);
        if (originalLevel == null) {
            originalLevel = server.overworld();
        }
        return new ReleasePoint(
                originalLevel,
                capture.originalPosition,
                capture.originalYaw,
                capture.originalPitch
        );
    }

    private static void sendHoldingStatus(ServerPlayer caster, Capture capture) {
        int totalSeconds = Math.max(0, (capture.ticks + 19) / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String time = String.format("%d:%02d", minutes, seconds);

        caster.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable(
                        "message.mageadditions.capture.holding",
                        capture.targetName,
                        time
                ).withStyle(ChatFormatting.AQUA)
        ));
    }

    private static void clearCasterRecast(UUID casterId, RecastResult result) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer caster = server.getPlayerList().getPlayer(casterId);
        if (caster == null) {
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(caster);
        var recasts = magicData.getPlayerRecasts();
        var recast = recasts.getRecastInstance(ModSpells.CAPTURE.get().getSpellId());
        if (recast != null && recasts.isRecastActive(recast)) {
            recasts.removeRecast(recast, result);
        }
    }

    private static final class Capture {
        final UUID caster;
        final UUID target;
        final String targetName;
        final ResourceKey<Level> originalDimension;
        final Vec3 originalPosition;
        final float originalYaw;
        final float originalPitch;
        final boolean player;
        final CompoundTag entityData;
        final GameType originalGameType;
        int ticks;
        int struggles;

        private Capture(
                UUID caster,
                UUID target,
                String targetName,
                ResourceKey<Level> originalDimension,
                Vec3 originalPosition,
                float originalYaw,
                float originalPitch,
                int ticks,
                boolean player,
                CompoundTag entityData,
                GameType originalGameType
        ) {
            this.caster = caster;
            this.target = target;
            this.targetName = targetName;
            this.originalDimension = originalDimension;
            this.originalPosition = originalPosition;
            this.originalYaw = originalYaw;
            this.originalPitch = originalPitch;
            this.ticks = ticks;
            this.player = player;
            this.entityData = entityData;
            this.originalGameType = originalGameType;
        }

        static Capture entity(
                UUID caster,
                UUID target,
                String targetName,
                ResourceKey<Level> originalDimension,
                Vec3 originalPosition,
                float originalYaw,
                float originalPitch,
                int ticks,
                CompoundTag entityData
        ) {
            return new Capture(
                    caster, target, targetName, originalDimension, originalPosition, originalYaw, originalPitch,
                    ticks, false, entityData, null
            );
        }

        static Capture player(
                UUID caster,
                UUID target,
                String targetName,
                ResourceKey<Level> originalDimension,
                Vec3 originalPosition,
                float originalYaw,
                float originalPitch,
                int ticks,
                GameType originalGameType
        ) {
            return new Capture(
                    caster, target, targetName, originalDimension, originalPosition, originalYaw, originalPitch,
                    ticks, true, null, originalGameType
            );
        }
    }

    private record ReleasePoint(ServerLevel level, Vec3 position, float yaw, float pitch) {
    }
}
