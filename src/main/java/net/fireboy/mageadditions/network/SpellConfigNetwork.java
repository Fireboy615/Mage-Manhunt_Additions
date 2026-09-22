package net.fireboy.mageadditions.network;

import net.fireboy.mageadditions.MageAdditions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers Mage Additions' server-authoritative spell editor packets.
 *
 * <p>This class self-registers on the mod bus so the spell editor does not need
 * another edit to MageAdditions.java. That keeps it isolated from the minigame
 * registration code being developed separately.</p>
 */
@EventBusSubscriber(modid = MageAdditions.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SpellConfigNetwork {
    private SpellConfigNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("9");

        registrar.playToServer(
                SpellConfigPayloads.Request.TYPE,
                SpellConfigPayloads.Request.STREAM_CODEC,
                SpellConfigServerPayloadHandler::handle
        );

        registrar.playToServer(
                SpellConfigPayloads.Update.TYPE,
                SpellConfigPayloads.Update.STREAM_CODEC,
                SpellConfigServerPayloadHandler::handle
        );

        registrar.playToClient(
                SpellConfigPayloads.Snapshot.TYPE,
                SpellConfigPayloads.Snapshot.STREAM_CODEC,
                SpellConfigClientPayloadHandler::handle
        );

        registrar.playToClient(
                SpellConfigPayloads.RuntimeSync.TYPE,
                SpellConfigPayloads.RuntimeSync.STREAM_CODEC,
                SpellConfigClientPayloadHandler::handle
        );
    }
}
