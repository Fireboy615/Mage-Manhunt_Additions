package net.fireboy.mageadditions.server.temporaryblock;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.List;
import java.util.UUID;

public final class TemporaryBlockManager {
    private TemporaryBlockManager() {
    }

    public static TemporaryBlockSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TemporaryBlockSavedData.FACTORY,
                TemporaryBlockSavedData.DATA_NAME
        );
    }

    /**
     * Replaces any loaded block with a temporary state while preserving the
     * exact original BlockState and full BlockEntity NBT, if present.
     *
     * The original block entity is detached before the state swap so container
     * blocks cannot spill/drop their contents during onRemove. Fluids are
     * handled naturally because their BlockState is preserved like any other.
     */
    public static boolean replace(
            ServerLevel level,
            BlockPos pos,
            UUID ownerId,
            BlockState temporaryState
    ) {
        if (level == null
                || ownerId == null
                || temporaryState == null
                || !level.hasChunkAt(pos)
                || level.isOutsideBuildHeight(pos)) {
            return false;
        }

        String dimension = dimension(level);
        TemporaryBlockSavedData saved = data(level.getServer());

        if (saved.get(dimension, pos).isPresent()) {
            return false;
        }

        BlockState originalState = level.getBlockState(pos);
        BlockEntity originalEntity = level.getBlockEntity(pos);
        CompoundTag originalEntityTag = originalEntity == null
                ? null
                : originalEntity.saveWithFullMetadata(level.registryAccess());

        TemporaryBlockRecord record = new TemporaryBlockRecord(
                dimension,
                pos,
                ownerId,
                originalState,
                temporaryState,
                originalEntityTag
        );
        saved.put(record);

        if (originalEntity != null) {
            // Important: remove the BE before setBlock. Container blocks such as
            // chests query the level during onRemove and drop contents if the
            // container is still present.
            level.removeBlockEntity(pos);
        }

        boolean changed = level.setBlock(pos, temporaryState, Block.UPDATE_ALL);
        if (!changed && !level.getBlockState(pos).equals(temporaryState)) {
            saved.remove(dimension, pos);
            restoreBlockEntity(level, record);
            return false;
        }

        return true;
    }

    /**
     * Restores one recorded temporary block. Restoration only overwrites the
     * position when the exact temporary state is still present; otherwise the
     * record is treated as stale so newer world changes are never clobbered.
     */
    public static RestoreResult restore(
            ServerLevel level,
            BlockPos pos,
            UUID expectedOwner
    ) {
        String dimension = dimension(level);
        TemporaryBlockSavedData saved = data(level.getServer());
        TemporaryBlockRecord record = saved.get(dimension, pos).orElse(null);

        if (record == null) {
            return RestoreResult.NOT_FOUND;
        }
        if (expectedOwner != null && !record.ownerId().equals(expectedOwner)) {
            return RestoreResult.NOT_OWNER;
        }
        if (!level.hasChunkAt(pos)) {
            return RestoreResult.CHUNK_UNLOADED;
        }

        BlockState current = level.getBlockState(pos);
        if (!current.equals(record.temporaryState())) {
            saved.remove(dimension, pos);
            return RestoreResult.STALE;
        }

        if (!level.setBlock(pos, record.originalState(), Block.UPDATE_ALL)) {
            return RestoreResult.FAILED;
        }

        restoreBlockEntity(level, record);
        saved.remove(dimension, pos);
        return RestoreResult.RESTORED;
    }

    /**
     * Restores up to maxPerCall loaded blocks owned by one spell/effect.
     * Returns the number processed plus whether owned records still remain.
     */
    public static RestoreBatch restoreOwned(
            ServerLevel level,
            UUID ownerId,
            int maxPerCall
    ) {
        TemporaryBlockSavedData saved = data(level.getServer());
        List<TemporaryBlockRecord> records = saved.ownedBy(ownerId);
        int processed = 0;

        for (TemporaryBlockRecord record : records) {
            if (processed >= Math.max(1, maxPerCall)) {
                break;
            }
            if (!record.dimension().equals(dimension(level))) {
                continue;
            }

            RestoreResult result = restore(level, record.pos(), ownerId);
            if (result != RestoreResult.CHUNK_UNLOADED
                    && result != RestoreResult.FAILED) {
                processed++;
            }
        }

        return new RestoreBatch(processed, saved.hasOwner(ownerId));
    }

    public static boolean isTemporary(ServerLevel level, BlockPos pos) {
        return data(level.getServer()).get(dimension(level), pos).isPresent();
    }

    public static boolean isOwnedBy(ServerLevel level, BlockPos pos, UUID ownerId) {
        return data(level.getServer())
                .get(dimension(level), pos)
                .map(record -> record.ownerId().equals(ownerId))
                .orElse(false);
    }

    public static boolean hasOwner(MinecraftServer server, UUID ownerId) {
        return data(server).hasOwner(ownerId);
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer().level() instanceof ServerLevel level
                && isTemporary(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> isTemporary(level, pos));
        }
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
            if (isTemporary(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (isTemporary(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void restoreBlockEntity(
            ServerLevel level,
            TemporaryBlockRecord record
    ) {
        CompoundTag tag = record.originalBlockEntity();
        if (tag == null) {
            return;
        }

        try {
            BlockEntity existing = level.getBlockEntity(record.pos());
            if (existing != null) {
                existing.loadWithComponents(tag.copy(), level.registryAccess());
                existing.setChanged();
                return;
            }

            BlockEntity restored = BlockEntity.loadStatic(
                    record.pos(),
                    record.originalState(),
                    tag.copy(),
                    level.registryAccess()
            );
            if (restored != null) {
                restored.setLevel(level);
                level.setBlockEntity(restored);
                restored.setChanged();
            }
        } catch (Exception exception) {
            MageAdditions.LOGGER.error(
                    "Failed to restore temporary block entity at {} in {} for owner {}",
                    record.pos(),
                    record.dimension(),
                    record.ownerId(),
                    exception
            );
        }
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public enum RestoreResult {
        RESTORED,
        NOT_FOUND,
        NOT_OWNER,
        CHUNK_UNLOADED,
        STALE,
        FAILED
    }

    public record RestoreBatch(int processed, boolean remaining) {
    }
}
