package net.fireboy.mageadditions.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BookDropMixin {

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;" +
                    "Lnet/minecraft/world/level/Level;" +
                    "Lnet/minecraft/core/BlockPos;" +
                    "Lnet/minecraft/world/level/block/entity/BlockEntity;" +
                    "Lnet/minecraft/world/entity/Entity;" +
                    "Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void mageadditions$replaceBookBlockDrops(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockEntity blockEntity,
            Entity entity,
            ItemStack tool,
            CallbackInfo ci
    ) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        if (!id.getNamespace().equals("irons_spellbooks")) {
            return;
        }

        boolean wisewoodShelf = id.getPath().equals("wisewood_bookshelf");
        boolean floorBooks = id.getPath().equals("book_stack");

        if (!wisewoodShelf && !floorBooks) {
            return;
        }

        if (!level.isClientSide) {
            Block.popResource(level, pos, new ItemStack(Items.BOOK, 3));
        }

        // Prevent Iron's normal loot table from also running.
        ci.cancel();
    }
}