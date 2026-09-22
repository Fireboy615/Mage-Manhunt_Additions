package net.fireboy.mageadditions.client;

import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * Keeps Mage Additions' casting movement overrides from changing the camera FOV.
 *
 * <p>The slowdown itself still uses the vanilla movement-speed attribute so it is
 * server authoritative and does not invert controls. Minecraft also uses that
 * attribute when calculating the player's FOV, though, which creates an unwanted
 * zoom while casting. This listener removes only our modifier from the FOV math
 * while preserving every other movement/FOV modifier.</p>
 */
public final class ClientFovEvents {
    private static final ResourceLocation CAST_MOVEMENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            MageAdditions.MODID,
            "spell_cast_movement"
    );

    private ClientFovEvents() {}

    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null || !movementSpeed.hasModifier(CAST_MOVEMENT_MODIFIER_ID)) {
            return;
        }

        float walkingSpeed = player.getAbilities().getWalkingSpeed();
        if (walkingSpeed == 0.0F) {
            return;
        }

        double currentMovementSpeed = movementSpeed.getValue();
        double movementSpeedWithoutMageOverride = valueWithoutMageMovementOverride(movementSpeed);

        double currentSpeedFovFactor = (currentMovementSpeed / walkingSpeed + 1.0D) / 2.0D;
        double cleanSpeedFovFactor = (movementSpeedWithoutMageOverride / walkingSpeed + 1.0D) / 2.0D;

        if (!Double.isFinite(currentSpeedFovFactor)
                || !Double.isFinite(cleanSpeedFovFactor)
                || Math.abs(currentSpeedFovFactor) < 1.0E-7D) {
            return;
        }

        // The rest of vanilla's FOV effects (flying, bow draw, etc.) are
        // multiplicative, so correcting just this ratio preserves them.
        float corrected = (float) (event.getNewFovModifier() * (cleanSpeedFovFactor / currentSpeedFovFactor));
        if (Float.isFinite(corrected)) {
            event.setNewFovModifier(corrected);
        }
    }

    private static double valueWithoutMageMovementOverride(AttributeInstance instance) {
        double baseValue = instance.getBaseValue();
        double afterAdditions = baseValue;

        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.is(CAST_MOVEMENT_MODIFIER_ID)
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                afterAdditions += modifier.amount();
            }
        }

        double afterBaseMultipliers = afterAdditions;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.is(CAST_MOVEMENT_MODIFIER_ID)
                    && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                afterBaseMultipliers += afterAdditions * modifier.amount();
            }
        }

        double total = afterBaseMultipliers;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.is(CAST_MOVEMENT_MODIFIER_ID)
                    && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                total *= 1.0D + modifier.amount();
            }
        }

        return instance.getAttribute().value().sanitizeValue(total);
    }
}
