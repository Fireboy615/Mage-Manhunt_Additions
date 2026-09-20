package net.fireboy.mageadditions.network;

import net.fireboy.mageadditions.minigame.MinigameManager;
import net.fireboy.mageadditions.network.payload.CancelMinigamePayload;
import net.fireboy.mageadditions.network.payload.ContinueMinigamePayload;
import net.fireboy.mageadditions.network.payload.LaunchMinigamePayload;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuRequestPayload;
import net.fireboy.mageadditions.network.payload.PauseMinigamePayload;
import net.fireboy.mageadditions.network.payload.RevivePlayerPayload;
import net.fireboy.mageadditions.network.payload.SelectTeamPayload;
import net.fireboy.mageadditions.network.payload.StartMinigamePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MinigameServerPayloadHandler {
    private MinigameServerPayloadHandler() {}

    public static void handle(OpenMinigameMenuRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.openAdminMenu(player);
        });
    }

    public static void handle(StartMinigamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.beginSetup(player, payload.gameId(), payload.teamsEnabled(), payload.teamCount(), payload.settings());
        });
    }

    public static void handle(SelectTeamPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> MinigameManager.selectTeam(player, payload.gameId(), payload.teamId()));
        }
    }

    public static void handle(CancelMinigamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.cancelLobby(player);
        });
    }

    public static void handle(LaunchMinigamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.launchMatch(player);
        });
    }

    public static void handle(PauseMinigamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.pauseMatch(player);
        });
    }

    public static void handle(ContinueMinigamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.continueMatch(player);
        });
    }

    public static void handle(RevivePlayerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.revivePlayer(player, payload.playerId());
        });
    }
}
