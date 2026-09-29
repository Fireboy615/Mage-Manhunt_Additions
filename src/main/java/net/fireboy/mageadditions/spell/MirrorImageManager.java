package net.fireboy.mageadditions.spell;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.mixin.ConnectionAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state for the Mirror Image spell.
 *
 * <p>Each image is a NeoForge fake server player so normal clients render a real
 * player model with the caster's skin and copied equipment. All images begin
 * directly on top of the caster, then mirror the caster's movement through normal
 * Minecraft collision. Each image keeps a small random yaw offset of up to 45
 * degrees left or right, so the group fans out naturally instead of staying
 * perfectly stacked. They mirror the caster's visible actions and poof on their
 * first hit.</p>
 */
public final class MirrorImageManager {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float MAX_CLONE_YAW_OFFSET = 45.0F;

    /**
     * NeoForge FakePlayers share a dummy Connection whose Netty channel is null by
     * default. Optional-network mods call hasChannel() during player ticks, which
     * dereferences that channel. Keep one harmless embedded channel attached for
     * the lifetime of the process and advertise no negotiated custom payloads.
     */
    private static final EmbeddedChannel FAKE_PLAYER_CHANNEL = new EmbeddedChannel();
    private static final Map<UUID, CloneState> CLONES = new HashMap<>();
    private static final Map<UUID, Set<UUID>> OWNER_TO_CLONES = new HashMap<>();

    private MirrorImageManager() {}

