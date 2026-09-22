package net.fireboy.mageadditions.minigame;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Central registry of host-selectable Mage Manhunt rulesets. */
public final class MinigameRegistry {
    public static final ResourceLocation BLITZ_15_ID = id("blitz_15");
    public static final ResourceLocation STANDARD_30_ID = id("standard_30");
    public static final ResourceLocation STANDARD_60_ID = id("standard_60");
    public static final ResourceLocation PRACTICE_ARENA_ID = id("practice_arena");
    public static final ResourceLocation FFA_TEAM_ID = id("ffa");
    public static final ResourceLocation UNASSIGNED_TEAM_ID = id("unassigned");

    private static final List<MinigameDefinition.TeamDefinition> TEAM_COLOURS = List.of(
        team("red", "team.mageadditions.red", ChatFormatting.RED),
        team("blue", "team.mageadditions.blue", ChatFormatting.BLUE),
        team("green", "team.mageadditions.green", ChatFormatting.GREEN),
        team("black", "team.mageadditions.black", ChatFormatting.DARK_GRAY),
        team("yellow", "team.mageadditions.yellow", ChatFormatting.YELLOW),
        team("white", "team.mageadditions.white", ChatFormatting.WHITE),
        team("gold", "team.mageadditions.gold", ChatFormatting.GOLD),
        team("gray", "team.mageadditions.gray", ChatFormatting.GRAY)
    );

    private static final Map<ResourceLocation, MinigameDefinition> GAMES = new LinkedHashMap<>();

    static {
        register(mode(BLITZ_15_ID, "minigame.mageadditions.blitz_15", "minigame.mageadditions.blitz_15.description", 15 * 60, 3000, 33, true, true, false));
        register(mode(STANDARD_30_ID, "minigame.mageadditions.standard_30", "minigame.mageadditions.standard_30.description", 30 * 60, 2000, 33, false, false, false));
        register(mode(STANDARD_60_ID, "minigame.mageadditions.standard_60", "minigame.mageadditions.standard_60.description", 60 * 60, 4000, 33, false, false, false));
        register(mode(PRACTICE_ARENA_ID, "minigame.mageadditions.practice_arena", "minigame.mageadditions.practice_arena.description", 0, 151, 151, false, false, true));
    }

    private MinigameRegistry() {}

    public static void bootstrap() {}

    public static MinigameDefinition get(ResourceLocation id) { return GAMES.get(id); }

    public static Collection<MinigameDefinition> all() { return List.copyOf(GAMES.values()); }

    public static List<MinigameDefinition.TeamDefinition> teamColours() { return TEAM_COLOURS; }

    private static MinigameDefinition mode(
        ResourceLocation id, String nameKey, String descriptionKey, int durationSeconds, double initialBorder,
        double finalBorder, boolean randomTeleport, boolean blitzKit, boolean practice
    ) {
        return new MinigameDefinition(
            id,
            Component.translatable(nameKey),
            Component.translatable(descriptionKey),
            durationSeconds,
            initialBorder,
            finalBorder,
            randomTeleport,
            blitzKit,
            practice,
            ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, "how_to_play/" + id.getPath() + ".json"),
            TEAM_COLOURS
        );
    }

    private static MinigameDefinition.TeamDefinition team(String path, String translationKey, ChatFormatting color) {
        return new MinigameDefinition.TeamDefinition(id(path), Component.translatable(translationKey).withStyle(color), color);
    }

    private static void register(MinigameDefinition definition) {
        MinigameDefinition old = GAMES.putIfAbsent(definition.id(), definition);
        if (old != null) throw new IllegalStateException("Duplicate Mage Additions minigame id: " + definition.id());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MageAdditions.MODID, path);
    }
}
