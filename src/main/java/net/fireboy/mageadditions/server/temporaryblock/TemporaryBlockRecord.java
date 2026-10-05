package net.fireboy.mageadditions.server.temporaryblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public record TemporaryBlockRecord(
        String dimension,
        BlockPos pos,
        UUID ownerId,
        BlockState originalState,
        BlockState temporaryState,
        CompoundTag originalBlockEntity
) {
    public TemporaryBlockRecord {
        pos = pos.immutable();
        originalBlockEntity = originalBlockEntity == null ? null : originalBlockEntity.copy();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension);
        tag.putLong("Pos", pos.asLong());
        tag.putUUID("OwnerId", ownerId);
        tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        tag.put("TemporaryState", NbtUtils.writeBlockState(temporaryState));

        if (originalBlockEntity != null) {
            tag.put("OriginalBlockEntity", originalBlockEntity.copy());
        }
        return tag;
    }

    public static TemporaryBlockRecord load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        return new TemporaryBlockRecord(
                tag.getString("Dimension"),
                BlockPos.of(tag.getLong("Pos")),
                tag.getUUID("OwnerId"),
                NbtUtils.readBlockState(
                        registries.lookupOrThrow(Registries.BLOCK),
                        tag.getCompound("OriginalState")
                ),
                NbtUtils.readBlockState(
                        registries.lookupOrThrow(Registries.BLOCK),
                        tag.getCompound("TemporaryState")
                ),
                tag.contains("OriginalBlockEntity")
                        ? tag.getCompound("OriginalBlockEntity")
                        : null
        );
    }
}
