package net.fireboy.mageadditions.server.domain;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.registry.ModBlocks;
import net.fireboy.mageadditions.server.temporaryblock.TemporaryBlockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DomainManager {
    private static final Map<UUID, ShellBuild> SHELL_BUILDS = new HashMap<>();
    private static final Map<UUID, LastLegalPosition> LAST_LEGAL_POSITIONS = new HashMap<>();
    private static final Set<UUID> INTERNAL_TELEPORTS = new HashSet<>();

    private DomainManager() {
    }

    public static DomainSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(DomainSavedData.FACTORY, DomainSavedData.DATA_NAME);
    }

    public static boolean canStart(ServerPlayer caster, int spellLevel, boolean sendFailure) {
        if (!CastTimeOverrides.serverAdditionsEnabled()) {
            return fail(caster, sendFailure, "Server Additions are disabled.");
        }

        ServerLevel level = caster.serverLevel();
        int radius = DomainConfig.radius(spellLevel);
        BlockPos center = caster.blockPosition();
        String dimension = dimension(level);

        // Testing mode: casting does not require a target, opponent, special
        // terrain, or any other gameplay condition. The only retained guard is
        // overlap prevention because overlapping temporary barrier records can
        // make exact world restoration ambiguous.
        for (DomainRecord domain : data(caster.getServer()).allDomains()) {
            if (domain.dimension().equals(dimension)
                    && volumesOverlap(center, radius, domain.center(), domain.radius())) {
                return fail(caster, sendFailure, "A Domain is already too close to this location.");
            }
        }

        return true;
    }

    public static boolean createDomain(ServerPlayer caster, int spellLevel) {
        if (!canStart(caster, spellLevel, true)) {
            return false;
        }

        ServerLevel level = caster.serverLevel();
        DomainShape shape = DomainConfig.shape();
        int radius = DomainConfig.radius(spellLevel);
        BlockPos center = caster.blockPosition();
        LinkedHashSet<UUID> participants = new LinkedHashSet<>();

        for (ServerPlayer player : caster.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.serverLevel() != level) {
                continue;
            }

            if (shape.contains(center, player.position(), radius)) {
                participants.add(player.getUUID());
            }
        }

        if (!participants.contains(caster.getUUID())) {
            participants.add(caster.getUUID());
        }

        int levelValue = Math.max(1, Math.min(5, spellLevel));
        long now = level.getGameTime();
        DomainRecord record = new DomainRecord(
                UUID.randomUUID(),
                dimension(level),
                caster.getUUID(),
                center.immutable(),
                shape,
                radius,
                levelValue,
                now,
                now + DomainConfig.durationTicks(levelValue),
                participants,
                participants,
                DomainRecord.Phase.ACTIVE
        );

        DomainSavedData saved = data(caster.getServer());
        saved.putDomain(record);
        SHELL_BUILDS.put(record.id(), new ShellBuild(generateShell(record), 0));

        for (ServerPlayer player : caster.getServer().getPlayerList().getPlayers()) {
            if (!record.isAliveParticipant(player.getUUID()) || player.serverLevel() != level) {
                continue;
            }

            if (DomainConfig.glowTicks() > 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING,
                        DomainConfig.glowTicks(),
                        0,
                        false,
                        false,
                        true
                ));
            }

            LAST_LEGAL_POSITIONS.put(
                    player.getUUID(),
                    new LastLegalPosition(record.dimension(), player.position())
            );

            player.sendSystemMessage(
                    Component.literal(
                            "DOMAIN SEALED • "
                                    + participants.size()
                                    + " players • "
                                    + formatTime(DomainConfig.durationTicks(levelValue))
                    ).withStyle(ChatFormatting.AQUA)
            );
        }

        MageAdditions.LOGGER.info(
                "Created Domain {} at {} in {} with {} participants, shape={}, radius={}, level={}",
                record.id(),
                record.center(),
                record.dimension(),
                record.participants().size(),
                record.shape(),
                record.radius(),
                record.spellLevel()
        );
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        DomainSavedData saved = data(server);
        List<DomainRecord> snapshot = new ArrayList<>(saved.allDomains());

        for (DomainRecord record : snapshot) {
            ServerLevel level = findLevel(server, record.dimension());
            if (level == null) {
                continue;
            }

            if (record.isActive()) {
                if (level.getGameTime() >= record.endGameTime()
                        || (record.participants().size() > 1
                        && record.aliveParticipants().size() <= 1)) {
                    beginRestoring(server, record);
                    continue;
                }

                buildBarrier(level, record, saved);
            } else {
                restoreBarrier(level, record, saved);
            }
        }

        enforcePlayerContainment(server);

        if (server.getTickCount() % 20 == 0) {
            sendActionBars(server);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }

        DomainSavedData saved = data(player.getServer());
        for (DomainRecord domain : new ArrayList<>(saved.allDomains())) {
            if (!domain.isActive()
                    || !domain.dimension().equals(dimension(player.serverLevel()))
                    || !domain.isAliveParticipant(player.getUUID())) {
                continue;
            }

            DomainRecord updated = domain.markDead(player.getUUID());
            saved.putDomain(updated);
            LAST_LEGAL_POSITIONS.remove(player.getUUID());

            if (updated.participants().size() > 1
                    && updated.aliveParticipants().size() <= 1) {
                beginRestoring(player.getServer(), updated);
            }
            break;
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_LEGAL_POSITIONS.remove(event.getEntity().getUUID());
    }

    public static void onTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || INTERNAL_TELEPORTS.contains(entity.getUUID())) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 from = event.getPrev();
        Vec3 to = event.getTarget();
        for (DomainRecord domain : activeDomains(level.getServer(), level)) {
            if (domain.contains(from) != domain.contains(to)) {
                event.setCanceled(true);
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(
                            Component.literal("The Domain barrier blocks teleportation.")
                                    .withStyle(ChatFormatting.AQUA),
                            true
                    );
                }
                return;
            }
        }
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile)
                || entity.level().isClientSide()
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 previous = new Vec3(entity.xo, entity.yo, entity.zo);
        Vec3 current = entity.position();

        for (DomainRecord domain : activeDomains(level.getServer(), level)) {
            if (domain.contains(previous) != domain.contains(current)) {
                projectile.discard();
                return;
            }
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled()
                || victim.level().isClientSide()
                || !(victim.level() instanceof ServerLevel level)) {
            return;
        }

        Entity source = event.getSource().getEntity();
        if (source == null) {
            source = event.getSource().getDirectEntity();
        }
        if (source == null || source.level() != victim.level()) {
            return;
        }

        for (DomainRecord domain : activeDomains(level.getServer(), level)) {
            boolean victimInside = domain.contains(victim.position());
            boolean sourceInside = domain.contains(source.position());
            if (victimInside != sourceInside) {
                event.setCanceled(true);
                return;
            }
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer().level() instanceof ServerLevel level)) {
            return;
        }

        if (isBarrierAt(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> isBarrierAt(level, pos));
    }

    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        var resolver = event.getStructureHelper();
        if (!resolver.resolve()) {
            return;
        }

        for (BlockPos pos : resolver.getToPush()) {
            if (isBarrierAt(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (isBarrierAt(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SHELL_BUILDS.clear();
        LAST_LEGAL_POSITIONS.clear();
        INTERNAL_TELEPORTS.clear();
    }

    private static void beginRestoring(MinecraftServer server, DomainRecord record) {
        DomainSavedData saved = data(server);
        DomainRecord current = saved.getDomain(record.id()).orElse(record);
        if (!current.isActive()) {
            return;
        }

        DomainRecord restoring = current.beginRestoring();
        saved.putDomain(restoring);
        SHELL_BUILDS.remove(restoring.id());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (restoring.participants().contains(player.getUUID())) {
                player.sendSystemMessage(
                        Component.literal("The Domain barrier is collapsing.")
                                .withStyle(ChatFormatting.AQUA)
                );
            }
        }
    }

    private static void buildBarrier(
            ServerLevel level,
            DomainRecord record,
            DomainSavedData saved
    ) {
        ShellBuild build = SHELL_BUILDS.computeIfAbsent(
                record.id(),
                ignored -> new ShellBuild(generateShell(record), 0)
        );

        int placedThisTick = 0;
        while (build.cursor < build.positions.size()
                && placedThisTick < DomainConfig.blocksPerTick()) {
            BlockPos pos = build.positions.get(build.cursor++);
            if (!level.hasChunkAt(pos)
                    || pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()) {
                continue;
            }

            if (saved.barrierAt(record.dimension(), pos).isPresent()
                    || TemporaryBlockManager.isTemporary(level, pos)) {
                continue;
            }

            if (placeBarrier(level, record, pos)) {
                placedThisTick++;
            }
        }

        if (build.cursor >= build.positions.size()) {
            SHELL_BUILDS.remove(record.id());
        }
    }

    private static boolean placeBarrier(
            ServerLevel level,
            DomainRecord domain,
            BlockPos pos
    ) {
        for (ServerPlayer player : level.players()) {
            if (player.getBoundingBox().intersects(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 1.0D
            )) {
                return false;
            }
        }

        return TemporaryBlockManager.replace(
                level,
                pos,
                domain.id(),
                ModBlocks.ARCANE_BARRIER.get().defaultBlockState()
        );
    }

    private static void restoreBarrier(
            ServerLevel level,
            DomainRecord domain,
            DomainSavedData saved
    ) {
        TemporaryBlockManager.RestoreBatch batch =
                TemporaryBlockManager.restoreOwned(
                        level,
                        domain.id(),
                        DomainConfig.restoresPerTick()
                );

        // Compatibility cleanup for Domains created by the earlier blue-glass
        // implementation. New Domains no longer create DomainBarrierRecords.
        int handled = batch.processed();
        List<DomainBarrierRecord> legacyBarriers = saved.barriersFor(domain.id());

        for (DomainBarrierRecord barrier : legacyBarriers) {
            if (handled >= DomainConfig.restoresPerTick()) {
                break;
            }
            if (!level.hasChunkAt(barrier.pos())) {
                continue;
            }

            BlockState current = level.getBlockState(barrier.pos());
            if (current.is(Blocks.BLUE_STAINED_GLASS)) {
                boolean restored = current.equals(barrier.originalState())
                        || level.setBlock(
                        barrier.pos(),
                        barrier.originalState(),
                        Block.UPDATE_ALL
                );

                if (!restored) {
                    continue;
                }
            }

            saved.removeBarrier(barrier.dimension(), barrier.pos());
            handled++;
        }

        if (!TemporaryBlockManager.hasOwner(level.getServer(), domain.id())
                && !saved.hasBarriers(domain.id())) {
            saved.removeDomain(domain.id());
            MageAdditions.LOGGER.info("Finished restoring Domain {}", domain.id());
        }
    }

    private static void enforcePlayerContainment(MinecraftServer server) {
        List<DomainRecord> active = activeDomains(server);
        if (active.isEmpty()) {
            LAST_LEGAL_POSITIONS.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = dimension(player.serverLevel());
            DomainRecord required = null;

            for (DomainRecord domain : active) {
                if (domain.dimension().equals(dimension)
                        && domain.isAliveParticipant(player.getUUID())) {
                    required = domain;
                    break;
                }
            }

            if (required != null) {
                if (required.contains(player.position())) {
                    LAST_LEGAL_POSITIONS.put(
                            player.getUUID(),
                            new LastLegalPosition(dimension, player.position())
                    );
                } else {
                    Vec3 destination = validLastPosition(player, required, true);
                    if (destination == null) {
                        destination = safeInsidePosition(player.serverLevel(), required);
                    }
                    internalTeleport(player, destination);
                }
                continue;
            }

            DomainRecord intruded = null;
            for (DomainRecord domain : active) {
                if (domain.dimension().equals(dimension)
                        && domain.contains(player.position())) {
                    intruded = domain;
                    break;
                }
            }

            if (intruded == null) {
                LAST_LEGAL_POSITIONS.put(
                        player.getUUID(),
                        new LastLegalPosition(dimension, player.position())
                );
            } else {
                Vec3 destination = validLastPosition(player, intruded, false);
                if (destination == null) {
                    destination = safeOutsidePosition(player.serverLevel(), intruded);
                }
                internalTeleport(player, destination);
            }
        }
    }

    private static Vec3 validLastPosition(
            ServerPlayer player,
            DomainRecord domain,
            boolean mustBeInside
    ) {
        LastLegalPosition last = LAST_LEGAL_POSITIONS.get(player.getUUID());
        if (last == null || !last.dimension.equals(domain.dimension())) {
            return null;
        }

        boolean inside = domain.contains(last.position);
        return inside == mustBeInside ? last.position : null;
    }

    private static Vec3 safeInsidePosition(ServerLevel level, DomainRecord domain) {
        BlockPos center = domain.center();
        for (int offset = 0; offset <= 8; offset++) {
            for (int direction : new int[]{1, -1}) {
                int dy = offset * direction;
                BlockPos feet = center.offset(0, dy, 0);
                if (!domain.shape().contains(center, Vec3.atCenterOf(feet), domain.radius())) {
                    continue;
                }
                if (level.getBlockState(feet).isAir()
                        && level.getBlockState(feet.above()).isAir()) {
                    return new Vec3(
                            feet.getX() + 0.5D,
                            feet.getY(),
                            feet.getZ() + 0.5D
                    );
                }
            }
        }

        return Vec3.atCenterOf(center);
    }

    private static Vec3 safeOutsidePosition(ServerLevel level, DomainRecord domain) {
        int x = domain.center().getX() + domain.radius() + 3;
        int z = domain.center().getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        y = Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 2, y));
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    private static void internalTeleport(ServerPlayer player, Vec3 destination) {
        INTERNAL_TELEPORTS.add(player.getUUID());
        try {
            player.teleportTo(
                    player.serverLevel(),
                    destination.x,
                    destination.y,
                    destination.z,
                    player.getYRot(),
                    player.getXRot()
            );
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        } finally {
            INTERNAL_TELEPORTS.remove(player.getUUID());
        }
    }

    private static void sendActionBars(MinecraftServer server) {
        for (DomainRecord domain : activeDomains(server)) {
            ServerLevel level = findLevel(server, domain.dimension());
            if (level == null) {
                continue;
            }

            int ticksRemaining = (int) Math.max(
                    0L,
                    domain.endGameTime() - level.getGameTime()
            );

            Component message = Component.literal(
                    "DOMAIN • "
                            + formatTime(ticksRemaining)
                            + " • "
                            + domain.aliveParticipants().size()
                            + " remaining"
            ).withStyle(ChatFormatting.AQUA);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (domain.isAliveParticipant(player.getUUID())) {
                    player.displayClientMessage(message, true);
                }
            }
        }
    }

    private static boolean isBarrierAt(ServerLevel level, BlockPos pos) {
        return TemporaryBlockManager.isTemporary(level, pos)
                || data(level.getServer())
                .barrierAt(dimension(level), pos)
                .isPresent();
    }

    private static List<DomainRecord> activeDomains(MinecraftServer server) {
        ArrayList<DomainRecord> result = new ArrayList<>();
        for (DomainRecord domain : data(server).allDomains()) {
            if (domain.isActive()) {
                result.add(domain);
            }
        }
        return result;
    }

    private static List<DomainRecord> activeDomains(
            MinecraftServer server,
            ServerLevel level
    ) {
        String dimension = dimension(level);
        ArrayList<DomainRecord> result = new ArrayList<>();
        for (DomainRecord domain : data(server).allDomains()) {
            if (domain.isActive() && domain.dimension().equals(dimension)) {
                result.add(domain);
            }
        }
        return result;
    }

    private static List<BlockPos> generateShell(DomainRecord domain) {
        LinkedHashSet<Long> packed = new LinkedHashSet<>();
        BlockPos center = domain.center();
        int radius = domain.radius();

        if (domain.shape() == DomainShape.BOX) {
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    add(packed, center, a, radius, b);
                    add(packed, center, a, -radius, b);
                    add(packed, center, radius, a, b);
                    add(packed, center, -radius, a, b);
                    add(packed, center, a, b, radius);
                    add(packed, center, a, b, -radius);
                }
            }
        } else {
            int radiusSquared = radius * radius;
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    int remainder = radiusSquared - a * a - b * b;
                    if (remainder < 0) {
                        continue;
                    }

                    int c = (int) Math.round(Math.sqrt(remainder));
                    add(packed, center, a, b, c);
                    add(packed, center, a, b, -c);
                    add(packed, center, a, c, b);
                    add(packed, center, a, -c, b);
                    add(packed, center, c, a, b);
                    add(packed, center, -c, a, b);
                }
            }
        }

        ArrayList<BlockPos> result = new ArrayList<>(packed.size());
        for (long value : packed) {
            result.add(BlockPos.of(value));
        }
        return result;
    }

    private static void add(
            Set<Long> positions,
            BlockPos center,
            int dx,
            int dy,
            int dz
    ) {
        positions.add(center.offset(dx, dy, dz).asLong());
    }

    private static boolean volumesOverlap(
            BlockPos firstCenter,
            int firstRadius,
            BlockPos secondCenter,
            int secondRadius
    ) {
        int combined = firstRadius + secondRadius;
        return Math.abs(firstCenter.getX() - secondCenter.getX()) <= combined
                && Math.abs(firstCenter.getY() - secondCenter.getY()) <= combined
                && Math.abs(firstCenter.getZ() - secondCenter.getZ()) <= combined;
    }

    private static boolean fail(
            ServerPlayer caster,
            boolean sendFailure,
            String message
    ) {
        if (sendFailure) {
            caster.displayClientMessage(
                    Component.literal(message).withStyle(ChatFormatting.RED),
                    true
            );
        }
        return false;
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (dimension(level).equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String formatTime(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static final class ShellBuild {
        private final List<BlockPos> positions;
        private int cursor;

        private ShellBuild(List<BlockPos> positions, int cursor) {
            this.positions = positions;
            this.cursor = cursor;
        }
    }

    private record LastLegalPosition(String dimension, Vec3 position) {
    }
}
