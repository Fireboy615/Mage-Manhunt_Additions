package net.fireboy.mageadditions.server.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record DomainRecord(
        UUID id,
        String dimension,
        UUID casterId,
        BlockPos center,
        DomainShape shape,
        int radius,
        int spellLevel,
        long createdGameTime,
        long endGameTime,
        Set<UUID> participants,
        Set<UUID> aliveParticipants,
        Phase phase
) {
    public DomainRecord {
        participants = Set.copyOf(participants);
        aliveParticipants = Set.copyOf(aliveParticipants);
    }

    public boolean contains(Vec3 position) {
        return shape.contains(center, position, radius);
    }

    public boolean isActive() {
        return phase == Phase.ACTIVE;
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public boolean isAliveParticipant(UUID playerId) {
        return aliveParticipants.contains(playerId);
    }

    public DomainRecord markDead(UUID playerId) {
        if (!aliveParticipants.contains(playerId)) {
            return this;
        }

        LinkedHashSet<UUID> alive = new LinkedHashSet<>(aliveParticipants);
        alive.remove(playerId);
        return new DomainRecord(
                id,
                dimension,
                casterId,
                center,
                shape,
                radius,
                spellLevel,
                createdGameTime,
                endGameTime,
                participants,
                alive,
                phase
        );
    }

    public DomainRecord beginRestoring() {
        if (phase == Phase.RESTORING) {
            return this;
        }

        return new DomainRecord(
                id,
                dimension,
                casterId,
                center,
                shape,
                radius,
                spellLevel,
                createdGameTime,
                endGameTime,
                participants,
                aliveParticipants,
                Phase.RESTORING
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Dimension", dimension);
        tag.putUUID("CasterId", casterId);
        tag.putLong("Center", center.asLong());
        tag.putString("Shape", shape.name());
        tag.putInt("Radius", radius);
        tag.putInt("SpellLevel", spellLevel);
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putLong("EndGameTime", endGameTime);
        tag.putString("Phase", phase.name());
        tag.put("Participants", saveIds(participants));
        tag.put("AliveParticipants", saveIds(aliveParticipants));
        return tag;
    }

    public static DomainRecord load(CompoundTag tag) {
        DomainShape shape;
        try {
            shape = DomainShape.valueOf(tag.getString("Shape"));
        } catch (IllegalArgumentException ignored) {
            shape = DomainShape.DOME;
        }

        Phase phase;
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ignored) {
            phase = Phase.ACTIVE;
        }

        return new DomainRecord(
                tag.getUUID("Id"),
                tag.getString("Dimension"),
                tag.getUUID("CasterId"),
                BlockPos.of(tag.getLong("Center")),
                shape,
                Math.max(1, tag.getInt("Radius")),
                Math.max(1, tag.getInt("SpellLevel")),
                tag.getLong("CreatedGameTime"),
                tag.getLong("EndGameTime"),
                loadIds(tag.getList("Participants", Tag.TAG_COMPOUND)),
                loadIds(tag.getList("AliveParticipants", Tag.TAG_COMPOUND)),
                phase
        );
    }

    private static ListTag saveIds(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            list.add(entry);
        }
        return list;
    }

    private static Set<UUID> loadIds(ListTag list) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (Tag entry : list) {
            CompoundTag compound = (CompoundTag) entry;
            if (compound.hasUUID("Id")) {
                ids.add(compound.getUUID("Id"));
            }
        }
        return ids;
    }

    public enum Phase {
        ACTIVE,
        RESTORING
    }
}
