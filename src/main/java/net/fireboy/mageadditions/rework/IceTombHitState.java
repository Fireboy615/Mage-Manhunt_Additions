package net.fireboy.mageadditions.rework;

/**
 * State mixed into IceTombEntity. A negative hit count means this is not a
 * player-cast reworked tomb (for example, an evil tomb made by Chilled).
 */
public interface IceTombHitState {
    int mageadditions$getHitsRemaining();
    int mageadditions$getMaxHits();
    void mageadditions$setHitBudget(int hits);
}
