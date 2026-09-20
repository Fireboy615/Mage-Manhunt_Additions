package net.fireboy.mageadditions;

import com.mojang.logging.LogUtils;
import net.fireboy.mageadditions.command.ModCommands;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.minigame.MinigameServerEvents;
import net.fireboy.mageadditions.network.MinigameNetwork;
import net.fireboy.mageadditions.network.SpellConfigServerEvents;
import net.fireboy.mageadditions.spell.SpellBehaviorServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MageAdditions.MODID)
public final class MageAdditions {
    public static final String MODID = "mageadditions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MageAdditions(IEventBus modBus) {
        CastTimeOverrides.reload();
        MinigameRegistry.bootstrap();

        modBus.addListener(MinigameNetwork::register);

        NeoForge.EVENT_BUS.addListener(ModCommands::register);
        NeoForge.EVENT_BUS.addListener(SpellConfigServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SpellBehaviorServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerStopped);

        LOGGER.info("Mage Additions loaded");
    }
}
