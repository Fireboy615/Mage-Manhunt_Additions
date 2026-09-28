package net.fireboy.mageadditions.registry;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.spell.MaceInfusionSpell;
import net.fireboy.mageadditions.spell.PiercingSpell;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS =
        DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, MageAdditions.MODID);

    public static final DeferredHolder<AbstractSpell, AbstractSpell> PIERCING =
        SPELLS.register("piercing", PiercingSpell::new);

    public static final DeferredHolder<AbstractSpell, AbstractSpell> MACE_INFUSION =
        SPELLS.register("mace_infusion", MaceInfusionSpell::new);

    private ModSpells() {
    }

    public static void register(IEventBus modEventBus) {
        SPELLS.register(modEventBus);
    }
}
