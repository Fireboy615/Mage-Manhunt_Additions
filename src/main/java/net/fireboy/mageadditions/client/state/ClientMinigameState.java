package net.fireboy.mageadditions.client.state;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fireboy.mageadditions.network.payload.LobbyStatePayload;

/** Client-only cache for the live lobby and private teammate outline list. */
public final class ClientMinigameState {
    private static final Set<UUID> TEAMMATES = new HashSet<>();
    private static boolean teammateOutlinesEnabled;
    private static LobbyStatePayload lobbyState;
    private static List<String> equipmentPresets = List.of();

    private ClientMinigameState() {}

    public static void setLobbyState(LobbyStatePayload state) {
        lobbyState = state;
    }

    public static LobbyStatePayload lobbyState() {
        return lobbyState;
    }

    public static void setEquipmentPresets(List<String> presets) {
        equipmentPresets = List.copyOf(presets);
    }

    public static List<String> equipmentPresets() {
        return equipmentPresets;
    }

    public static void setTeammates(boolean enabled, Iterable<UUID> teammates) {
        TEAMMATES.clear();
        for (UUID teammate : teammates) {
            TEAMMATES.add(teammate);
        }
        teammateOutlinesEnabled = enabled;
    }

    public static boolean shouldHighlight(UUID entityId) {
        return teammateOutlinesEnabled && TEAMMATES.contains(entityId);
    }

    public static void clearLobby() {
        lobbyState = null;
    }

    public static void clearAll() {
        lobbyState = null;
        teammateOutlinesEnabled = false;
        TEAMMATES.clear();
        equipmentPresets = List.of();
    }
}
