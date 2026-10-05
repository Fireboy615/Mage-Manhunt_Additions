package net.fireboy.mageadditions.server.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public record DomainBarrierRecord(
        String dimension,
        BlockPos pos,
        UUID domainId,
        BlockState originalState
) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension);
        tag.putLong("Pos", pos.asLong());
        tag.putUUID("DomainId", domainId);
        tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        return tag;
    }

    public static DomainBarrierRecord load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        BlockState originalState = NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK),
                tag.getCompound("OriginalState")
        );

        return new DomainBarrierRecord(
                tag.getString("Dimension"),
                BlockPos.of(tag.getLong("Pos")),
                tag.getUUID("DomainId"),
                originalState
        );
    }
}
