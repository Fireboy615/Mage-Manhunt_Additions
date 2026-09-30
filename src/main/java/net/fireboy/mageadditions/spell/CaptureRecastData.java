package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public final class CaptureRecastData implements ICastDataSerializable {
    private UUID targetId;

    public CaptureRecastData() {
        this.targetId = new UUID(0L, 0L);
    }

    public CaptureRecastData(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID targetId() {
        return targetId;
    }

    @Override
    public void reset() {
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUUID(targetId);
    }

    @Override
    public void readFromBuffer(FriendlyByteBuf buffer) {
        targetId = buffer.readUUID();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("target", targetId);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.hasUUID("target")) {
            targetId = tag.getUUID("target");
        }
    }
}
