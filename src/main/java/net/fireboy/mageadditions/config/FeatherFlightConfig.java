package net.fireboy.mageadditions.config;

/** JSON model for the Feather Flight behaviour rework in config/mage_additions.json. */
public final class FeatherFlightConfig {
    public boolean enabled = true;

    /** Maximum downward speed while jump is held. Positive blocks/tick. */
    public double slow_fall_speed = 0.115D;

    /** Horizontal acceleration added each airborne tick while movement input is held. */
    public double air_acceleration = 0.018D;

    /** Horizontal speed cap while Feather Flight is active. */
    public double max_horizontal_speed = 0.30D;

    /** Added directly to Minecraft's normal jump strength (normally 0.42). */
    public double extra_jump_strength = 0.16D;

    /** Complete fall-damage immunity while the Aeromancy Flight effect is active. */
    public boolean fall_damage_immunity = true;
}
