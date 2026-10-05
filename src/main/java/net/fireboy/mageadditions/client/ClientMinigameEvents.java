package net.fireboy.mageadditions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fireboy.mageadditions.client.state.ClientCaptureState;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuRequestPayload;
import net.fireboy.mageadditions.network.payload.MirrorMovementInputPayload;
import net.fireboy.mageadditions.network.payload.CaptureStrugglePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientMinigameEvents {
    private static final KeyMapping OPEN_MINIGAME_MENU = new KeyMapping(
        "key.mageadditions.open_minigame_menu",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F8,
        "key.categories.mageadditions"
    );
    private static final KeyMapping OPEN_SPELL_CONFIG = new KeyMapping(
        "key.mageadditions.open_spell_config",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F7,
        "key.categories.mageadditions"
    );

    private static float LAST_MIRROR_FORWARD;
    private static float LAST_MIRROR_STRAFE;

    private ClientMinigameEvents() {}

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MINIGAME_MENU);
        event.register(OPEN_SPELL_CONFIG);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getConnection() == null) {
            // Do not carry one server's authoritative custom-spell availability
            // into the next connection. Login runtime sync repopulates this cache.
            CastTimeOverrides.clearSyncedCustomSpellUsable();
        }

        if (minecraft.player == null || minecraft.getConnection() == null) {
            ClientCaptureState.setCaptured(false);
        } else {
            // Vanilla does not tell the server which W/A/S/D keys a player is
            // holding when collision prevents movement. Mirror Image needs that
            // intent so clones with a clear path keep walking independently.
            float mirrorForward = 0.0F;
            float mirrorStrafe = 0.0F;
            if (minecraft.options.keyUp.isDown()) mirrorForward += 1.0F;
            if (minecraft.options.keyDown.isDown()) mirrorForward -= 1.0F;
            if (minecraft.options.keyRight.isDown()) mirrorStrafe += 1.0F;
            if (minecraft.options.keyLeft.isDown()) mirrorStrafe -= 1.0F;

            // Send continuously while a movement key is held, plus one zero packet
            // when movement stops. The server ignores this unless mirrors exist.
            if (mirrorForward != 0.0F || mirrorStrafe != 0.0F
                    || LAST_MIRROR_FORWARD != 0.0F || LAST_MIRROR_STRAFE != 0.0F) {
                PacketDistributor.sendToServer(new MirrorMovementInputPayload(mirrorForward, mirrorStrafe));
            }
            LAST_MIRROR_FORWARD = mirrorForward;
            LAST_MIRROR_STRAFE = mirrorStrafe;

            if (ClientCaptureState.isCaptured()) {
            // Capture uses the player's normal jump key (Space by default). Consume
            // the click while stored so it becomes a struggle attempt instead of
            // spectator flight input. Rapid physical taps produce rapid attempts.
                while (minecraft.options.keyJump.consumeClick()) {
                    PacketDistributor.sendToServer(CaptureStrugglePayload.INSTANCE);
                }
            }
        }

        while (OPEN_MINIGAME_MENU.consumeClick()) {
            if (CastTimeOverrides.minigameEnabled()
                    && minecraft.player != null && minecraft.getConnection() != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(OpenMinigameMenuRequestPayload.INSTANCE);
            }
        }

        while (OPEN_SPELL_CONFIG.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null
                    && !(minecraft.screen instanceof MageAdditionsConfigScreen)) {
                minecraft.setScreen(new MageAdditionsConfigScreen(minecraft.screen));
            }
        }
    }
}
