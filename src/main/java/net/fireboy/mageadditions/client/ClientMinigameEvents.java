package net.fireboy.mageadditions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuRequestPayload;
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

    private ClientMinigameEvents() {}

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MINIGAME_MENU);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (OPEN_MINIGAME_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(OpenMinigameMenuRequestPayload.INSTANCE);
            }
        }
    }
}
