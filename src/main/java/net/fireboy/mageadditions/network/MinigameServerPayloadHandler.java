package net.fireboy.mageadditions.network;

import net.fireboy.mageadditions.minigame.EquipmentPresetStore;
import net.fireboy.mageadditions.minigame.MinigameManager;
import net.fireboy.mageadditions.network.payload.AdminAssignTeamPayload;
import net.fireboy.mageadditions.network.payload.CancelMinigamePayload;
import net.fireboy.mageadditions.network.payload.DeleteEquipmentPresetPayload;
import net.fireboy.mageadditions.network.payload.EquipmentPresetListPayload;
import net.fireboy.mageadditions.network.payload.RandomizeTeamsPayload;
import net.fireboy.mageadditions.network.payload.RequestEquipmentPresetsPayload;
import net.fireboy.mageadditions.network.payload.RequestMatchControlRefreshPayload;
import net.fireboy.mageadditions.network.payload.SaveEquipmentPresetPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;
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
            MinigameManager.cancelCurrentSession(player);
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

    public static void handle(AdminAssignTeamPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.op_only").withStyle(ChatFormatting.RED));
                return;
            }
            MinigameManager.adminAssignTeam(player, payload.playerId(), payload.teamId());
        });
    }

    public static void handle(RandomizeTeamsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) return;
            MinigameManager.randomizeTeams(player);
        });
    }

    public static void handle(RequestMatchControlRefreshPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (player.hasPermissions(2)) MinigameManager.refreshMatchControl(player);
        });
    }

    public static void handle(RequestEquipmentPresetsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2) || player.getServer() == null) return;
            PacketDistributor.sendToPlayer(player, new EquipmentPresetListPayload(EquipmentPresetStore.names(player.getServer())));
        });
    }

    public static void handle(SaveEquipmentPresetPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2) || player.getServer() == null) return;
            String saved = EquipmentPresetStore.saveCurrent(player, payload.name());
            if (!saved.isBlank()) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.loadout.saved", saved).withStyle(ChatFormatting.GREEN));
            }
            PacketDistributor.sendToPlayer(player, new EquipmentPresetListPayload(EquipmentPresetStore.names(player.getServer())));
        });
    }

    public static void handle(DeleteEquipmentPresetPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2) || player.getServer() == null) return;
            if (EquipmentPresetStore.delete(player.getServer(), payload.name())) {
                player.sendSystemMessage(Component.translatable("message.mageadditions.loadout.deleted", payload.name()).withStyle(ChatFormatting.YELLOW));
            }
            PacketDistributor.sendToPlayer(player, new EquipmentPresetListPayload(EquipmentPresetStore.names(player.getServer())));
        });
    }
}
