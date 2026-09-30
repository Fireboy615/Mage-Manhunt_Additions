package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.client.state.ClientCaptureState;
import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Colours Mage Additions' private outlines without changing the colour/state of
 * genuine vanilla or spell-applied Glowing.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineColorMixin {
    private static final int CAPTURE_TARGET_COLOR = 0x55FF55;

    @Redirect(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"
        )
    )
    private int mageadditions$usePrivateOutlineColor(Entity entity) {
        // A genuine Glowing flag/effect remains visually distinct from our private
        // client-only selection/team outlines.
        if (entity.isCurrentlyGlowing()) {
            return 0xFFFFFF;
        }

        if (ClientCaptureState.isSelectedTarget(entity.getId())) {
            return CAPTURE_TARGET_COLOR;
        }

        if (ClientMinigameState.shouldHighlight(entity.getUUID())) {
            return ClientMinigameState.teammateOutlineColor();
        }

        return entity.getTeamColor();
    }
}
