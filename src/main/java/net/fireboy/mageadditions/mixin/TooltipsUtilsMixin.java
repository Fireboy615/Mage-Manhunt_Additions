package net.fireboy.mageadditions.mixin;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Makes Iron's standard spell tooltip reflect Mage Additions' live effect-duration
 * override. TooltipsUtils is the common caller for active-spell and scroll tooltips,
 * so this also works for Iron's own spells that override getUniqueInfo().
 */
@Mixin(value = TooltipsUtils.class, remap = false)
public abstract class TooltipsUtilsMixin {
    private static final String EFFECT_LENGTH_KEY = "ui.irons_spellbooks.effect_length";

    @Redirect(
            method = {"formatActiveSpellTooltip", "formatScrollTooltip"},
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getUniqueInfo(ILnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;",
                    remap = false
            ),
            remap = false
    )
    private static List<MutableComponent> mageAdditions$resolvedUniqueInfo(
            AbstractSpell spell,
            int spellLevel,
            LivingEntity caster
    ) {
        List<MutableComponent> original = spell.getUniqueInfo(spellLevel, caster);
        CastTimeOverrides.NumericOverride override =
                CastTimeOverrides.behavior(spell).effectDurationOverride();
        if (!override.enabled() || original.isEmpty()) {
            return original;
        }

        List<MutableComponent> resolved = new ArrayList<>(original.size());
        for (MutableComponent component : original) {
            resolved.add(mageAdditions$rewriteEffectLength(spell, component, override));
        }
        return resolved;
    }

    private static MutableComponent mageAdditions$rewriteEffectLength(
            AbstractSpell spell,
            MutableComponent original,
            CastTimeOverrides.NumericOverride override
    ) {
        if (!(original.getContents() instanceof TranslatableContents contents)
                || !EFFECT_LENGTH_KEY.equals(contents.getKey())) {
            return original;
        }

        Object[] args = contents.getArgs();
        if (args.length == 0) {
            return original;
        }

        int baseTicks;
        if (override.mode() == CastTimeOverrides.NumericMode.ABSOLUTE) {
            // Absolute mode ignores the native duration, so zero is a valid base.
            baseTicks = 0;
        } else {
            baseTicks = mageAdditions$parseDisplayedTimeTicks(args[0]);
            if (baseTicks < 0) {
                return original;
            }
        }

        int resolvedTicks = CastTimeOverrides.resolveEffectDurationTicks(spell, baseTicks);
        Object[] replacementArgs = args.clone();
        replacementArgs[0] = Utils.timeFromTicks(resolvedTicks, 1);

        MutableComponent replacement = Component.translatable(contents.getKey(), replacementArgs)
                .withStyle(original.getStyle());
        for (Component sibling : original.getSiblings()) {
            replacement.append(sibling.copy());
        }
        return replacement;
    }

    /**
     * Iron's Utils.timeFromTicks emits values such as 20.0s or 1.5m. For
     * multiplier mode we convert that already-displayed native value back to
     * ticks, apply the override, then format it using Iron's own formatter.
     */
    private static int mageAdditions$parseDisplayedTimeTicks(Object argument) {
        if (argument == null) {
            return -1;
        }

        String text = String.valueOf(argument).trim().toLowerCase(Locale.ROOT);
        double tickMultiplier;
        if (text.endsWith("s")) {
            tickMultiplier = 20.0;
        } else if (text.endsWith("m")) {
            tickMultiplier = 20.0 * 60.0;
        } else {
            return -1;
        }

        try {
            double amount = Double.parseDouble(text.substring(0, text.length() - 1).trim());
            if (!Double.isFinite(amount) || amount < 0.0) {
                return -1;
            }
            return (int) Math.round(amount * tickMultiplier);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
