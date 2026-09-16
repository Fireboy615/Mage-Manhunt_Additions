package net.fireboy.mageadditions.config;

/**
 * JSON model for the Counterspell behaviour patch in config/mage_additions.json.
 */
public final class CounterspellConfig {
    public boolean enabled = false;

    /** "cone" keeps the v0.2 area counterspell; "targeted" uses Iron's target-lock cast flow. */
    public String mode = "cone";

    /** Counterspell cast duration in ticks when mode is "targeted". */
    public int cast_time_ticks = 12;

    /** Maximum target/search range in blocks. Used by both cone and targeted modes. */
    public double range = 6.0;

    /** Aim assist inflation for targeted mode, matching Iron's targeted-spell style. */
    public double aim_assist = 0.35;

    /** Full cone angle in degrees. Used only by cone mode. */
    public double angle_degrees = 90.0;

    /** If true, entities hidden behind blocks are ignored in cone mode. Targeted mode always checks blocks. */
    public boolean require_line_of_sight = true;

    /** "all", "nearest", or "crosshair". Used only by cone mode. */
    public String target_mode = "all";

    /** Temporary visualisation of the cone. Used only by cone mode. */
    public boolean debug_particles = false;
}
