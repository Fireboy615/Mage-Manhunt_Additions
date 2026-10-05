package net.fireboy.mageadditions.registry;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MageAdditions.MODID);

    public static final DeferredBlock<TransparentBlock> ARCANE_BARRIER =
            BLOCKS.registerBlock(
                    "arcane_barrier",
                    TransparentBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.AMETHYST)
                            .noOcclusion()
                            .lightLevel(state -> 5)
            );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
