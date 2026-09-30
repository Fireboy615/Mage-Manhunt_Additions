package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.mixin.MobEffectInstanceAccessor;
import net.fireboy.mageadditions.network.payload.ProjectileBouncePayload;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime support for generic capability-driven spell behaviour overrides. */
public final class GenericSpellOverrideServerEvents {
    private static final Map<UUID, String> SOURCE_SPELLS = new ConcurrentHashMap<>();
    private static final Map<UUID, RecentCast> RECENT_CASTS = new ConcurrentHashMap<>();
    private static final List<PendingEffectDuration> PENDING_EFFECT_DURATIONS = new ArrayList<>();
    private static final Map<UUID, PendingKnockback> PENDING_KNOCKBACK = new ConcurrentHashMap<>();
    private static final double MIN_KNOCKBACK_VECTOR_SQR = 1.0E-5D;
    private static final double FALLBACK_KNOCKBACK_STRENGTH = 0.4D;
    private static final Map<UUID, Integer> SERVER_REMAINING_BOUNCES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CLIENT_REMAINING_BOUNCES = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> CLIENT_SYNCED_BOUNCES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SERVER_LAST_BOUNCE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CLIENT_LAST_BOUNCE_TICK = new ConcurrentHashMap<>();
    private static final Set<UUID> LINGER_DURATION_APPLIED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, NativeCloudSource> NATIVE_CLOUD_SOURCES = new ConcurrentHashMap<>();
    private static final List<PendingCloudImpact> PENDING_CLOUDS = new ArrayList<>();
    private static final Map<Class<?>, Field> CURSOR_FIELDS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> NO_CURSOR_FIELD = ConcurrentHashMap.newKeySet();

    private GenericSpellOverrideServerEvents() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();

        // Native lingering areas are spawned as separate entities. Match them
        // back to the short-lived impact/AoE entity that created them so OFF can
        // genuinely suppress the cloud and linger-duration overrides can still
        // identify the originating spell.
        if (!event.loadedFromDisk() && shouldSuppressNativeCloud(entity)) {
            event.setCanceled(true);
            return;
        }

        AbstractSpell spell = matchPendingLingeringEntity(entity);
        AbstractSpell nativeCloudSpell = !event.loadedFromDisk() ? matchNativeCloudSource(entity) : null;
        if (spell == null) spell = nativeCloudSpell;
        if (spell == null) spell = spellFromOwner(entity);
        if (spell == null && entity instanceof Projectile projectile) {
            // Some Iron projectiles outlive the cast/owner lookup window. Resolve
            // them structurally at spawn time as well, so clients receive bounce
            // state before the projectile's first possible local block impact.
            spell = SpellCapabilities.findSpellForProjectileClass(projectile.getClass());
        }
        if (spell == null) return;

        SOURCE_SPELLS.put(entity.getUUID(), spell.getSpellId());
        SpellCapabilities.Capabilities capabilities = SpellCapabilities.detect(spell);
        if (capabilities.areaOfEffect()) {
            applyRadiusOverride(entity, spell);
        }
        if (capabilities.cloudOnImpact() && SpellCapabilities.findImpactAreaFactory(entity.getClass()) != null) {
            NATIVE_CLOUD_SOURCES.put(entity.getUUID(), new NativeCloudSource(
                    entity, spell.getSpellId(), ownerUuid(entity)
            ));
        }
        if (entity instanceof Projectile projectile && capabilities.bounces()) {
            int count = CastTimeOverrides.behavior(spell).bounceCount();
            if (count > 0) {
                SERVER_REMAINING_BOUNCES.put(entity.getUUID(), count);
                // Tell clients up-front that this concrete projectile is bounce-enabled.
                // Client-side spell/config inference is intentionally not authoritative:
                // without this sync a client can run Iron's normal impact/discard path
                // even though the server kept the projectile alive.
                syncBounceState(projectile, count, false);
            }
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity().level().isClientSide()) {
            CLIENT_REMAINING_BOUNCES.remove(id);
            CLIENT_SYNCED_BOUNCES.remove(event.getEntity().getId());
            CLIENT_LAST_BOUNCE_TICK.remove(id);
            return;
        }
        SOURCE_SPELLS.remove(id);
        PENDING_KNOCKBACK.remove(id);
        SERVER_REMAINING_BOUNCES.remove(id);
        SERVER_LAST_BOUNCE_TICK.remove(id);
        LINGER_DURATION_APPLIED.remove(id);
        NATIVE_CLOUD_SOURCES.remove(id);
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        AbstractSpell spell = spellFromTracked(entity);
        if (spell == null) return;

