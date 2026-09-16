package net.fireboy.mageadditions.config;

/**
 * JSON model for the Counterspell behaviour patch in config/mage_additions.json.
 *
 * This is disabled by default so upgrading Mage Additions does not silently
 * replace Iron's Counterspell unless the pack explicitly opts in.
 */
public final class CounterspellConfig {
    public boolean enabled = false;

    /** Maximum straight-line distance from the caster to a target, in blocks. */
    public double range = 6.0;

    /** Full cone angle in degrees. 90 means 45 degrees either side of the look direction. */
    public double angle_degrees = 90.0;

    /** If true, entities hidden behind blocks are ignored. */
    public boolean require_line_of_sight = true;

    /** "all", "nearest", or "crosshair". */
    public String target_mode = "all";
}
