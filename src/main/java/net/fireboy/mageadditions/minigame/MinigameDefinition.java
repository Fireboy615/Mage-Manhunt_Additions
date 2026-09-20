package net.fireboy.mageadditions.minigame;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Metadata and default rules used by both the host setup screen and server match runner. */
public record MinigameDefinition(
    ResourceLocation id,
    Component displayName,
    Component description,
    int durationSeconds,
    double initialBorderSize,
    double finalBorderSize,
    boolean randomTeleport,
    boolean blitzStarterKit,
    boolean practice,
    ResourceLocation howToPlayPage,
    List<TeamDefinition> teams
) {
    public MinigameDefinition {
        teams = List.copyOf(teams);
    }

    public TeamDefinition team(ResourceLocation teamId) {
        for (TeamDefinition team : teams) {
            if (team.id().equals(teamId)) {
                return team;
            }
        }
        return null;
    }

    public record TeamDefinition(ResourceLocation id, Component displayName, ChatFormatting color) {}
}
