package net.fireboy.mageadditions.network;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.fireboy.mageadditions.compat.irons.IronsSpellConfigBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Server-side distribution of Iron's live spell balancing values. */
public final class SpellConfigSyncService {
    private SpellConfigSyncService() {}

    public static void broadcast(AbstractSpell spell) {
        PacketDistributor.sendToAllPlayers(new SpellConfigPayloads.RuntimeSync(List.of(toEntry(spell))));
    }

    public static void sendAllTo(ServerPlayer player) {
        List<SpellConfigPayloads.RuntimeEntry> entries = SpellRegistry.REGISTRY.stream()
                .filter(spell -> spell != SpellRegistry.none())
                .map(SpellConfigSyncService::toEntry)
                .toList();

        PacketDistributor.sendToPlayer(player, new SpellConfigPayloads.RuntimeSync(entries));
    }

    private static SpellConfigPayloads.RuntimeEntry toEntry(AbstractSpell spell) {
        IronsSpellConfigBridge.Settings settings = IronsSpellConfigBridge.read(spell);
        return new SpellConfigPayloads.RuntimeEntry(
                spell.getSpellResource(),
                settings.enabled(),
                settings.school(),
                settings.maxLevel(),
                settings.minRarity().name(),
                settings.manaMultiplier(),
                settings.powerMultiplier(),
                settings.cooldownSeconds(),
                settings.allowCrafting()
        );
    }
}
