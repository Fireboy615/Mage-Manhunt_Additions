package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.List;

public final class CaptureSpell extends AbstractSpell {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "capture");
    private final DefaultConfig config = new DefaultConfig().setMinRarity(SpellRarity.EPIC).setSchoolResource(SchoolRegistry.ENDER_RESOURCE).setMaxLevel(5).setCooldownSeconds(25).build();
    public CaptureSpell(){ baseManaCost=70; manaCostPerLevel=15; castTime=20; }
    @Override public ResourceLocation getSpellResource(){ return ID; }
    @Override public DefaultConfig getDefaultConfig(){ return config; }
    @Override public CastType getCastType(){ return CastType.LONG; }
    public static int durationTicks(int level){ return 20 * (6 + 3 * Math.max(1, level)); }
    @Override public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster){
        return List.of(Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(durationTicks(level),1)), Component.translatable("spell.mageadditions.capture.escape_info"));
    }
    @Override public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data){
        if(!level.isClientSide) CaptureManager.cast(caster, spellLevel);
        super.onCast(level, spellLevel, caster, source, data);
    }
}
