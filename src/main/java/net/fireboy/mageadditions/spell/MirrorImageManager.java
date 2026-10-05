package net.fireboy.mageadditions.spell;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.mixin.ConnectionAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
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
 * directly on top of the caster. Each image receives one permanent evenly-spaced
 * Y-axis transform. The caster's horizontal movement and look direction are both
 * rotated through that same transform, so every image behaves like a rotated copy of the
 * caster rather than separately patched movement/head states. Images use normal
 * block collision and independently simulated gravity, mirror visible actions, and
 * poof on their first hit.</p>
 */
public final class MirrorImageManager {

    /**
     * NeoForge FakePlayers share a dummy Connection whose Netty channel is null by
     * default. Optional-network mods call hasChannel() during player ticks, which
     * dereferences that channel. Keep one harmless embedded channel attached for
     * the lifetime of the process and advertise no negotiated custom payloads.
     */
    private static final EmbeddedChannel FAKE_PLAYER_CHANNEL = new EmbeddedChannel();
    private static final Map<UUID, CloneState> CLONES = new HashMap<>();
    private static final Map<UUID, Set<UUID>> OWNER_TO_CLONES = new HashMap<>();
    private static final Map<UUID, MovementInput> OWNER_INPUTS = new HashMap<>();

    private MirrorImageManager() {}

    /**
     * Receives the caster's actual movement keys from their client. Vanilla player
     * movement packets only contain the resulting position, so once the real player
     * is pressed against a wall the server otherwise cannot tell the difference
     * between "holding W into the wall" and "not trying to move".
     */
    public static void updateMovementInput(ServerPlayer owner, float forward, float strafe, boolean jumpHeld) {
        UUID ownerId = owner.getUUID();
        if (!OWNER_TO_CLONES.containsKey(ownerId)) {
            OWNER_INPUTS.remove(ownerId);
            return;
        }

        float clampedForward = Mth.clamp(forward, -1.0F, 1.0F);
        float clampedStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        OWNER_INPUTS.put(ownerId, new MovementInput(clampedForward, clampedStrafe, jumpHeld));
    }

