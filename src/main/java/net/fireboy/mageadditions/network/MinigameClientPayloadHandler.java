package net.fireboy.mageadditions.network;

import net.fireboy.mageadditions.client.screen.HowToPlayScreen;
import net.fireboy.mageadditions.client.screen.MatchControlScreen;
import net.fireboy.mageadditions.client.screen.MinigameAdminScreen;
import net.fireboy.mageadditions.client.screen.TeamSelectionScreen;
import net.fireboy.mageadditions.client.state.ClientMinigameState;
import net.fireboy.mageadditions.minigame.MinigameDefinition;
import net.fireboy.mageadditions.minigame.MinigameRegistry;
import net.fireboy.mageadditions.network.payload.CloseTeamSelectionPayload;
import net.fireboy.mageadditions.network.payload.LobbyStatePayload;
import net.fireboy.mageadditions.network.payload.OpenMatchControlPayload;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuPayload;
import net.fireboy.mageadditions.network.payload.OpenTeamSelectionPayload;
import net.fireboy.mageadditions.network.payload.TeamOutlinePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MinigameClientPayloadHandler {
    private MinigameClientPayloadHandler() {}

    public static void handle(OpenMinigameMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new MinigameAdminScreen()));
    }


    public static void handle(OpenMatchControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new MatchControlScreen(payload)));
    }

    public static void handle(OpenTeamSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MinigameDefinition game = MinigameRegistry.get(payload.gameId());
            if (game != null) {
                Minecraft.getInstance().setScreen(new TeamSelectionScreen(
                        game,
                        payload.teamsEnabled(),
                        payload.teamCount(),
                        payload.canManage(),
                        payload.settings()
                ));
            }
        });
    }

    public static void handle(LobbyStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientMinigameState.setLobbyState(payload);
            if (Minecraft.getInstance().screen instanceof TeamSelectionScreen screen) {
                screen.applyState(payload);
            }
        });
    }

    public static void handle(TeamOutlinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMinigameState.setTeammates(payload.enabled(), payload.teammates()));
    }

    public static void handle(CloseTeamSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientMinigameState.clearLobby();
            if (Minecraft.getInstance().screen instanceof TeamSelectionScreen
                    || Minecraft.getInstance().screen instanceof HowToPlayScreen) {
                Minecraft.getInstance().setScreen(null);
            }
        });
    }
}
