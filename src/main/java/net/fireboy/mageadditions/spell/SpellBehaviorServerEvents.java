package net.fireboy.mageadditions.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runtime enforcement for Mage Additions behaviours that apply while casting. */
public final class SpellBehaviorServerEvents {
    private static final ResourceLocation MOVEMENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            MageAdditions.MODID,
            "spell_cast_movement"
    );

    /**
     * Avoid removing/re-adding a syncable attribute modifier every server tick.
     * We only touch it when the player starts/stops casting, changes spell, or
     * the configured movement mode/value changes.
     */
    private static final Map<UUID, AppliedMovement> APPLIED = new HashMap<>();

    private SpellBehaviorServerEvents() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            updateCastingMovement(player);
        }
        APPLIED.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private static void updateCastingMovement(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            APPLIED.remove(player.getUUID());
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (!magicData.isCasting()) {
            clearModifier(player.getUUID(), movementSpeed);
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(magicData.getCastingSpellId());
        if (spell == null || spell == SpellRegistry.none()) {
            clearModifier(player.getUUID(), movementSpeed);
            return;
        }

        CastTimeOverrides.BehaviorSettings behavior = CastTimeOverrides.behavior(spell);
        Double desiredMultiplier = switch (behavior.movementMode()) {
            case NORMAL -> 1.0;
            case SLOWED -> Math.max(0.0, Math.min(10.0, behavior.movementMultiplier()));
            case ROOTED -> 0.0;
            default -> null;
        };

        if (desiredMultiplier == null) {
            clearModifier(player.getUUID(), movementSpeed);
            return;
        }

        AppliedMovement desiredState = new AppliedMovement(spell.getSpellId(), behavior.movementMode(), desiredMultiplier);
        AppliedMovement currentState = APPLIED.get(player.getUUID());
        if (desiredState.equals(currentState) && movementSpeed.hasModifier(MOVEMENT_MODIFIER_ID)) {
            return;
        }

        // Do not modify Iron's CASTING_MOVESPEED attribute here. Iron's reads
        // that attribute as part of its own casting-input handling, so injecting
        // a negative modifier there can alter the input calculation itself and
        // reverse controls. Apply our override to vanilla movement speed instead.
        // 0.5 therefore means exactly 50% of the player's normal movement speed.
        movementSpeed.removeModifier(MOVEMENT_MODIFIER_ID);
        double amount = desiredMultiplier - 1.0;
        movementSpeed.addTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));

        APPLIED.put(player.getUUID(), desiredState);
    }

    private static void clearModifier(UUID playerId, AttributeInstance movementSpeed) {
        if (APPLIED.remove(playerId) != null || movementSpeed.hasModifier(MOVEMENT_MODIFIER_ID)) {
            movementSpeed.removeModifier(MOVEMENT_MODIFIER_ID);
        }
    }

    private record AppliedMovement(
            String spellId,
            CastTimeOverrides.MovementMode mode,
            double multiplier
    ) {}
}