    public static void createMirrorImage(ServerLevel level, ServerPlayer owner, int spellLevel) {
        MinecraftServer server = level.getServer();
        removeExistingClones(owner.getUUID(), server, false);
        OWNER_INPUTS.remove(owner.getUUID());

        int effectiveLevel = Math.max(1, spellLevel);
        int cloneCount = effectiveLevel + 2; // I=3, II=4, III=5, then +1 per extra level.
        int formationCount = cloneCount + 1; // Include the real caster as one point of the formation.
        float angleStepDegrees = 360.0F / formationCount;
        int durationTicks = MirrorImageSpell.getDurationTicks(effectiveLevel);
        int expiresAtTick = server.getTickCount() + durationTicks;

        Set<UUID> ownerClones = new HashSet<>();
        for (int cloneIndex = 0; cloneIndex < cloneCount; cloneIndex++) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), owner.getGameProfile().getName());
            profile.getProperties().putAll(owner.getGameProfile().getProperties());

            // Keep the real caster at formation angle 0, then place every
            // clone on the remaining equally spaced angles. Level I therefore
            // produces four trajectories at 0/90/180/270 degrees (caster + 3
            // clones), Level II a pentagon, Level III a hexagon, etc.
            float transformYaw = Mth.wrapDegrees(angleStepDegrees * (cloneIndex + 1));

            MirrorClonePlayer clone = new MirrorClonePlayer(level, profile, owner.getUUID());
            initializeFakeConnection(clone);
            configureClonePhysics(clone);
            float initialCloneYaw = Mth.wrapDegrees(owner.getYRot() + transformYaw);
            float initialClonePitch = owner.getXRot();
            clone.moveTo(
                    owner.getX(),
                    owner.getY(),
                    owner.getZ(),
                    initialCloneYaw,
                    initialClonePitch
            );
            clone.setOnGround(owner.onGround());

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
            syncVisibleState(owner, clone, transformYaw);
            broadcastFullEquipment(clone);

            CloneState state = new CloneState(
                    owner.getUUID(),
                    clone,
                    expiresAtTick,
                    owner.position(),
                    owner.onGround(),
                    transformYaw
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

            // Treat the image as one rotated copy of the caster. Horizontal movement
            // comes from the caster's INPUT when movement keys are held, rather than
            // only the caster's resulting position. That lets an image keep walking
            // through its own open path even while the real player is holding forward
            // into a wall. If there is no movement input, copy real displacement so
            // external movement such as knockback can still be mirrored.
            Vec3 ownerPosition = owner.position();
            Vec3 movement = ownerPosition.subtract(state.lastOwnerPosition);
            Vec3 ownerHorizontalMovement = resolveOwnerHorizontalMovement(owner, state, movement);
            Vec3 horizontalMovement = rotateHorizontal(
                    ownerHorizontalMovement,
                    state.transformYaw
            );
            moveCloneWithPhysics(owner, state, horizontalMovement, movement.y);
            syncVisibleState(owner, state.clone, state.transformYaw);
            state.lastOwnerPosition = ownerPosition;
            state.lastOwnerOnGround = owner.onGround();

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
        OWNER_INPUTS.clear();
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
        // Clones collide with terrain. Gravity itself is integrated manually in the
        // server tick because fake ServerPlayers do not receive client movement
        // packets and therefore do not run normal player travel physics.
        clone.noPhysics = false;
        clone.setNoGravity(false);
    }

    private static Vec3 resolveOwnerHorizontalMovement(
            ServerPlayer owner,
            CloneState state,
            Vec3 actualMovement
    ) {
        MovementInput input = OWNER_INPUTS.get(owner.getUUID());
        Vec3 actualHorizontal = new Vec3(actualMovement.x, 0.0D, actualMovement.z);

        if (input == null || !input.isMoving()) {
            return actualHorizontal;
        }

        double actualSpeed = actualHorizontal.horizontalDistance();
        if (actualSpeed > 1.0E-4D && !owner.horizontalCollision) {
            // Learn the real current movement speed whenever the owner has room to
            // move. This preserves sprinting, speed effects, slow terrain, etc. and
            // gives us a good speed to continue using when the owner hits a wall.
            state.lastObservedHorizontalSpeed = actualSpeed;
        }

        double speed = Math.max(state.lastObservedHorizontalSpeed, owner.getSpeed());
        if (owner.isShiftKeyDown()) {
            speed *= 0.30D;
        }
        speed = Math.max(0.02D, speed);

        // Convert the caster's local W/A/S/D intent into world movement using their
        // CURRENT yaw. Movement and facing then receive the same clone transform.
        double forward = input.forward;
        double strafe = input.strafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0D) {
            forward /= length;
            strafe /= length;
        }

        double radians = Math.toRadians(owner.getYRot());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        // Minecraft yaw 0 faces +Z. Positive strafe means right.
        double x = (-sin * forward + cos * strafe) * speed;
        double z = ( cos * forward + sin * strafe) * speed;
        return new Vec3(x, 0.0D, z);
    }

    private static void moveCloneWithPhysics(
            ServerPlayer owner,
            CloneState state,
            Vec3 horizontalMovement,
            double ownerVerticalDisplacement
    ) {
        MirrorClonePlayer clone = state.clone;
        double verticalVelocity = clone.getDeltaMovement().y;

        // A jump is a discrete grounded action, not "copy positive Y motion".
        // Detect it only when the OWNER actually transitions ground -> air with a
        // meaningful upward impulse. Then apply it only if THIS clone is physically
        // standing on something. This prevents the mid-air double-jumps introduced
        // by the previous approach while still allowing grounded mirrors to jump.
        double ownerJumpVelocity = Math.max(owner.getDeltaMovement().y, ownerVerticalDisplacement);
        boolean ownerStartedJump = state.lastOwnerOnGround
                && !owner.onGround()
                && ownerJumpVelocity > 0.05D;
        boolean cloneCanJump = clone.onGround()
                || (clone.verticalCollision && verticalVelocity <= 0.0D);
        double cloneWaterSurface = waterSurfaceY(clone);
        boolean cloneInWater = !Double.isNaN(cloneWaterSurface)
                && clone.getY() < cloneWaterSurface - 0.01D;
        MovementInput movementInput = OWNER_INPUTS.get(owner.getUUID());
        boolean jumpHeld = movementInput != null && movementInput.jumpHeld();

        if (ownerStartedJump && cloneCanJump) {
            verticalVelocity = ownerJumpVelocity;
            clone.hasImpulse = true;
        } else if (owner.isFallFlying()) {
            verticalVelocity = owner.getDeltaMovement().y;
            clone.hasImpulse = true;
        } else if (cloneInWater) {
            // Water Y movement is completely clone-local. Use a lightly damped
            // spring toward a normal upright player's surface depth, with a tiny
            // per-clone oscillating target so the image naturally bobs even when
            // the caster is perfectly still and Space is not held.
            state.waterBobPhase += 0.11D;
            double bobOffset = Math.sin(state.waterBobPhase) * 0.045D;
            double targetY = cloneWaterSurface - 1.35D + bobOffset;
            double spring = (targetY - clone.getY()) * 0.065D;

            // Vanilla water travel retains about 80% vertical velocity each tick.
            verticalVelocity = verticalVelocity * 0.80D + spring;

            if (jumpHeld) {
                // Same liquid-jump impulse vanilla applies while the jump key is held.
                verticalVelocity += 0.04D;
            }

            verticalVelocity = Mth.clamp(verticalVelocity, -0.085D, 0.12D);
            clone.hasImpulse = true;
        } else {
            // Fake ServerPlayers never receive normal client movement packets, so
            // apply gravity EVERY tick, including while grounded. The tiny downward
            // move is what lets Entity#move continuously confirm floor collision and
            // keep onGround stable; skipping gravity on grounded ticks made the fake
            // player alternate between grounded/airborne and caused missed jumps.
            verticalVelocity -= clone.getGravity();
        }

        Vec3 requestedMovement = new Vec3(
                horizontalMovement.x,
                verticalVelocity,
                horizontalMovement.z
        );

        // Setting delta movement before move() lets vanilla collision handling zero
        // blocked axes correctly. The actual position update therefore respects
        // floors, ceilings, walls, slabs, stairs, etc.
        clone.setDeltaMovement(requestedMovement);
        clone.move(MoverType.SELF, requestedMovement);

        double nextVerticalVelocity;
        if (clone.verticalCollision) {
            nextVerticalVelocity = 0.0D;
        } else if (cloneInWater || owner.isFallFlying()) {
            // Water drag/spring were already integrated above; preserve this local
            // velocity so the next tick continues the same independent bob.
            nextVerticalVelocity = verticalVelocity;
        } else {
            // Vanilla-like air drag after movement. Horizontal velocity is not
            // retained because horizontal displacement is copied fresh each tick.
            nextVerticalVelocity = verticalVelocity * 0.98D;
        }

        clone.setDeltaMovement(0.0D, nextVerticalVelocity, 0.0D);
    }

    /**
     * FakePlayer does not reliably maintain vanilla fluid-contact flags because it
     * never receives normal client movement packets. Find the actual top surface of
     * the nearby water column instead and drive clone-local buoyancy from that.
     */
    private static double waterSurfaceY(Entity entity) {
        int startY = Mth.floor(entity.getY()) - 1;
        int endY = startY + 8;
        double highestSurface = Double.NaN;

        for (int y = startY; y <= endY; y++) {
            BlockPos pos = BlockPos.containing(entity.getX(), y, entity.getZ());
            var fluidState = entity.level().getFluidState(pos);
            if (!fluidState.is(FluidTags.WATER)) {
                if (!Double.isNaN(highestSurface) && y > Mth.floor(highestSurface)) {
                    break;
                }
                continue;
            }

            double surface = pos.getY() + fluidState.getHeight(entity.level(), pos);
            if (Double.isNaN(highestSurface) || surface > highestSurface) {
                highestSurface = surface;
            }
        }

        return highestSurface;
    }

    private static void syncVisibleState(
            ServerPlayer owner,
            MirrorClonePlayer clone,
            float transformYaw
    ) {
        List<Pair<EquipmentSlot, ItemStack>> changedEquipment = new ArrayList<>();
        copySlotIfChanged(owner, clone, EquipmentSlot.HEAD, changedEquipment);
        copySlotIfChanged(owner, clone, EquipmentSlot.CHEST, changedEquipment);
        copySlotIfChanged(owner, clone, EquipmentSlot.LEGS, changedEquipment);
        copySlotIfChanged(owner, clone, EquipmentSlot.FEET, changedEquipment);
        copySlotIfChanged(owner, clone, EquipmentSlot.MAINHAND, changedEquipment);
        copySlotIfChanged(owner, clone, EquipmentSlot.OFFHAND, changedEquipment);
        if (!changedEquipment.isEmpty() && !clone.isRemoved()) {
            clone.serverLevel().getChunkSource().broadcastAndSend(
                    clone, new ClientboundSetEquipmentPacket(clone.getId(), changedEquipment)
            );
        }

        clone.mirrorModelParts(owner);
        clone.setMainArm(owner.getMainArm());
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

        // One permanent Y transform is applied to every owner-facing component.
        // Because the offset never changes, an owner turn of +30 degrees is exactly
        // a +30-degree turn for every clone, from that clone's own world heading.
        float cloneYaw = Mth.wrapDegrees(owner.getYRot() + transformYaw);
        float cloneHeadYaw = Mth.wrapDegrees(owner.getYHeadRot() + transformYaw);
        float cloneBodyYaw = Mth.wrapDegrees(owner.yBodyRot + transformYaw);
        float clonePitch = owner.getXRot();

        clone.setYRot(cloneYaw);
        clone.setXRot(clonePitch);
        clone.setYHeadRot(cloneHeadYaw);
        clone.yRotO = cloneYaw;
        clone.xRotO = clonePitch;
        clone.yHeadRot = cloneHeadYaw;
        clone.yHeadRotO = cloneHeadYaw;
        clone.yBodyRot = cloneBodyYaw;
        clone.yBodyRotO = cloneBodyYaw;

        // Fake ServerPlayers do not have a real client sending movement/rotation
        // packets. Send an absolute entity state after every physics step so the
        // observing clients cannot normalize all fake-player heads back toward the
        // caster's world orientation. Head yaw is a separate packet for players.
        broadcastCloneTransform(clone, cloneHeadYaw);

        // Mirror blocking/eating/bow-drawing/other held-use poses without
        // actually consuming, releasing, or activating the copied item.
        clone.mirrorItemUse(owner);
        clone.mirrorSwing(owner);
    }

    private static void broadcastCloneTransform(MirrorClonePlayer clone, float headYaw) {
        clone.serverLevel().getChunkSource().broadcastAndSend(
                clone, new ClientboundTeleportEntityPacket(clone)
        );
        clone.serverLevel().getChunkSource().broadcastAndSend(
                clone, new ClientboundRotateHeadPacket(clone, rotationByte(headYaw))
        );
    }

    private static byte rotationByte(float degrees) {
        return (byte) Mth.floor(degrees * 256.0F / 360.0F);
    }

    private static void copySlotIfChanged(
            ServerPlayer owner,
            MirrorClonePlayer clone,
            EquipmentSlot slot,
            List<Pair<EquipmentSlot, ItemStack>> changedEquipment
    ) {
        ItemStack desired = owner.getItemBySlot(slot);
        if (!ItemStack.matches(clone.getItemBySlot(slot), desired)) {
            ItemStack copy = desired.copy();
            clone.setItemSlot(slot, copy);
            changedEquipment.add(Pair.of(slot, copy.copy()));
        }
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

    private static void broadcastFullEquipment(MirrorClonePlayer clone) {
        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        )) {
            equipment.add(Pair.of(slot, clone.getItemBySlot(slot).copy()));
        }
        clone.serverLevel().getChunkSource().broadcastAndSend(
                clone, new ClientboundSetEquipmentPacket(clone.getId(), equipment)
        );
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
                OWNER_INPUTS.remove(state.ownerId);
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
        final float transformYaw;
        Vec3 lastOwnerPosition;
        boolean lastOwnerOnGround;
        double lastObservedHorizontalSpeed;
        double waterBobPhase;

        CloneState(
                UUID ownerId,
                MirrorClonePlayer clone,
                int expiresAtTick,
                Vec3 lastOwnerPosition,
                boolean lastOwnerOnGround,
                float transformYaw
        ) {
            this.ownerId = ownerId;
            this.clone = clone;
            this.expiresAtTick = expiresAtTick;
            this.lastOwnerPosition = lastOwnerPosition;
            this.lastOwnerOnGround = lastOwnerOnGround;
            this.transformYaw = transformYaw;
            this.lastObservedHorizontalSpeed = Math.max(0.02D, clone.getSpeed());
            // Deterministic but different phase per clone so they do not all move
            // up and down in a visibly artificial lockstep.
            this.waterBobPhase = Math.toRadians(transformYaw);
        }
    }

    private record MovementInput(float forward, float strafe, boolean jumpHeld) {
        boolean isMoving() {
            return Math.abs(forward) > 1.0E-4F || Math.abs(strafe) > 1.0E-4F;
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

        void mirrorModelParts(ServerPlayer owner) {
            byte mask = 0;
            for (PlayerModelPart part : PlayerModelPart.values()) {
                if (owner.isModelPartShown(part)) {
                    mask = (byte) (mask | part.getMask());
                }
            }
            if (this.entityData.get(DATA_PLAYER_MODE_CUSTOMISATION) != mask) {
                this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, mask);
            }
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
        public void push(Entity other) {
            if (isOwnerOrSiblingMirror(other)) {
                return;
            }
            super.push(other);
        }

        @Override
        protected void doPush(Entity other) {
            if (isOwnerOrSiblingMirror(other)) {
                return;
            }
            super.doPush(other);
        }

        private boolean isOwnerOrSiblingMirror(Entity other) {
            if (other.getUUID().equals(ownerId)) {
                return true;
            }
            return other instanceof MirrorClonePlayer mirror && mirror.ownerId.equals(this.ownerId);
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
