package net.fireboy.mageadditions.rework;

import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Safety rules for Iron's Evasion teleport.
 *
 * <p>The native effect samples a random Y value and then lets
 * LivingEntity#randomTeleport drop the entity downward until it finds a solid
 * floor. That can unexpectedly send a surface fight deep underground. This
 * rework keeps Iron's normal sampling/retry behavior, while still allowing
 * sensible same-height/upward teleports inside low-ceiling caves.</p>
 */
public final class EvasionRework {
    private EvasionRework() {}

    public static boolean safeRandomTeleport(
            LivingEntity entity,
            double x,
            double y,
            double z,
            boolean broadcast
    ) {
        if (!CastTimeOverrides.spellReworksEnabled() || !(entity instanceof Player)) {
            return entity.randomTeleport(x, y, z, broadcast);
        }

        Level level = entity.level();
        BlockPos candidate = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(candidate)) {
            return false;
        }

        double landingY = resolveLandingY(level, candidate, y);
        if (Double.isNaN(landingY)) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            int surfaceY = serverLevel.getHeight(
                    Heightmap.Types.MOTION_BLOCKING,
                    Mth.floor(x),
                    Mth.floor(z)
            );

            // Heightmaps return the first air block above the highest blocking
            // surface. If the player's head remains below that height, this is a
            // covered/underground destination rather than open terrain.
            double playerTopY = landingY + entity.getBbHeight();
            boolean coveredDestination = playerTopY + 1.0E-4D < surfaceY;

            if (coveredDestination) {
                boolean currentlyInLowCeilingCave = hasSolidCeilingWithin(
                        level,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        3
                );
                boolean destinationHasNearbyCeiling = hasSolidCeilingWithin(
                        level,
                        x,
                        landingY,
                        z,
                        3
                );
                boolean movesDownward = landingY + 1.0E-4D < entity.getY();

                // Cave exception: while already fighting beneath a nearby roof,
                // Evasion may choose another nearby covered space, but only if
                // that landing is level/upward and also has a ceiling within
                // three blocks. This prevents deep downward cave teleports.
                if (!currentlyInLowCeilingCave
                        || !destinationHasNearbyCeiling
                        || movesDownward) {
                    return false;
                }
            }
        }

        return entity.randomTeleport(x, y, z, broadcast);
    }

    private static boolean hasSolidCeilingWithin(
            Level level,
            double x,
            double feetY,
            double z,
            int maxBlocks
    ) {
        int baseY = Mth.floor(feetY);
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);

        for (int offset = 1; offset <= maxBlocks; offset++) {
            BlockPos pos = new BlockPos(blockX, baseY + offset, blockZ);
            if (level.getBlockState(pos).blocksMotion()) {
                return true;
            }
        }
        return false;
    }

    private static double resolveLandingY(Level level, BlockPos start, double requestedY) {
        BlockPos cursor = start;
        double landingY = requestedY;

        while (cursor.getY() > level.getMinBuildHeight()) {
            BlockPos below = cursor.below();
            if (level.getBlockState(below).blocksMotion()) {
                return landingY;
            }

            cursor = below;
            landingY -= 1.0D;
        }

        return Double.NaN;
    }
}
