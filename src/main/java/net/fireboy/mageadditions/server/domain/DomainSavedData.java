package net.fireboy.mageadditions.server.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DomainSavedData extends SavedData {
    public static final String DATA_NAME = "mageadditions_domains";
    public static final Factory<DomainSavedData> FACTORY =
            new Factory<>(DomainSavedData::new, DomainSavedData::load);

    private final Map<UUID, DomainRecord> domains = new LinkedHashMap<>();
    private final Map<BarrierKey, DomainBarrierRecord> barriers = new LinkedHashMap<>();

    public static DomainSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        DomainSavedData data = new DomainSavedData();

        ListTag domainList = tag.getList("Domains", Tag.TAG_COMPOUND);
        for (Tag entry : domainList) {
            DomainRecord record = DomainRecord.load((CompoundTag) entry);
            data.domains.put(record.id(), record);
        }

        ListTag barrierList = tag.getList("Barriers", Tag.TAG_COMPOUND);
        for (Tag entry : barrierList) {
            DomainBarrierRecord record = DomainBarrierRecord.load(
                    (CompoundTag) entry,
                    registries
            );
            data.barriers.put(BarrierKey.of(record.dimension(), record.pos()), record);
        }

        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        ListTag domainList = new ListTag();
        for (DomainRecord record : domains.values()) {
            domainList.add(record.save());
        }
        tag.put("Domains", domainList);

        ListTag barrierList = new ListTag();
        for (DomainBarrierRecord record : barriers.values()) {
            barrierList.add(record.save());
        }
        tag.put("Barriers", barrierList);

        return tag;
    }

    public Collection<DomainRecord> allDomains() {
        return List.copyOf(domains.values());
    }

    public Optional<DomainRecord> getDomain(UUID domainId) {
        return Optional.ofNullable(domains.get(domainId));
    }

    public void putDomain(DomainRecord record) {
        domains.put(record.id(), record);
        setDirty();
    }

    public void removeDomain(UUID domainId) {
        if (domains.remove(domainId) != null) {
            setDirty();
        }
    }

    public Optional<DomainBarrierRecord> barrierAt(String dimension, BlockPos pos) {
        return Optional.ofNullable(barriers.get(BarrierKey.of(dimension, pos)));
    }

    public void putBarrier(DomainBarrierRecord record) {
        barriers.put(BarrierKey.of(record.dimension(), record.pos()), record);
        setDirty();
    }

    public void removeBarrier(String dimension, BlockPos pos) {
        if (barriers.remove(BarrierKey.of(dimension, pos)) != null) {
            setDirty();
        }
    }

    public List<DomainBarrierRecord> barriersFor(UUID domainId) {
        ArrayList<DomainBarrierRecord> result = new ArrayList<>();
        for (DomainBarrierRecord record : barriers.values()) {
            if (record.domainId().equals(domainId)) {
                result.add(record);
            }
        }
        return result;
    }

    public boolean hasBarriers(UUID domainId) {
        for (DomainBarrierRecord record : barriers.values()) {
            if (record.domainId().equals(domainId)) {
                return true;
            }
        }
        return false;
    }

    private record BarrierKey(String dimension, long packedPos) {
        static BarrierKey of(String dimension, BlockPos pos) {
            return new BarrierKey(dimension, pos.asLong());
        }
    }
}
