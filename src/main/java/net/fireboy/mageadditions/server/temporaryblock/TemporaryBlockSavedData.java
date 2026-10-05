package net.fireboy.mageadditions.server.temporaryblock;

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

public final class TemporaryBlockSavedData extends SavedData {
    public static final String DATA_NAME = "mageadditions_temporary_blocks";
    public static final Factory<TemporaryBlockSavedData> FACTORY =
            new Factory<>(TemporaryBlockSavedData::new, TemporaryBlockSavedData::load);

    private final Map<Key, TemporaryBlockRecord> records = new LinkedHashMap<>();

    public static TemporaryBlockSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        TemporaryBlockSavedData data = new TemporaryBlockSavedData();
        ListTag list = tag.getList("Records", Tag.TAG_COMPOUND);

        for (Tag entry : list) {
            TemporaryBlockRecord record =
                    TemporaryBlockRecord.load((CompoundTag) entry, registries);
            data.records.put(Key.of(record.dimension(), record.pos()), record);
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        ListTag list = new ListTag();
        for (TemporaryBlockRecord record : records.values()) {
            list.add(record.save());
        }
        tag.put("Records", list);
        return tag;
    }

    public Optional<TemporaryBlockRecord> get(String dimension, BlockPos pos) {
        return Optional.ofNullable(records.get(Key.of(dimension, pos)));
    }

    public void put(TemporaryBlockRecord record) {
        records.put(Key.of(record.dimension(), record.pos()), record);
        setDirty();
    }

    public void remove(String dimension, BlockPos pos) {
        if (records.remove(Key.of(dimension, pos)) != null) {
            setDirty();
        }
    }

    public List<TemporaryBlockRecord> ownedBy(UUID ownerId) {
        ArrayList<TemporaryBlockRecord> result = new ArrayList<>();
        for (TemporaryBlockRecord record : records.values()) {
            if (record.ownerId().equals(ownerId)) {
                result.add(record);
            }
        }
        return result;
    }

    public boolean hasOwner(UUID ownerId) {
        for (TemporaryBlockRecord record : records.values()) {
            if (record.ownerId().equals(ownerId)) {
                return true;
            }
        }
        return false;
    }

    public Collection<TemporaryBlockRecord> all() {
        return List.copyOf(records.values());
    }

    private record Key(String dimension, long packedPos) {
        static Key of(String dimension, BlockPos pos) {
            return new Key(dimension, pos.asLong());
        }
    }
}
