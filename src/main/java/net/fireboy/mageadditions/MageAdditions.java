package net.fireboy.mageadditions;

import com.mojang.logging.LogUtils;
import net.fireboy.mageadditions.command.ModCommands;
import net.fireboy.mageadditions.compat.irons.MagehunterBalanceEvents;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.fireboy.mageadditions.enchantment.AspectEnchantmentEvents;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.minigame.MinigameServerEvents;
import net.fireboy.mageadditions.network.MinigameNetwork;
import net.fireboy.mageadditions.network.SpellConfigServerEvents;
import net.fireboy.mageadditions.registry.ModBlocks;
import net.fireboy.mageadditions.registry.ModEffects;
import net.fireboy.mageadditions.registry.ModItems;
import net.fireboy.mageadditions.registry.ModSpells;
import net.fireboy.mageadditions.server.domain.DomainConfig;
import net.fireboy.mageadditions.server.domain.DomainManager;
import net.fireboy.mageadditions.server.temporaryblock.TemporaryBlockManager;
import net.fireboy.mageadditions.rework.ArrowVolleyRework;
import net.fireboy.mageadditions.spell.CaptureManager;
import net.fireboy.mageadditions.spell.GenericSpellOverrideServerEvents;
import net.fireboy.mageadditions.spell.MirrorImageManager;
import net.fireboy.mageadditions.spell.ProjectileOverrideServerEvents;
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
        // Mage Additions registries.
        ModBlocks.register(modBus);
        ModEffects.register(modBus);
        ModSpells.register(modBus);
        ModItems.register(modBus);
        modBus.addListener(ModItems::addCreativeTabContents);

        // Existing startup/bootstrap behavior.
        CastTimeOverrides.reload();
        DomainConfig.reload();
        MinigameRegistry.bootstrap();

        // Existing network registration. This must stay on the MOD bus or
        // client -> server minigame payloads are rejected at runtime.
        modBus.addListener(MinigameNetwork::register);

        // Existing NeoForge gameplay/event listeners.
        NeoForge.EVENT_BUS.addListener(ModCommands::register);
        NeoForge.EVENT_BUS.addListener(MagehunterBalanceEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(SpellConfigServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SpellBehaviorServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ProjectileOverrideServerEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ArrowVolleyRework::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(AspectEnchantmentEvents::onDamageApplied);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onLivingKnockBack);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onSpellCast);
        NeoForge.EVENT_BUS.addListener(GenericSpellOverrideServerEvents::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(MirrorImageManager::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MirrorImageManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(MirrorImageManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CaptureManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(CaptureManager::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(CaptureManager::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CaptureManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DomainManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(DomainManager::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DomainManager::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(DomainManager::onTeleport);
        NeoForge.EVENT_BUS.addListener(DomainManager::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(DomainManager::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(TemporaryBlockManager::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(TemporaryBlockManager::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(TemporaryBlockManager::onPistonPre);
        NeoForge.EVENT_BUS.addListener(DomainManager::onServerStopped);
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
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onDamageApplied);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MinigameServerEvents::onServerStopped);

        LOGGER.info("Mage Additions loaded");
    }
}
