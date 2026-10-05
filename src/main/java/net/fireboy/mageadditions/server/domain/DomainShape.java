package net.fireboy.mageadditions.server.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public enum DomainShape {
    BOX,
    DOME;

    public boolean contains(BlockPos center, Vec3 position, int radius) {
        double cx = center.getX() + 0.5D;
        double cy = center.getY() + 0.5D;
        double cz = center.getZ() + 0.5D;

        double dx = position.x - cx;
        double dy = position.y - cy;
        double dz = position.z - cz;

        return switch (this) {
            case BOX -> Math.abs(dx) <= radius
                    && Math.abs(dy) <= radius
                    && Math.abs(dz) <= radius;
            case DOME -> dx * dx + dy * dy + dz * dz <= (double) radius * radius;
        };
    }

    public boolean contains(BlockPos center, BlockPos pos, int radius) {
        return contains(center, Vec3.atCenterOf(pos), radius);
    }

    public boolean isShellBlock(BlockPos center, BlockPos pos, int radius) {
        int dx = pos.getX() - center.getX();
        int dy = pos.getY() - center.getY();
        int dz = pos.getZ() - center.getZ();

        return switch (this) {
            case BOX -> Math.abs(dx) <= radius
                    && Math.abs(dy) <= radius
                    && Math.abs(dz) <= radius
                    && (Math.abs(dx) == radius
                    || Math.abs(dy) == radius
                    || Math.abs(dz) == radius);
            case DOME -> {
                double distanceSquared = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                double outer = (double) radius * radius;
                double innerRadius = Math.max(0.0D, radius - 1.25D);
                double inner = innerRadius * innerRadius;
                yield distanceSquared <= outer && distanceSquared >= inner;
            }
        };
    }
}
