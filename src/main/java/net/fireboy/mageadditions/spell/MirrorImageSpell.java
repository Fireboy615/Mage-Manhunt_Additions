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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Deception spell that surrounds the caster with player-shaped moving images.
 * Level I forms a square, Level II a pentagon, Level III a hexagon.
 */
public final class MirrorImageSpell extends AbstractSpell {
    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "mirror_image");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(45)
            .build();

    public MirrorImageSpell() {
        this.baseManaCost = 80;
        this.manaCostPerLevel = 20;
        this.castTime = 20;
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

    public static int getDurationTicks(int spellLevel) {
        int effectiveLevel = Math.max(1, spellLevel);
        return 20 * (8 + (4 * Math.min(effectiveLevel, 3)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData magicData) {
        if (level instanceof ServerLevel serverLevel && caster instanceof ServerPlayer player) {
            MirrorImageManager.createMirrorImage(serverLevel, player, spellLevel);
        }

        super.onCast(level, spellLevel, caster, castSource, magicData);
    }
}