        SpellCapabilities.Capabilities capabilities = SpellCapabilities.detect(spell);
        if (entity instanceof Projectile && capabilities.hitboxSize()) {
            applyHitboxOverride(entity, spell);
        }
        if (capabilities.areaOfEffect()) {
            applyRadiusOverride(entity, spell);
        }
        if (capabilities.lingerDuration()
                && LINGER_DURATION_APPLIED.add(entity.getUUID())) {
            applyLingerDurationOverride(entity, spell);
        }
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()) return;
        AbstractSpell spell = spellFromTracked(projectile);
        if (spell == null) spell = SpellCapabilities.findSpellForProjectileClass(projectile.getClass());
        if (spell == null) spell = spellFromOwner(projectile);
        if (spell == null) return;

        if (event.getRayTraceResult() instanceof BlockHitResult blockHit && tryBounce(projectile, spell, blockHit)) {
            event.setCanceled(true);
            return;
        }

        CastTimeOverrides.BehaviorSettings behavior = CastTimeOverrides.behavior(spell);
        SpellCapabilities.Capabilities capabilities = SpellCapabilities.detect(spell);
        if (event.getRayTraceResult() instanceof BlockHitResult
                && capabilities.cloudOnImpact()
                && behavior.cloudMode() != CastTimeOverrides.CloudMode.OFF
                && (behavior.cloudMode() == CastTimeOverrides.CloudMode.ON || behavior.lingerDurationOverride().enabled())) {
            queueCloudImpact(
                    projectile, spell, event.getRayTraceResult().getLocation(),
                    behavior.cloudMode() == CastTimeOverrides.CloudMode.ON
            );
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Entity direct = event.getSource().getDirectEntity();
        AbstractSpell spell = spellFromDamageSource(direct, event.getSource().getEntity());
        if (spell == null) return;

        SpellCapabilities.Capabilities capabilities = SpellCapabilities.detect(spell);
        if (capabilities.knockback()) {
            var override = CastTimeOverrides.behavior(spell).knockbackOverride();
            if (override.enabled()) {
                LivingEntity target = event.getEntity();
                Vec3 ratio = resolveKnockbackRatio(target, direct, event.getSource().getEntity());
                PENDING_KNOCKBACK.put(target.getUUID(), new PendingKnockback(
                        spell.getSpellId(),
                        target,
                        ratio.x,
                        ratio.z,
                        target.tickCount + 2
                ));
            }
        }

        // Cone-style projectiles perform their entity collision manually and do
        // not necessarily emit ProjectileImpactEvent. Damage is therefore the
        // second generic impact signal for cloud-on-impact.
        CastTimeOverrides.BehaviorSettings behavior = CastTimeOverrides.behavior(spell);
        if (direct != null
                && capabilities.cloudOnImpact()
                && behavior.cloudMode() != CastTimeOverrides.CloudMode.OFF
                && (behavior.cloudMode() == CastTimeOverrides.CloudMode.ON || behavior.lingerDurationOverride().enabled())
                && SpellCapabilities.findImpactAreaFactory(direct.getClass()) != null) {
            queueCloudImpact(
                    direct, spell, event.getEntity().position(),
                    behavior.cloudMode() == CastTimeOverrides.CloudMode.ON
            );
        }
    }

    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        UUID targetId = event.getEntity().getUUID();
        PendingKnockback pending = PENDING_KNOCKBACK.get(targetId);
        if (pending == null) return;
        if (pending.expiresAtTick() < event.getEntity().tickCount) {
            PENDING_KNOCKBACK.remove(targetId, pending);
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(pending.spellId());
        if (spell == null || spell == SpellRegistry.none()) {
            PENDING_KNOCKBACK.remove(targetId, pending);
            return;
        }

        event.setStrength((float) CastTimeOverrides.resolveKnockback(spell, event.getStrength()));

        // Preserve a spell's intentional native direction when it supplied one.
        // Iron/vanilla derive many projectile hits from horizontal projectile
        // velocity, though, and vertical or nearly stationary magic projectiles
        // can pass (0, 0). LivingEntity.knockback deliberately randomizes that
        // case, so replace only unusable vectors with the stable direction we
        // captured from the damaging spell.
        if (!isUsableKnockbackVector(event.getRatioX(), event.getRatioZ())) {
            if (isUsableKnockbackVector(pending.ratioX(), pending.ratioZ())) {
                event.setRatioX(pending.ratioX());
                event.setRatioZ(pending.ratioZ());
            } else {
                Vec3 fallback = deterministicTargetFacingRatio(event.getEntity());
                event.setRatioX(fallback.x);
                event.setRatioZ(fallback.z);
            }
        }

        PENDING_KNOCKBACK.remove(targetId, pending);
    }

    /**
     * Iron's fires this immediately before {@code AbstractSpell#onCast}. Some
     * self-buff spells then add their MobEffectInstance without an effect source,
     * which means MobEffectEvent.Added cannot otherwise identify the originating
     * spell. Keep an exact-tick cast context so duration overrides remain
     * consistent for every spell level without catching unrelated potion effects.
     */
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null || spell == SpellRegistry.none()) return;

        // Only keep cast context for spells that actually have an enabled
        // duration override. This makes the fallback precise while avoiding any
        // reliance on structural capability detection for direct self buffs.
        if (!CastTimeOverrides.behavior(spell).effectDurationOverride().enabled()) return;
        RECENT_CASTS.put(player.getUUID(), new RecentCast(spell, server.getTickCount()));
    }

    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;

        AbstractSpell spell = spellFromSource(event.getEffectSource());
        if (spell == null && event.getEntity() instanceof ServerPlayer player) {
            // Iron's direct self-buffs (Charge is the important example) call
            // addEffect(instance) with a null source. SpellOnCastEvent fires
            // immediately before onCast(), so the caster itself is the reliable
            // fallback attribution for those effects.
            spell = recentCastSpell(player);
        }
        if (spell == null) return;

        // The override being enabled is the authority here. Capability detection
        // is only a UI hint and can miss addon/direct-effect implementations; it
        // must not prevent a saved override from actually running.
        if (!CastTimeOverrides.behavior(spell).effectDurationOverride().enabled()) return;

        MobEffectInstance incoming = event.getEffectInstance();
        int original = incoming.getDuration();
        int resolved = CastTimeOverrides.resolveEffectDurationTicks(spell, original);
        if (resolved == original) return;

        // NeoForge fires MobEffectEvent.Added before LivingEntity inserts/merges
        // the incoming instance, so this fixes brand-new effects immediately.
        ((MobEffectInstanceAccessor) (Object) incoming).mageadditions$setDuration(resolved);

        // If the entity already has the same effect, LivingEntity may merge the
        // incoming instance into the existing one after this event returns. Queue
        // a server-tick-post correction so the final active instance is forced to
        // the configured duration as well.
        PENDING_EFFECT_DURATIONS.add(new PendingEffectDuration(
                event.getEntity(), incoming.getEffect(), spell.getSpellId(), resolved
        ));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int staleBefore = server.getTickCount() - 1;
        RECENT_CASTS.entrySet().removeIf(entry -> entry.getValue().serverTick() < staleBefore);
        processPendingEffectDurations();
        processPendingKnockbackFallbacks();
        updateFollowCursor(server);
        processPendingClouds(server);
    }

    /**
     * Fallback hook for Iron's AbstractMagicProjectile block impacts. On the
     * 3.14.x line the normal impact event is enough server-side, but the client
     * previously skipped our listener and would still let projectiles such as
     * Magic Missile discard themselves locally. Newer Iron builds also route
     * some block impacts internally. The mixin calls this before onHitBlock() so
     * both sides keep the reflected projectile alive.
     *
     * <p>This runs on both logical sides. The server uses the exact tracked
     * originating spell; the client can infer the spell from the projectile
     * class so the projectile stays visible while the authoritative server
     * performs the same reflection.</p>
     */
    public static boolean tryHandleIronBlockBounce(Projectile projectile, BlockHitResult hit) {
        // Once the server has identified a bounce-enabled projectile it sends an
        // entity-id keyed state packet to every client. Prefer that state on the
        // client instead of independently reading Mage Additions config or trying
        // to infer the spell from the projectile class. This keeps visual impact
        // handling in lockstep with the authoritative server.
        if (projectile.level().isClientSide()) {
            Integer syncedRemaining = CLIENT_SYNCED_BOUNCES.get(projectile.getId());
            if (syncedRemaining != null) {
                UUID id = projectile.getUUID();
                int tick = projectile.tickCount;
                Integer lastTick = CLIENT_LAST_BOUNCE_TICK.get(id);
                if (lastTick != null && lastTick == tick) return true;
                if (syncedRemaining <= 0) return false;
                if (!applyBounceMotion(projectile, hit)) return false;
                CLIENT_LAST_BOUNCE_TICK.put(id, tick);
                CLIENT_SYNCED_BOUNCES.put(projectile.getId(), syncedRemaining - 1);
                return true;
            }
        }

        AbstractSpell spell = spellFromTracked(projectile);
        if (spell == null) {
            spell = SpellCapabilities.findSpellForProjectileClass(projectile.getClass());
        }
        if (spell == null && !projectile.level().isClientSide()) {
            spell = spellFromOwner(projectile);
        }
        return spell != null && tryBounce(projectile, spell, hit);
    }

    /** Called by the client payload handler, including when the entity spawn packet
     * has not arrived yet. The id-keyed state can therefore pre-arm the mixin before
     * the projectile's first local block collision. */
    public static void acceptClientBounceState(int entityId, int remainingBounces) {
        if (remainingBounces < 0) {
            CLIENT_SYNCED_BOUNCES.remove(entityId);
        } else {
            CLIENT_SYNCED_BOUNCES.put(entityId, remainingBounces);
        }
    }

    private static void syncBounceState(Projectile projectile, int remainingBounces, boolean bounced) {
        Vec3 velocity = projectile.getDeltaMovement();
        PacketDistributor.sendToAllPlayers(new ProjectileBouncePayload(
                projectile.getId(),
                Math.max(0, remainingBounces),
                bounced,
                projectile.getX(), projectile.getY(), projectile.getZ(),
                velocity.x, velocity.y, velocity.z
        ));
    }

    private static boolean tryBounce(Projectile projectile, AbstractSpell spell, BlockHitResult hit) {
        if (!SpellCapabilities.detect(spell).bounces()) return false;
        boolean clientSide = projectile.level().isClientSide();
        Map<UUID, Integer> remainingBounces = clientSide
                ? CLIENT_REMAINING_BOUNCES
                : SERVER_REMAINING_BOUNCES;
        Map<UUID, Integer> lastBounceTick = clientSide
                ? CLIENT_LAST_BOUNCE_TICK
                : SERVER_LAST_BOUNCE_TICK;
        UUID id = projectile.getUUID();
        int tick = projectile.tickCount;

        // A single physical block impact can be observed by both NeoForge's
        // ProjectileImpactEvent and a mixin hook. Treat a second observation in
        // the same entity tick as the already-consumed bounce: keep cancelling
        // the impact, but do not reflect again or spend another bounce charge.
        Integer lastTick = lastBounceTick.get(id);
        if (lastTick != null && lastTick == tick) return true;

        int remaining = remainingBounces.getOrDefault(
                id,
                CastTimeOverrides.behavior(spell).bounceCount()
        );
        if (remaining <= 0) return false;
        if (!applyBounceMotion(projectile, hit)) return false;

        int next = remaining - 1;
        lastBounceTick.put(id, tick);
        remainingBounces.put(id, next);
        if (!clientSide) {
            syncBounceState(projectile, next, true);
        }
        return true;
    }

    private static boolean applyBounceMotion(Projectile projectile, BlockHitResult hit) {
        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-8) return false;

        Direction direction = hit.getDirection();
        Vec3 reflected = switch (direction.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };

        Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        projectile.setPos(hit.getLocation().add(normal.scale(0.06)));
        projectile.setDeltaMovement(reflected);
        projectile.hasImpulse = true;
        return true;
    }

    private static void queueCloudImpact(Entity source, AbstractSpell spell, Vec3 position, boolean forceCloud) {
        Method factory = SpellCapabilities.findImpactAreaFactory(source.getClass());
        if (factory == null || !(source.level() instanceof ServerLevel level)) return;
        UUID ownerId = ownerUuid(source);
        int dueTick = level.getServer().getTickCount() + 1;

        // Prevent the same damage/impact path from creating duplicate pending
        // requests at effectively the same point in the same tick.
        for (PendingCloudImpact pending : PENDING_CLOUDS) {
            if (!pending.satisfied
                    && pending.source == source
                    && pending.dueTick == dueTick
                    && pending.position.distanceToSqr(position) < 0.04) {
                return;
            }
        }
        PENDING_CLOUDS.add(new PendingCloudImpact(source, spell.getSpellId(), ownerId, position, dueTick, factory, forceCloud));
    }

    private static boolean shouldSuppressNativeCloud(Entity entity) {
        if (!SpellCapabilities.hasDurationAccessors(entity.getClass())) return false;
        NativeCloudSource source = findNativeCloudSource(entity, CastTimeOverrides.CloudMode.OFF);
        if (source == null) return false;
        NATIVE_CLOUD_SOURCES.remove(source.source().getUUID(), source);
        return true;
    }

    private static AbstractSpell matchNativeCloudSource(Entity entity) {
        if (!SpellCapabilities.hasDurationAccessors(entity.getClass())) return null;
        NativeCloudSource source = findNativeCloudSource(entity, null);
        if (source == null) return null;
        AbstractSpell spell = SpellRegistry.getSpell(source.spellId());
        if (spell == null || spell == SpellRegistry.none()) return null;
        if (CastTimeOverrides.behavior(spell).cloudMode() == CastTimeOverrides.CloudMode.OFF) return null;
        NATIVE_CLOUD_SOURCES.remove(source.source().getUUID(), source);
        return spell;
    }

    private static NativeCloudSource findNativeCloudSource(Entity entity, CastTimeOverrides.CloudMode requiredMode) {
        UUID owner = ownerUuid(entity);
        NativeCloudSource best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NativeCloudSource source : NATIVE_CLOUD_SOURCES.values()) {
            Entity parent = source.source();
            if (parent == entity || parent.isRemoved() || parent.level() != entity.level()) continue;
            if (source.ownerId() != null && owner != null && !source.ownerId().equals(owner)) continue;
            if (source.ownerId() != null && owner == null) continue;

            AbstractSpell spell = SpellRegistry.getSpell(source.spellId());
            if (spell == null || spell == SpellRegistry.none()) continue;
            CastTimeOverrides.CloudMode mode = CastTimeOverrides.behavior(spell).cloudMode();
            if (requiredMode != null && mode != requiredMode) continue;

            double distance = parent.position().distanceToSqr(entity.position());
            // Native impact clouds are created at, or immediately beside, their
            // parent effect/projectile. Keep this tight to avoid attributing an
            // unrelated summoned entity from the same caster.
            if (distance <= 16.0 && distance < bestDistance) {
                best = source;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static AbstractSpell matchPendingLingeringEntity(Entity entity) {
        if (!SpellCapabilities.hasDurationAccessors(entity.getClass())) return null;
        UUID owner = ownerUuid(entity);
        PendingCloudImpact best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PendingCloudImpact pending : PENDING_CLOUDS) {
            if (pending.satisfied || pending.source.level() != entity.level()) continue;
            if (pending.ownerId != null && owner != null && !pending.ownerId.equals(owner)) continue;
            if (pending.ownerId != null && owner == null) continue;
            double distance = pending.position.distanceToSqr(entity.position());
            if (distance <= 64.0 && distance < bestDistance) {
                best = pending;
                bestDistance = distance;
            }
        }
        if (best == null) return null;
        best.satisfied = true;
        AbstractSpell spell = SpellRegistry.getSpell(best.spellId);
        return spell == null || spell == SpellRegistry.none() ? null : spell;
    }

    private static void processPendingClouds(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<PendingCloudImpact> iterator = PENDING_CLOUDS.iterator();
        while (iterator.hasNext()) {
            PendingCloudImpact pending = iterator.next();
            if (pending.satisfied) {
                iterator.remove();
                continue;
            }
            if (pending.dueTick > now) continue;

            AbstractSpell spell = SpellRegistry.getSpell(pending.spellId);
            if (pending.forceCloud
                    && spell != null && spell != SpellRegistry.none()
                    && CastTimeOverrides.behavior(spell).cloudMode() == CastTimeOverrides.CloudMode.ON) {
                try {
                    if (pending.factory.getParameterCount() == 0) {
                        pending.factory.invoke(pending.source);
                    } else {
                        pending.factory.invoke(pending.source, pending.position);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // A structurally-compatible addon can still reject invocation
                    // after its projectile is removed. In that case native impact
                    // behaviour remains untouched rather than inventing a fallback.
                }
            }
            iterator.remove();
        }
    }

    private static void updateFollowCursor(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MagicData magic = MagicData.getPlayerMagicData(player);
            AbstractSpell spell = SpellRegistry.getSpell(magic.getCastingSpellId());
            if (spell == null || spell == SpellRegistry.none()) continue;
            if (!SpellCapabilities.detect(spell).followCursor()) continue;
            if (!CastTimeOverrides.behavior(spell).followCursor()) continue;

            Object castData = magic.getAdditionalCastData();
            if (castData == null) continue;
            Field targetField = cursorField(castData.getClass());
            if (targetField == null) continue;

            double range = CastTimeOverrides.resolveTargetRange(spell, 40.0);
            range = Math.max(1.0, Math.min(256.0, range));
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(range));
            ServerLevel level = player.serverLevel();
            HitResult aimed = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 target = aimed.getType() == HitResult.Type.MISS ? end : aimed.getLocation();

            if (aimed.getType() == HitResult.Type.MISS) {
                Vec3 downStart = target.add(0.0, 16.0, 0.0);
                Vec3 downEnd = target.add(0.0, -64.0, 0.0);
                HitResult ground = level.clip(new ClipContext(downStart, downEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (ground.getType() != HitResult.Type.MISS) target = ground.getLocation();
            }

            try {
                targetField.set(castData, target);
            } catch (IllegalAccessException ignored) {}
        }
    }

    private static Field cursorField(Class<?> type) {
        Field cached = CURSOR_FIELDS.get(type);
        if (cached != null) return cached;
        if (NO_CURSOR_FIELD.contains(type)) return null;

        Field fallback = null;
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods) || !Vec3.class.isAssignableFrom(field.getType())) continue;
                String name = field.getName().toLowerCase(Locale.ROOT);
                try {
                    field.setAccessible(true);
                } catch (RuntimeException ignored) {}
                if (name.contains("center") || name.contains("target") || name.contains("position") || name.contains("location")) {
                    CURSOR_FIELDS.put(type, field);
                    return field;
                }
                if (fallback == null) fallback = field;
            }
            current = current.getSuperclass();
        }
        if (fallback != null) {
            CURSOR_FIELDS.put(type, fallback);
            return fallback;
        }
        NO_CURSOR_FIELD.add(type);
        return null;
    }

    private static void applyHitboxOverride(Entity entity, AbstractSpell spell) {
        AABB box = entity.getBoundingBox();
        double nativeSize = Math.max(box.getXsize(), box.getZsize());
        if (nativeSize <= 1.0E-6) return;
        double target = CastTimeOverrides.resolveHitboxSize(spell, nativeSize);
        if (Math.abs(target - nativeSize) <= 1.0E-6) return;
        double scale = target / nativeSize;
        double halfX = box.getXsize() * scale * 0.5;
        double halfZ = box.getZsize() * scale * 0.5;
        double halfY = box.getYsize() * scale * 0.5;
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        entity.setBoundingBox(new AABB(cx - halfX, cy - halfY, cz - halfZ, cx + halfX, cy + halfY, cz + halfZ));
    }

    private static void applyRadiusOverride(Entity entity, AbstractSpell spell) {
        try {
            Method getter = entity.getClass().getMethod("getRadius");
            Method setter = entity.getClass().getMethod("setRadius", float.class);
            Object value = getter.invoke(entity);
            if (!(value instanceof Number number)) return;
            double nativeRadius = number.doubleValue();
            double resolved = CastTimeOverrides.resolveAreaOfEffect(spell, nativeRadius);
            if (Math.abs(resolved - nativeRadius) > 1.0E-6) setter.invoke(entity, (float) resolved);
        } catch (ReflectiveOperationException ignored) {
            // Not an entity-backed radius. Capability detection stays conservative;
            // spell-specific mixins can extend this path later without changing config format.
        }
    }

    private static void applyLingerDurationOverride(Entity entity, AbstractSpell spell) {
        try {
            Method getter = entity.getClass().getMethod("getDuration");
            Method setter = entity.getClass().getMethod("setDuration", int.class);
            Object value = getter.invoke(entity);
            if (!(value instanceof Number number)) return;
            int nativeDuration = number.intValue();
            int resolved = CastTimeOverrides.resolveLingerDurationTicks(spell, nativeDuration);
            if (resolved != nativeDuration) setter.invoke(entity, resolved);
        } catch (ReflectiveOperationException ignored) {
            // Not an entity-backed lingering area.
        }
    }


    private static void processPendingKnockbackFallbacks() {
        for (Map.Entry<UUID, PendingKnockback> entry : PENDING_KNOCKBACK.entrySet()) {
            PendingKnockback pending = entry.getValue();
            LivingEntity target = pending.target();
            if (target == null || target.isRemoved() || !target.isAlive() || target.level().isClientSide()) {
                PENDING_KNOCKBACK.remove(entry.getKey(), pending);
                continue;
            }
            if (pending.expiresAtTick() < target.tickCount) {
                PENDING_KNOCKBACK.remove(entry.getKey(), pending);
                continue;
            }

            AbstractSpell spell = SpellRegistry.getSpell(pending.spellId());
            if (spell == null || spell == SpellRegistry.none()) {
                PENDING_KNOCKBACK.remove(entry.getKey(), pending);
                continue;
            }

            // If no native LivingKnockBackEvent was emitted for this damaging spell,
            // create one with vanilla's normal 0.4 hurt baseline. Absolute overrides
            // still resolve to their configured value; multiplier overrides now have
            // a predictable baseline instead of silently doing nothing.
            Vec3 ratio = isUsableKnockbackVector(pending.ratioX(), pending.ratioZ())
                    ? new Vec3(pending.ratioX(), 0.0, pending.ratioZ())
                    : deterministicTargetFacingRatio(target);
            target.knockback(FALLBACK_KNOCKBACK_STRENGTH, ratio.x, ratio.z);

            // onLivingKnockBack normally removes this exact record synchronously.
            // The conditional cleanup keeps the map safe if another mod cancels or
            // short-circuits the event before our listener is reached.
            PENDING_KNOCKBACK.remove(entry.getKey(), pending);
        }
    }

    private static Vec3 resolveKnockbackRatio(LivingEntity target, Entity direct, Entity owner) {
        if (direct instanceof Projectile projectile) {
            Vec3 motion = projectile.getDeltaMovement();
            Vec3 fromTravel = new Vec3(-motion.x, 0.0, -motion.z);
            if (isUsableKnockbackVector(fromTravel.x, fromTravel.z)) return fromTravel;

            Entity projectileOwner = projectile.getOwner();
            Vec3 fromOwner = ratioFromSource(target, projectileOwner);
            if (isUsableKnockbackVector(fromOwner.x, fromOwner.z)) return fromOwner;
        }

        // Non-projectile spell entities (AoE centers, rays, clouds, etc.) often
        // provide the most accurate radial source point themselves.
        Vec3 fromDirect = ratioFromSource(target, direct);
        if (isUsableKnockbackVector(fromDirect.x, fromDirect.z)) return fromDirect;

        Vec3 fromOwner = ratioFromSource(target, owner);
        if (isUsableKnockbackVector(fromOwner.x, fromOwner.z)) return fromOwner;

        if (direct != null) {
            Vec3 motion = direct.getDeltaMovement();
            Vec3 fromTravel = new Vec3(-motion.x, 0.0, -motion.z);
            if (isUsableKnockbackVector(fromTravel.x, fromTravel.z)) return fromTravel;
        }

        if (owner != null) {
            Vec3 look = owner.getLookAngle();
            Vec3 fromLook = new Vec3(-look.x, 0.0, -look.z);
            if (isUsableKnockbackVector(fromLook.x, fromLook.z)) return fromLook;
        }

        return Vec3.ZERO;
    }

    private static Vec3 ratioFromSource(LivingEntity target, Entity source) {
        if (source == null || source == target) return Vec3.ZERO;
        return new Vec3(source.getX() - target.getX(), 0.0, source.getZ() - target.getZ());
    }

    private static boolean isUsableKnockbackVector(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z) && x * x + z * z >= MIN_KNOCKBACK_VECTOR_SQR;
    }

    private static Vec3 deterministicTargetFacingRatio(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (isUsableKnockbackVector(horizontal.x, horizontal.z)) return horizontal;

        double radians = Math.toRadians(target.getYRot());
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static AbstractSpell spellFromDamageSource(Entity direct, Entity owner) {
        AbstractSpell spell = spellFromSource(direct);
        return spell != null ? spell : spellFromSource(owner);
    }

    private static AbstractSpell spellFromSource(Entity source) {
        if (source == null) return null;
        AbstractSpell tracked = spellFromTracked(source);
        if (tracked != null) return tracked;
        if (source instanceof ServerPlayer player) return currentSpell(player);
        return spellFromOwner(source);
    }

    private static AbstractSpell spellFromTracked(Entity entity) {
        String id = SOURCE_SPELLS.get(entity.getUUID());
        if (id == null) return null;
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return spell == null || spell == SpellRegistry.none() ? null : spell;
    }

    private static AbstractSpell spellFromOwner(Entity entity) {
        Entity owner = ownerEntity(entity);
        return owner instanceof ServerPlayer player ? currentSpell(player) : null;
    }

    private static UUID ownerUuid(Entity entity) {
        Entity owner = ownerEntity(entity);
        return owner == null ? null : owner.getUUID();
    }

    private static Entity ownerEntity(Entity entity) {
        if (entity instanceof Projectile projectile) return projectile.getOwner();
        try {
            Method getOwner = entity.getClass().getMethod("getOwner");
            Object value = getOwner.invoke(entity);
            return value instanceof Entity e ? e : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static AbstractSpell currentSpell(ServerPlayer player) {
        String id = MagicData.getPlayerMagicData(player).getCastingSpellId();
        AbstractSpell spell = SpellRegistry.getSpell(id);
        return spell == null || spell == SpellRegistry.none() ? null : spell;
    }

    private static AbstractSpell recentCastSpell(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        RecentCast recent = RECENT_CASTS.get(player.getUUID());
        if (recent == null || recent.serverTick() != server.getTickCount()) return null;
        return recent.spell();
    }

    private static void processPendingEffectDurations() {
        if (PENDING_EFFECT_DURATIONS.isEmpty()) return;

        Iterator<PendingEffectDuration> iterator = PENDING_EFFECT_DURATIONS.iterator();
        while (iterator.hasNext()) {
            PendingEffectDuration pending = iterator.next();
            LivingEntity target = pending.target();
            if (!target.isRemoved() && !target.level().isClientSide()) {
                MobEffectInstance active = target.getEffect(pending.effect());
                if (active != null && active.getDuration() != pending.durationTicks()) {
                    ((MobEffectInstanceAccessor) (Object) active)
                            .mageadditions$setDuration(pending.durationTicks());
                }
            }
            iterator.remove();
        }
    }

    private record RecentCast(AbstractSpell spell, int serverTick) {}

    private record PendingEffectDuration(
            LivingEntity target,
            Holder<MobEffect> effect,
            String spellId,
            int durationTicks
    ) {}

    private record PendingKnockback(
            String spellId,
            LivingEntity target,
            double ratioX,
            double ratioZ,
            int expiresAtTick
    ) {}

    private record NativeCloudSource(Entity source, String spellId, UUID ownerId) {}

    private static final class PendingCloudImpact {
        private final Entity source;
        private final String spellId;
        private final UUID ownerId;
        private final Vec3 position;
        private final int dueTick;
        private final Method factory;
        private final boolean forceCloud;
        private boolean satisfied;

        private PendingCloudImpact(Entity source, String spellId, UUID ownerId, Vec3 position, int dueTick, Method factory, boolean forceCloud) {
            this.source = source;
            this.spellId = spellId;
            this.ownerId = ownerId;
            this.position = position;
            this.dueTick = dueTick;
            this.factory = factory;
            this.forceCloud = forceCloud;
        }
    }
}
