package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.util.ModTags;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;

/**
 * Nature/Earth mobility spell.
 *
 * The caster teleports onto the surface of the block they are looking at. The
 * arrival point briefly roots the caster using Iron's real RootEntity, while
 * the departure point erupts in roots and catches nearby living entities.
 */
public final class EarthenStepSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "earthen_step");

    private static final double BASE_RANGE = 8.0D;
    private static final double RANGE_PER_LEVEL = 3.0D;
    private static final double DEPARTURE_ROOT_RADIUS = 3.0D;
    private static final int ARRIVAL_SELF_ROOT_TICKS = 20;
    private static final int DEPARTURE_ROOT_TICKS = 60;
    private static final int DEPARTURE_VISUAL_TICKS = 10;
    private static final double ROOT_HEALTH = 40.0D;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(15)
            .build();

    public EarthenStepSpell() {
        this.baseManaCost = 45;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ROOTED_DIRT_PLACE);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable(
                        "spell.mageadditions.earthen_step.teleport_range",
                        String.format("%.0f", getTeleportRange(spellLevel))
                ),
                Component.translatable(
                        "spell.mageadditions.earthen_step.departure_radius",
                        String.format("%.0f", DEPARTURE_ROOT_RADIUS)
                ),
                Component.translatable(
                        "spell.mageadditions.earthen_step.arrival_root",
                        ARRIVAL_SELF_ROOT_TICKS
                )
        );
    }

    @Override
    public boolean checkPreCastConditions(
            Level level,
            int spellLevel,
            LivingEntity caster,
            MagicData magicData
    ) {
        return findDestination(level, caster, spellLevel).isPresent();
    }

    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData magicData
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            super.onCast(level, spellLevel, caster, castSource, magicData);
            return;
        }

        Optional<Vec3> destination = findDestination(serverLevel, caster, spellLevel);
        if (destination.isEmpty()) {
            return;
        }

        Vec3 origin = caster.position();
        float yaw = caster.getYRot();
        float pitch = caster.getXRot();

        // Resolve all departure targets before adding the invisible visual anchor,
        // otherwise the anchor itself would be included in the three-block query.
        List<LivingEntity> departureTargets = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(origin, origin).inflate(DEPARTURE_ROOT_RADIUS),
                target -> target != caster
                        && target.isAlive()
                        && !target.getType().is(ModTags.CANT_ROOT)
        );

        // Teleport only after a safe grounded destination has been found. Momentum
        // is cleared so the five-tick arrival root does not drag the caster sideways.
        Vec3 targetPos = destination.get();
        caster.stopRiding();
        if (caster instanceof ServerPlayer player) {
            player.teleportTo(serverLevel, targetPos.x, targetPos.y, targetPos.z, yaw, pitch);
        } else {
            caster.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        }
        caster.setDeltaMovement(Vec3.ZERO);
        caster.fallDistance = 0.0F;

        // Leave an actual RootEntity model at the point the caster vanished from.
        spawnDepartureRootVisual(serverLevel, caster, origin);

        // The departure eruption roots every valid living entity in the requested
        // three-block radius for one second.
        for (LivingEntity target : departureTargets) {
            rootTarget(serverLevel, caster, target, DEPARTURE_ROOT_TICKS);
        }

        // Use the real Root entity at the arrival point so the caster gets the same
        // root texture/animation and is genuinely immobilised for exactly five ticks.
        rootTarget(serverLevel, caster, caster, ARRIVAL_SELF_ROOT_TICKS);

        // Extra root fog makes both the departure and arrival read clearly even when
        // several RootEntity models overlap.
        sendRootFog(serverLevel, origin, 18);
        sendRootFog(serverLevel, targetPos, 18);

        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    private double getTeleportRange(int spellLevel) {
        return BASE_RANGE + RANGE_PER_LEVEL * Math.max(0, spellLevel - 1);
    }

    /**
     * Earthen Step deliberately requires a block target. The destination is the top
     * collision surface of that block, which means a successful cast always arrives
     * touching terrain and can immediately trigger the departure-root eruption.
     */
    private Optional<Vec3> findDestination(Level level, LivingEntity caster, int spellLevel) {
        HitResult hit = caster.pick(getTeleportRange(spellLevel), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        VoxelShape collisionShape = blockState.getCollisionShape(level, blockPos);
        if (collisionShape.isEmpty()) {
            return Optional.empty();
        }

        // Query the collision surface at the block centre rather than taking the
        // shape's global maximum. This keeps slabs/stairs/fences grounded correctly
        // instead of potentially placing the caster above empty space.
        double localSurfaceY = collisionShape.max(Direction.Axis.Y, 0.5D, 0.5D);
        if (!Double.isFinite(localSurfaceY)) {
            return Optional.empty();
        }

        double surfaceY = blockPos.getY() + localSurfaceY;
        Vec3 destination = new Vec3(
                blockPos.getX() + 0.5D,
                surfaceY,
                blockPos.getZ() + 0.5D
        );

        AABB destinationBox = caster.getBoundingBox().move(
                destination.x - caster.getX(),
                destination.y - caster.getY(),
                destination.z - caster.getZ()
        );

        if (!level.noCollision(caster, destinationBox)) {
            return Optional.empty();
        }
        if (!level.getWorldBorder().isWithinBounds(destinationBox)) {
            return Optional.empty();
        }

        return Optional.of(destination);
    }

    private static void rootTarget(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target,
            int requestedTicks
    ) {
        if (!target.isAlive() || target.getType().is(ModTags.CANT_ROOT)) {
            return;
        }

        target.stopRiding();

        RootEntity root = new RootEntity(level, owner);
        root.setTarget(target);
        // RootEntity removes itself when tickCount > duration, so N requested ticks
        // maps to N - 1 here to make the visible/immobile window exact.
        root.setDuration(Math.max(0, requestedTicks - 1));
        root.moveTo(target.position());

        var maxHealth = root.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(ROOT_HEALTH);
            root.setHealth((float) ROOT_HEALTH);
        }

        level.addFreshEntity(root);
        target.startRiding(root, true);
    }

    /**
     * RootEntity only remains alive while something rides it. An invisible temporary
     * armour stand is therefore used as a short-lived server-side anchor so the actual
     * Root spell model can remain at the departure point even when nobody was standing
     * there. The anchor is never saved and cannot be hit or pushed.
     */
    private static void spawnDepartureRootVisual(ServerLevel level, LivingEntity owner, Vec3 origin) {
        DepartureRootAnchor anchor = new DepartureRootAnchor(level, origin, DEPARTURE_VISUAL_TICKS + 2);
        level.addFreshEntity(anchor);

        RootEntity root = new RootEntity(level, owner);
        root.setTarget(anchor);
        root.setDuration(DEPARTURE_VISUAL_TICKS - 1);
        root.moveTo(origin);

        var maxHealth = root.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(ROOT_HEALTH);
            root.setHealth((float) ROOT_HEALTH);
        }

        level.addFreshEntity(root);
        anchor.startRiding(root, true);
    }

    private static void sendRootFog(ServerLevel level, Vec3 position, int count) {
        level.sendParticles(
                ParticleHelper.ROOT_FOG,
                position.x,
                position.y + 0.1D,
                position.z,
                count,
                0.65D,
                0.15D,
                0.65D,
                0.04D
        );
    }

    private static final class DepartureRootAnchor extends ArmorStand {
        private final int lifetimeTicks;

        private DepartureRootAnchor(ServerLevel level, Vec3 position, int lifetimeTicks) {
            super(level, position.x, position.y, position.z);
            this.lifetimeTicks = lifetimeTicks;
            setInvisible(true);
            setNoGravity(true);
            setInvulnerable(true);
            setNoBasePlate(true);
        }

        @Override
        public void tick() {
            super.tick();
            setDeltaMovement(Vec3.ZERO);
            if (tickCount >= lifetimeTicks) {
                discard();
            }
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return false;
        }
    }
}
