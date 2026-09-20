package net.fireboy.mageadditions.network;

import net.fireboy.mageadditions.network.payload.CancelMinigamePayload;
import net.fireboy.mageadditions.network.payload.CloseTeamSelectionPayload;
import net.fireboy.mageadditions.network.payload.RevivePlayerPayload;
import net.fireboy.mageadditions.network.payload.PauseMinigamePayload;
import net.fireboy.mageadditions.network.payload.OpenMatchControlPayload;
import net.fireboy.mageadditions.network.payload.ContinueMinigamePayload;
import net.fireboy.mageadditions.network.payload.LaunchMinigamePayload;
import net.fireboy.mageadditions.network.payload.LobbyStatePayload;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuPayload;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuRequestPayload;
import net.fireboy.mageadditions.network.payload.OpenTeamSelectionPayload;
import net.fireboy.mageadditions.network.payload.SelectTeamPayload;
import net.fireboy.mageadditions.network.payload.StartMinigamePayload;
import net.fireboy.mageadditions.network.payload.TeamOutlinePayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MinigameNetwork {
    private MinigameNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");

        registrar.playToServer(
                OpenMinigameMenuRequestPayload.TYPE,
                OpenMinigameMenuRequestPayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );
        registrar.playToServer(
                StartMinigamePayload.TYPE,
                StartMinigamePayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );
        registrar.playToServer(
                SelectTeamPayload.TYPE,
                SelectTeamPayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );
        registrar.playToServer(
                LaunchMinigamePayload.TYPE,
                LaunchMinigamePayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );
        registrar.playToServer(
                CancelMinigamePayload.TYPE,
                CancelMinigamePayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );

        registrar.playToServer(
                PauseMinigamePayload.TYPE,
                PauseMinigamePayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );
        registrar.playToServer(
                ContinueMinigamePayload.TYPE,
                ContinueMinigamePayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );
        registrar.playToServer(
                RevivePlayerPayload.TYPE,
                RevivePlayerPayload.STREAM_CODEC,
                MinigameServerPayloadHandler::handle
        );

        registrar.playToClient(
                OpenMinigameMenuPayload.TYPE,
                OpenMinigameMenuPayload.STREAM_CODEC,
                MinigameClientPayloadHandler::handle
        );
        registrar.playToClient(
                OpenMatchControlPayload.TYPE,
                OpenMatchControlPayload.STREAM_CODEC,
                MinigameClientPayloadHandler::handle
        );
        registrar.playToClient(
                OpenTeamSelectionPayload.TYPE,
                OpenTeamSelectionPayload.STREAM_CODEC,
                MinigameClientPayloadHandler::handle
        );
        registrar.playToClient(
                LobbyStatePayload.TYPE,
                LobbyStatePayload.STREAM_CODEC,
                MinigameClientPayloadHandler::handle
        );
        registrar.playToClient(
                CloseTeamSelectionPayload.TYPE,
                CloseTeamSelectionPayload.STREAM_CODEC,
                MinigameClientPayloadHandler::handle
        );
        registrar.playToClient(
                TeamOutlinePayload.TYPE,
                TeamOutlinePayload.STREAM_CODEC,
                MinigameClientPayloadHandler::handle
        );
    }
}
