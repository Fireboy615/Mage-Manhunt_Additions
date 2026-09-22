package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.MageAdditions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Physical-client bootstrap kept separate so dedicated servers never touch
 * client-only classes.
 */
@Mod(value = MageAdditions.MODID, dist = Dist.CLIENT)
public final class MageAdditionsClient {
    public MageAdditionsClient(IEventBus modBus, ModContainer container) {
        // Restore the Config button on Mods -> Mage Additions.
        // NeoForge asks this factory to create the screen when the button is clicked.
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parentScreen) -> new MageAdditionsConfigScreen(parentScreen)
        );

        // Existing Mage Additions client/minigame registrations.
        modBus.addListener(ClientMinigameEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientMinigameEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientFovEvents::onComputeFovModifier);
    }
}
