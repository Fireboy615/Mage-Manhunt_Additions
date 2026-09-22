package net.fireboy.mageadditions.mixin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mage Manhunt is normally played from a fresh world, so make its preferred
 * singleplayer world-creation defaults visible before the player clicks Create.
 * Re-created/existing worlds are unaffected because vanilla applies their saved
 * difficulty and command setting after this state object is constructed.
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Shadow
    private Difficulty difficulty;

    @Shadow
    private Boolean allowCommands;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mageadditions$applyNewWorldDefaults(
        Path savesFolder,
        WorldCreationContext settings,
        Optional<ResourceKey<WorldPreset>> preset,
        OptionalLong seed,
        CallbackInfo ci
    ) {
        this.difficulty = Difficulty.EASY;
        this.allowCommands = true;
    }
}
