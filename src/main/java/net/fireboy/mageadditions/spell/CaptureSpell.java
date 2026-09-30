package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.network.payload.CaptureTargetPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CaptureSpell extends AbstractSpell {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "capture");
    private static final int CAST_RANGE = 8;
    private static final float AIM_ASSIST = 0.35F;
    private static final int RECAST_COUNT = 2;
    public static final int NON_PLAYER_STORAGE_TICKS = 20 * 60 * 10; // 10 minutes

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(60)
            .build();

    public CaptureSpell() {
        baseManaCost = 100;
        manaCostPerLevel = 15;
        castTime = 60;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return RECAST_COUNT;
    }

    @Override
    public ICastDataSerializable getEmptyCastData() {
        return new CaptureRecastData();
    }

    public static int durationTicks(int level) {
        return 20 * (6 + 3 * Math.max(1, level));
    }

    public int getRecastDuration(int spellLevel, LivingEntity caster) {
        return durationTicks(spellLevel);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.cast_range", Utils.stringTruncation(CAST_RANGE, 1)),
                Component.translatable("spell.mageadditions.capture.player_duration",
                        Utils.timeFromTicks(durationTicks(level), 1)),
                Component.translatable("spell.mageadditions.capture.mob_duration", "10:00"),
                Component.translatable("spell.mageadditions.capture.escape_info")
        );
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        // A recast releases the entity currently stored by this spell. It does not
        // require selecting that target again.
        if (magicData.getPlayerRecasts().hasRecastForSpell(getSpellId())) {
            if (caster instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new CaptureTargetPayload(-1));
            }
            return super.checkPreCastConditions(level, spellLevel, caster, magicData);
        }

        // First cast is entity-selection only. Lock the selected entity UUID now
        // so the same target is captured after the full three-second channel. This
        // intentionally accepts any normal Entity, not just LivingEntity targets.
        HitResult hit = Utils.raycastForEntity(
                level,
                caster,
                CAST_RANGE,
                true,
                AIM_ASSIST
        );

        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof PartEntity<?> partEntity) {
                target = partEntity.getParent();
            }

            if (target != caster && CaptureManager.canCaptureTarget(target)) {
                magicData.setAdditionalCastData(new CaptureRecastData(target.getUUID()));
                if (caster instanceof ServerPlayer player) {
                    PacketDistributor.sendToPlayer(player, new CaptureTargetPayload(target.getId()));
                }
                return super.checkPreCastConditions(level, spellLevel, caster, magicData);
            }
        }

        if (caster instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new CaptureTargetPayload(-1));
            player.sendSystemMessage(Component.translatable("message.mageadditions.capture.no_target")
                    .withStyle(ChatFormatting.RED));
        }
        return false;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData magicData) {
        if (!(level instanceof ServerLevel serverLevel) || !(caster instanceof ServerPlayer player)) {
            super.onCast(level, spellLevel, caster, source, magicData);
            return;
        }

        PacketDistributor.sendToPlayer(player, new CaptureTargetPayload(-1));

        RecastInstance recast = magicData.getPlayerRecasts().hasRecastForSpell(getSpellId())
                ? magicData.getPlayerRecasts().getRecastInstance(getSpellId())
                : null;

        if (recast != null) {
            if (recast.getCastData() instanceof CaptureRecastData data) {
                CaptureManager.releaseByCaster(player, data.targetId());
            } else {
                CaptureManager.releaseByCaster(player, null);
            }
            super.onCast(level, spellLevel, caster, source, magicData);
            return;
        }

        if (magicData.getAdditionalCastData() instanceof CaptureRecastData targeting) {
            Entity target = serverLevel.getEntity(targeting.targetId());
            if (target != null
                    && target.isAlive()
                    && target != caster
                    && CaptureManager.canCaptureTarget(target)) {
                int duration = target instanceof ServerPlayer && !(target instanceof FakePlayer)
                        ? durationTicks(spellLevel)
                        : NON_PLAYER_STORAGE_TICKS;
                if (CaptureManager.capture(player, target, duration)) {
                    magicData.getPlayerRecasts().addRecast(
                            new RecastInstance(
                                    getSpellId(),
                                    spellLevel,
                                    RECAST_COUNT,
                                    duration,
                                    source,
                                    new CaptureRecastData(target.getUUID())
                            ),
                            magicData
                    );
                }
            }
        }

        super.onCast(level, spellLevel, caster, source, magicData);
    }

    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance recastInstance, RecastResult result, ICastDataSerializable castData) {
        // If the recast timer expires, the maximum storage duration is expiring too.
        // CaptureManager releases the stored entity back into the world.
        if (result == RecastResult.TIMEOUT && castData instanceof CaptureRecastData data) {
            CaptureManager.releaseTimedOut(player, data.targetId());
        }
        super.onRecastFinished(player, recastInstance, result, castData);
    }
}
