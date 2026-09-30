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
import net.fireboy.mageadditions.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Self buff that gives any non-mace main-hand weapon vanilla-style mace smash
 * attacks for a duration.
 */
public final class MaceInfusionSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
        ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "mace_infusion");

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.LEGENDARY)
        .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
        .setMaxLevel(1)
        .setCooldownSeconds(30)
        .build();

    public MaceInfusionSpell() {
        this.baseManaCost = 200;
        this.manaCostPerLevel = 10;
        this.castTime = 40;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
            "ui.irons_spellbooks.effect_length",
            Utils.timeFromTicks(getDurationTicks(spellLevel), 1)
        ));
    }

    private static int getDurationTicks(int spellLevel) {
        return 20 * (10 + (10 * Math.max(1, spellLevel)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData magicData) {
        if (!level.isClientSide) {
            // Match Piercing's progression: 20s at level I, +10s each level.
            int durationTicks = getDurationTicks(spellLevel);
            caster.addEffect(new MobEffectInstance(ModEffects.MACE_INFUSION, durationTicks, 0, false, false, true));
        }

        super.onCast(level, spellLevel, caster, castSource, magicData);
    }
}
