package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.server.domain.DomainConfig;
import net.fireboy.mageadditions.server.domain.DomainManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

public final class DomainSpell extends AbstractSpell {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "domain");

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(60)
            .setAllowCrafting(false)
            .build();

    public DomainSpell() {
        baseManaCost = 200;
        manaCostPerLevel = 25;
        castTime = 40;
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
    public boolean allowLooting() {
        return false;
    }

    @Override
    public boolean allowCrafting() {
        return false;
    }

    @Override
    public boolean checkPreCastConditions(
            Level level,
            int spellLevel,
            LivingEntity caster,
            MagicData magicData
    ) {
        if (level.isClientSide()) {
            return super.checkPreCastConditions(level, spellLevel, caster, magicData);
        }

        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }

        return DomainManager.canStart(player, spellLevel, true)
                && super.checkPreCastConditions(level, spellLevel, caster, magicData);
    }

    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData magicData
    ) {
        if (!level.isClientSide() && caster instanceof ServerPlayer player) {
            DomainManager.createDomain(player, spellLevel);
        }

        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable(
                        "spell.mageadditions.domain.size",
                        DomainConfig.diameter(spellLevel)
                ),
                Component.translatable(
                        "ui.irons_spellbooks.effect_length",
                        Utils.timeFromTicks(DomainConfig.durationTicks(spellLevel), 1)
                ),
                Component.translatable("spell.mageadditions.domain.rule")
        );
    }
}
