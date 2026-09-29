package net.fireboy.mageadditions.registry;

import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.effect.MaceInfusionEffect;
import net.fireboy.mageadditions.effect.MirrorCloakEffect;
import net.fireboy.mageadditions.effect.PiercingEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, MageAdditions.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> PIERCING =
        EFFECTS.register("piercing", PiercingEffect::new);

    public static final DeferredHolder<MobEffect, MobEffect> MIRROR_CLOAK =
        EFFECTS.register("mirror_cloak", MirrorCloakEffect::new);

    public static final DeferredHolder<MobEffect, MobEffect> MACE_INFUSION =
        EFFECTS.register("mace_infusion", MaceInfusionEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
