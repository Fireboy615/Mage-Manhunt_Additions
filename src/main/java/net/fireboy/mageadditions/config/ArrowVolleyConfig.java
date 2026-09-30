package net.fireboy.mageadditions.config;

/** JSON model for the Arrow Volley behaviour rework in config/mage_additions.json. */
public final class ArrowVolleyConfig {
    public boolean enabled = true;
    public double cone_angle_degrees = 40.0D;
    public double projectile_speed = 1.15D;
    public double damage_per_level = 0.25D;

    // Point-blank protection. Limits how much of one volley can be dumped into
    // a single target while keeping the full visual/projectile count.
    public int max_hits_per_target = 6;
    public double close_range_distance = 3.0D;
    public double close_range_damage_multiplier = 0.60D;
    public double full_damage_distance = 7.0D;
}
