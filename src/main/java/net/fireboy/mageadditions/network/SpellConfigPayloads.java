package net.fireboy.mageadditions.network;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Network DTOs for the server-authoritative spell editor. */
public final class SpellConfigPayloads {
    private SpellConfigPayloads() {}

    public record Request(ResourceLocation spellId) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "spell_config_request")
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Request decode(RegistryFriendlyByteBuf buffer) {
                return new Request(ResourceLocation.STREAM_CODEC.decode(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Request value) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.spellId());
            }
        };

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * Editor update. Mage Additions no longer duplicates Iron's mana/cooldown
     * overrides; those native values above remain the single source of truth.
     */
    public record Update(
            ResourceLocation spellId,
            boolean enabled,
            ResourceLocation school,
            int maxLevel,
            String minRarity,
            double manaMultiplier,
            double powerMultiplier,
            double cooldownSeconds,
            boolean allowCrafting,
            boolean mageOverridesEnabled,
            String castMode,
            double castValue,
            String rangeMode,
            double rangeValue,
            String movementMode,
            double movementMultiplier,
            boolean maxHeightEnabled,
            double maxHeightAboveGround,
            boolean hasLineOfSightOverride,
            boolean lineOfSightValue,
            boolean hasMinCastDistance,
            double minCastDistance,
            boolean hasMaxCastDistance,
            double maxCastDistance
    ) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "spell_config_update")
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, Update> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Update decode(RegistryFriendlyByteBuf buffer) {
                return new Update(
                        ResourceLocation.STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(),
                        ResourceLocation.STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(),
                        buffer.readUtf(64),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(32),
                        buffer.readDouble(),
                        buffer.readUtf(32),
                        buffer.readDouble(),
                        buffer.readUtf(32),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readDouble()
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Update value) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.spellId());
                buffer.writeBoolean(value.enabled());
                ResourceLocation.STREAM_CODEC.encode(buffer, value.school());
                buffer.writeVarInt(value.maxLevel());
                buffer.writeUtf(value.minRarity(), 64);
                buffer.writeDouble(value.manaMultiplier());
                buffer.writeDouble(value.powerMultiplier());
                buffer.writeDouble(value.cooldownSeconds());
                buffer.writeBoolean(value.allowCrafting());
                buffer.writeBoolean(value.mageOverridesEnabled());
                buffer.writeUtf(value.castMode(), 32);
                buffer.writeDouble(value.castValue());
                buffer.writeUtf(value.rangeMode(), 32);
                buffer.writeDouble(value.rangeValue());
                buffer.writeUtf(value.movementMode(), 32);
                buffer.writeDouble(value.movementMultiplier());
                buffer.writeBoolean(value.maxHeightEnabled());
                buffer.writeDouble(value.maxHeightAboveGround());
                buffer.writeBoolean(value.hasLineOfSightOverride());
                buffer.writeBoolean(value.lineOfSightValue());
                buffer.writeBoolean(value.hasMinCastDistance());
                buffer.writeDouble(value.minCastDistance());
                buffer.writeBoolean(value.hasMaxCastDistance());
                buffer.writeDouble(value.maxCastDistance());
            }
        };

        @Override
        public Type<Update> type() {
            return TYPE;
        }
    }

    public record Snapshot(
            ResourceLocation spellId,
            boolean success,
            String message,
            boolean canEdit,
            String backendName,
            boolean enabled,
            ResourceLocation school,
            int maxLevel,
            String minRarity,
            double manaMultiplier,
            double powerMultiplier,
            double cooldownSeconds,
            boolean allowCrafting,
            boolean mageOverridesEnabled,
            String castMode,
            double castValue,
            String rangeMode,
            double rangeValue,
            double originalTargetRange,
            String movementMode,
            double movementMultiplier,
            boolean maxHeightEnabled,
            double maxHeightAboveGround,
            boolean hasLineOfSightOverride,
            boolean lineOfSightValue,
            boolean originalLineOfSight,
            boolean hasMinCastDistance,
            double minCastDistance,
            double originalMinCastDistance,
            boolean hasMaxCastDistance,
            double maxCastDistance,
            double originalMaxCastDistance
    ) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "spell_config_snapshot")
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Snapshot decode(RegistryFriendlyByteBuf buffer) {
                return new Snapshot(
                        ResourceLocation.STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(),
                        buffer.readUtf(512),
                        buffer.readBoolean(),
                        buffer.readUtf(128),
                        buffer.readBoolean(),
                        ResourceLocation.STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(),
                        buffer.readUtf(64),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(32),
                        buffer.readDouble(),
                        buffer.readUtf(32),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readUtf(32),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readBoolean(),
                        buffer.readDouble(),
                        buffer.readDouble()
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Snapshot value) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.spellId());
                buffer.writeBoolean(value.success());
                buffer.writeUtf(value.message(), 512);
                buffer.writeBoolean(value.canEdit());
                buffer.writeUtf(value.backendName(), 128);
                buffer.writeBoolean(value.enabled());
                ResourceLocation.STREAM_CODEC.encode(buffer, value.school());
                buffer.writeVarInt(value.maxLevel());
                buffer.writeUtf(value.minRarity(), 64);
                buffer.writeDouble(value.manaMultiplier());
                buffer.writeDouble(value.powerMultiplier());
                buffer.writeDouble(value.cooldownSeconds());
                buffer.writeBoolean(value.allowCrafting());
                buffer.writeBoolean(value.mageOverridesEnabled());
                buffer.writeUtf(value.castMode(), 32);
                buffer.writeDouble(value.castValue());
                buffer.writeUtf(value.rangeMode(), 32);
                buffer.writeDouble(value.rangeValue());
                buffer.writeDouble(value.originalTargetRange());
                buffer.writeUtf(value.movementMode(), 32);
                buffer.writeDouble(value.movementMultiplier());
                buffer.writeBoolean(value.maxHeightEnabled());
                buffer.writeDouble(value.maxHeightAboveGround());
                buffer.writeBoolean(value.hasLineOfSightOverride());
                buffer.writeBoolean(value.lineOfSightValue());
                buffer.writeBoolean(value.originalLineOfSight());
                buffer.writeBoolean(value.hasMinCastDistance());
                buffer.writeDouble(value.minCastDistance());
                buffer.writeDouble(value.originalMinCastDistance());
                buffer.writeBoolean(value.hasMaxCastDistance());
                buffer.writeDouble(value.maxCastDistance());
                buffer.writeDouble(value.originalMaxCastDistance());
            }
        };

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /**
     * Iron's live values sent from the server to clients. This packet is kept
     * separate from Snapshot because Snapshot also carries editor-only state and
     * permissions. RuntimeSync can therefore be broadcast safely to everyone.
     */
    public record RuntimeEntry(
            ResourceLocation spellId,
            boolean enabled,
            ResourceLocation school,
            int maxLevel,
            String minRarity,
            double manaMultiplier,
            double powerMultiplier,
            double cooldownSeconds,
            boolean allowCrafting
    ) {}

    public record RuntimeSync(java.util.List<RuntimeEntry> entries) implements CustomPacketPayload {
        private static final int MAX_ENTRIES = 4096;

        public static final Type<RuntimeSync> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "spell_config_runtime_sync")
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, RuntimeSync> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public RuntimeSync decode(RegistryFriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Invalid spell runtime sync size: " + size);
                }

                java.util.List<RuntimeEntry> entries = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new RuntimeEntry(
                            ResourceLocation.STREAM_CODEC.decode(buffer),
                            buffer.readBoolean(),
                            ResourceLocation.STREAM_CODEC.decode(buffer),
                            buffer.readVarInt(),
                            buffer.readUtf(64),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readBoolean()
                    ));
                }
                return new RuntimeSync(java.util.List.copyOf(entries));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, RuntimeSync value) {
                if (value.entries().size() > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Too many spell runtime entries: " + value.entries().size());
                }
                buffer.writeVarInt(value.entries().size());
                for (RuntimeEntry entry : value.entries()) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, entry.spellId());
                    buffer.writeBoolean(entry.enabled());
                    ResourceLocation.STREAM_CODEC.encode(buffer, entry.school());
                    buffer.writeVarInt(entry.maxLevel());
                    buffer.writeUtf(entry.minRarity(), 64);
                    buffer.writeDouble(entry.manaMultiplier());
                    buffer.writeDouble(entry.powerMultiplier());
                    buffer.writeDouble(entry.cooldownSeconds());
                    buffer.writeBoolean(entry.allowCrafting());
                }
            }
        };

        @Override
        public Type<RuntimeSync> type() {
            return TYPE;
        }
    }
}
