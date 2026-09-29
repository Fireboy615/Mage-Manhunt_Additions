package net.fireboy.mageadditions.mixin;

import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives Mage Manhunt's private teammate outline its team colour without changing
 * the colour of a real vanilla/spell Glowing effect.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineColorMixin {
    @Redirect(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"
        )
    )
    private int mageadditions$usePrivateTeammateOutlineColor(Entity entity) {
        if (!ClientMinigameState.shouldHighlight(entity.getUUID())) {
            return entity.getTeamColor();
        }

        // A genuine Glowing flag/effect should remain visually distinct from the
        // always-on private teammate outline.
        if (entity.isCurrentlyGlowing()) {
            return 0xFFFFFF;
        }

        return ClientMinigameState.teammateOutlineColor();
    }
}