    public static void createMirrorImage(ServerLevel level, ServerPlayer owner, int spellLevel) {
        MinecraftServer server = level.getServer();
        removeExistingClones(owner.getUUID(), server, false);

        int effectiveLevel = Math.max(1, spellLevel);
        int cloneCount = effectiveLevel + 2; // I=3, II=4, III=5, then +1 per extra level.
        int durationTicks = MirrorImageSpell.getDurationTicks(effectiveLevel);
        int expiresAtTick = server.getTickCount() + durationTicks;

        Set<UUID> ownerClones = new HashSet<>();
        for (int cloneIndex = 0; cloneIndex < cloneCount; cloneIndex++) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), owner.getGameProfile().getName());
            profile.getProperties().putAll(owner.getGameProfile().getProperties());

            float yawOffset = randomYawOffset();

            MirrorClonePlayer clone = new MirrorClonePlayer(level, profile, owner.getUUID());
            initializeFakeConnection(clone);
            configureClonePhysics(clone);
            clone.moveTo(
                    owner.getX(),
                    owner.getY(),
                    owner.getZ(),
                    Mth.wrapDegrees(owner.getYRot() + yawOffset),
                    owner.getXRot()
            );
            syncVisibleState(owner, clone, yawOffset);

            sendCloneProfile(server, clone);
            level.addNewPlayer(clone);
            if (level.getEntity(clone.getUUID()) != clone) {
                removeCloneProfile(server, clone.getUUID());
                MageAdditions.LOGGER.warn(
                        "Mirror Image clone {} for {} could not be added to the level",
                        cloneIndex,
                        owner.getGameProfile().getName()
                );
                continue;
            }

            CloneState state = new CloneState(
                    owner.getUUID(),
                    clone,
                    expiresAtTick,
                    owner.position(),
                    yawOffset
            );
            CLONES.put(clone.getUUID(), state);
            ownerClones.add(clone.getUUID());
        }

        if (!ownerClones.isEmpty()) {
            OWNER_TO_CLONES.put(owner.getUUID(), ownerClones);
        }

        // All copies appear from the caster's exact position, so one shared poof
        // hides the frame where the extra player models are introduced.
        poof(level, owner, 18);
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.isCanceled()) {
            return;
        }

        CloneState state = CLONES.get(event.getEntity().getUUID());
        if (state == null) {
            return;
        }

        // Images are one-hit decoys. Cancel the incoming damage itself so no
        // vanilla death/respawn logic runs for the fake player, then poof it.
        event.setCanceled(true);
        removeClone(state.clone.getUUID(), state.clone.serverLevel().getServer(), true);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();

        List<UUID> remove = new ArrayList<>();
        for (CloneState state : List.copyOf(CLONES.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerId);
            if (owner == null
                    || !owner.isAlive()
                    || state.clone.isRemoved()
                    || owner.level() != state.clone.level()
                    || now >= state.expiresAtTick) {
                remove.add(state.clone.getUUID());
                continue;
            }

            configureClonePhysics(state.clone);

            // Copy the real player's displacement through normal collision, but
            // rotate the horizontal part by this clone's persistent yaw offset.
            // Because every image starts on the caster, this makes them fan out
            // naturally while still performing the same movement pattern.
            Vec3 ownerPosition = owner.position();
            Vec3 movement = ownerPosition.subtract(state.lastOwnerPosition);
            syncVisibleState(owner, state.clone, state.yawOffset);
            moveCloneLikeOwner(state.clone, rotateHorizontal(movement, state.yawOffset));
            state.lastOwnerPosition = ownerPosition;

            // Autonomous fake-casting remains disabled: independent aiming/casting
            // would immediately reveal which member is not the real player.
        }

        for (UUID cloneId : remove) {
            removeClone(cloneId, server, false);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CLONES.clear();
        OWNER_TO_CLONES.clear();
    }

    private static void initializeFakeConnection(MirrorClonePlayer clone) {
        Connection connection = clone.connection.getConnection();
        if (connection.channel() == null) {
            ((ConnectionAccessor) (Object) connection).mageadditions$setChannel(FAKE_PLAYER_CHANNEL);
        }
        ChannelAttributes.setConnectionType(connection, ConnectionType.OTHER);
        ChannelAttributes.setPayloadSetup(connection, NetworkPayloadSetup.empty());
    }

    private static void configureClonePhysics(MirrorClonePlayer clone) {
        // Clones should collide with terrain exactly like a normal player. Gravity
        // stays disabled because their vertical displacement is copied from the real
        // player along with horizontal movement; applying a second gravity step would
        // make their motion diverge for reasons unrelated to the player's movement.
        clone.noPhysics = false;
        clone.setNoGravity(true);
        clone.setDeltaMovement(Vec3.ZERO);
        clone.fallDistance = 0.0F;
    }

    private static void moveCloneLikeOwner(MirrorClonePlayer clone, Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-8D) {
            return;
        }
        clone.move(MoverType.SELF, movement);
        clone.setDeltaMovement(Vec3.ZERO);
        clone.fallDistance = 0.0F;
    }

    private static void syncVisibleState(ServerPlayer owner, MirrorClonePlayer clone, float yawOffset) {
        copySlotIfChanged(owner, clone, EquipmentSlot.HEAD);
        copySlotIfChanged(owner, clone, EquipmentSlot.CHEST);
        copySlotIfChanged(owner, clone, EquipmentSlot.LEGS);
        copySlotIfChanged(owner, clone, EquipmentSlot.FEET);
        copySlotIfChanged(owner, clone, EquipmentSlot.MAINHAND);
        copySlotIfChanged(owner, clone, EquipmentSlot.OFFHAND);

        clone.setPose(owner.getPose());
        clone.setShiftKeyDown(owner.isShiftKeyDown());
        clone.setSprinting(owner.isSprinting());
        clone.setSwimming(owner.isSwimming());

        if (owner.isFallFlying() != clone.isFallFlying()) {
            if (owner.isFallFlying()) {
                clone.startFallFlying();
            } else {
                clone.stopFallFlying();
            }
        }

        // Look where the real player looks, with one persistent sideways offset
        // chosen when this image is created. Pitch is mirrored exactly.
        float cloneYaw = Mth.wrapDegrees(owner.getYRot() + yawOffset);
        float cloneHeadYaw = Mth.wrapDegrees(owner.getYHeadRot() + yawOffset);
        clone.setYRot(cloneYaw);
        clone.setXRot(owner.getXRot());
        clone.setYHeadRot(cloneHeadYaw);
        clone.yRotO = Mth.wrapDegrees(owner.yRotO + yawOffset);
        clone.xRotO = owner.xRotO;
        clone.yHeadRot = Mth.wrapDegrees(owner.yHeadRot + yawOffset);
        clone.yHeadRotO = Mth.wrapDegrees(owner.yHeadRotO + yawOffset);
        clone.yBodyRot = Mth.wrapDegrees(owner.yBodyRot + yawOffset);
        clone.yBodyRotO = Mth.wrapDegrees(owner.yBodyRotO + yawOffset);

        // Mirror blocking/eating/bow-drawing/other held-use poses without
        // actually consuming, releasing, or activating the copied item.
        clone.mirrorItemUse(owner);
        clone.mirrorSwing(owner);
    }

    private static void copySlotIfChanged(ServerPlayer owner, MirrorClonePlayer clone, EquipmentSlot slot) {
        ItemStack desired = owner.getItemBySlot(slot);
        if (!ItemStack.matches(clone.getItemBySlot(slot), desired)) {
            clone.setItemSlot(slot, desired.copy());
        }
    }

    private static float randomYawOffset() {
        return (RANDOM.nextFloat() * 2.0F - 1.0F) * MAX_CLONE_YAW_OFFSET;
    }

    private static Vec3 rotateHorizontal(Vec3 movement, float yawOffsetDegrees) {
        if (Math.abs(yawOffsetDegrees) < 1.0E-4F) {
            return movement;
        }

        double radians = Math.toRadians(yawOffsetDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = movement.x * cos - movement.z * sin;
        double z = movement.x * sin + movement.z * cos;
        return new Vec3(x, movement.y, z);
    }

    private static void poof(ServerLevel level, Entity entity, int count) {
        level.sendParticles(
                ParticleTypes.POOF,
                entity.getX(), entity.getY() + 1.0D, entity.getZ(),
                count, 0.35D, 0.65D, 0.35D, 0.08D
        );
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                entity.getX(), entity.getY() + 1.0D, entity.getZ(),
                Math.max(8, count / 2), 0.25D, 0.55D, 0.25D, 0.05D
        );
        entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.75F, 1.35F);
    }

    private static void removeExistingClones(UUID ownerId, MinecraftServer server, boolean withPoof) {
        Set<UUID> cloneIds = OWNER_TO_CLONES.get(ownerId);
        if (cloneIds == null || cloneIds.isEmpty()) {
            return;
        }
        for (UUID cloneId : List.copyOf(cloneIds)) {
            removeClone(cloneId, server, withPoof);
        }
    }

    private static void removeClone(UUID cloneId, MinecraftServer server, boolean withPoof) {
        CloneState state = CLONES.remove(cloneId);
        if (state == null) {
            return;
        }

        Set<UUID> ownerClones = OWNER_TO_CLONES.get(state.ownerId);
        if (ownerClones != null) {
            ownerClones.remove(cloneId);
            if (ownerClones.isEmpty()) {
                OWNER_TO_CLONES.remove(state.ownerId);
            }
        }

        if (!state.clone.isRemoved()) {
            if (withPoof) {
                poof(state.clone.serverLevel(), state.clone, 28);
            }
            state.clone.serverLevel().removePlayerImmediately(state.clone, Entity.RemovalReason.DISCARDED);
        }
        removeCloneProfile(server, cloneId);

    }

    private static void sendCloneProfile(MinecraftServer server, ServerPlayer clone) {
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
                ),
                List.of(clone)
        );
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            viewer.connection.send(packet);
        }
    }

    private static void removeCloneProfile(MinecraftServer server, UUID cloneId) {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(cloneId));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            viewer.connection.send(packet);
        }
    }

    private static final class CloneState {
        final UUID ownerId;
        final MirrorClonePlayer clone;
        final int expiresAtTick;
        final float yawOffset;
        Vec3 lastOwnerPosition;

        CloneState(
                UUID ownerId,
                MirrorClonePlayer clone,
                int expiresAtTick,
                Vec3 lastOwnerPosition,
                float yawOffset
        ) {
            this.ownerId = ownerId;
            this.clone = clone;
            this.expiresAtTick = expiresAtTick;
            this.lastOwnerPosition = lastOwnerPosition;
            this.yawOffset = yawOffset;
        }
    }

    /** FakePlayer is invulnerable by default; images need attack/projectile hit events. */
    private static final class MirrorClonePlayer extends FakePlayer {
        private final UUID ownerId;
        private boolean lastOwnerSwinging;
        private int lastOwnerSwingTime;
        private InteractionHand lastOwnerSwingArm = InteractionHand.MAIN_HAND;

        MirrorClonePlayer(ServerLevel level, GameProfile profile, UUID ownerId) {
            super(level, profile);
            this.ownerId = ownerId;
        }

        void mirrorItemUse(ServerPlayer owner) {
            if (owner.isUsingItem()) {
                InteractionHand hand = owner.getUsedItemHand();
                this.useItem = this.getItemInHand(hand);
                // Keep it effectively infinite server-side so the fake player can
                // never finish eating, release a bow, drink, or trigger item logic.
                this.useItemRemaining = Integer.MAX_VALUE;
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
            } else {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
                this.setLivingEntityFlag(1, false);
                this.setLivingEntityFlag(2, false);
            }
        }

        void mirrorSwing(ServerPlayer owner) {
            boolean restarted = owner.swinging && (
                    !lastOwnerSwinging
                            || owner.swingTime < lastOwnerSwingTime
                            || owner.swingingArm != lastOwnerSwingArm
            );
            if (restarted) {
                int animation = owner.swingingArm == InteractionHand.MAIN_HAND ? 0 : 3;
                this.serverLevel().getChunkSource().broadcastAndSend(
                        this, new ClientboundAnimatePacket(this, animation)
                );
            }

            lastOwnerSwinging = owner.swinging;
            lastOwnerSwingTime = owner.swingTime;
            lastOwnerSwingArm = owner.swingingArm;

            // Keep server-side animation state aligned as well; the swing() call
            // above supplies the actual client animation packet at each restart.
            this.swinging = owner.swinging;
            this.swingingArm = owner.swingingArm;
            this.swingTime = owner.swingTime;
            this.attackAnim = owner.attackAnim;
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            // Passive environment damage is ignored so a decoy does not vanish from
            // things such as fall/fire ticks. Direct attacks/projectiles still reach
            // LivingIncomingDamageEvent and poof it immediately.
            return source.getEntity() == null && source.getDirectEntity() == null;
        }

        @Override
        public boolean isPushable() {
            // Images may begin exactly inside the real player; disable entity
            // shoving so that overlap does not reveal the caster or cause jitter.
            // Block/world collision still works normally through Entity#move.
            return false;
        }

        @Override
        public boolean canHarmPlayer(Player player) {
            return !player.getUUID().equals(ownerId);
        }
    }
}
