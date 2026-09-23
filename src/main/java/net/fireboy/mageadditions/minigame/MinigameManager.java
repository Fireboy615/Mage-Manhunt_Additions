package net.fireboy.mageadditions.minigame;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.fireboy.mageadditions.network.payload.CloseTeamSelectionPayload;
import net.fireboy.mageadditions.network.payload.LobbyStatePayload;
import net.fireboy.mageadditions.network.payload.OpenMatchControlPayload;
import net.fireboy.mageadditions.network.payload.OpenMinigameMenuPayload;
import net.fireboy.mageadditions.network.payload.OpenTeamSelectionPayload;
import net.fireboy.mageadditions.network.payload.TeamOutlinePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative minigame lobby and match state. */
public final class MinigameManager {
    public enum Phase { IDLE, LOBBY, RUNNING, PAUSED }

    private static final Map<UUID, ResourceLocation> TEAM_SELECTIONS = new HashMap<>();
    private static final Set<UUID> MATCH_PARTICIPANTS = new HashSet<>();
    private static final Set<UUID> DEAD_PARTICIPANTS = new HashSet<>();
    /** Players temporarily promoted to OP by practice mode. Persisted so cancel can safely undo it after a restart. */
    private static final Set<UUID> PRACTICE_PROMOTED_OPS = new HashSet<>();
    private static final Map<UUID, FreezePoint> FREEZE_POINTS = new HashMap<>();
    private static final String[] RECIPE_IDS = {
            "crafttweaker:scroll_forge_change",
            "crafttweaker:copper_book_change",
            "crafttweaker:iron_book_change",
            "crafttweaker:gold_book_change",
            "crafttweaker:fireward_ring_change",
            "crafttweaker:frostward_ring_change",
            "crafttweaker:poisonward_ring_change",
            "crafttweaker:cast_time_ring_change",
            "crafttweaker:cooldown_ring_change",
            "crafttweaker:mana_ring_change",
            "crafttweaker:silver_ring_change",
            "crafttweaker:inscription_table_change",
            "crafttweaker:heavy_chain_necklace_change",
            "crafttweaker:concentration_amulet_change",
            "crafttweaker:conjurers_talisman_change",
            "crafttweaker:amethyst_resonance_charm_change"
    };

    private static Phase phase = Phase.IDLE;
    private static ResourceLocation activeGame;
    private static boolean teamsEnabled;
    private static int teamCount;
    private static MinigameSettings activeSettings;
    private static int matchTicksRemaining;
    private static int tickCounter;
    private static int persistenceTickCounter;
    private static UUID hostId;
    private static boolean frozeGameTicks;

    private MinigameManager() {}

    public static void beginSetup(
            ServerPlayer operator,
            ResourceLocation gameId,
            boolean useTeams,
            int requestedTeamCount,
            MinigameSettings requestedSettings
    ) {
        MinigameDefinition game = MinigameRegistry.get(gameId);
        if (game == null) {
            operator.sendSystemMessage(Component.translatable("message.mageadditions.minigame.unknown").withStyle(ChatFormatting.RED));
            return;
        }

        MinecraftServer server = operator.getServer();
        if (server == null) {
            return;
        }

        int validatedTeams = useTeams ? Math.max(2, Math.min(requestedTeamCount, game.teams().size())) : 0;
        activeGame = gameId;
        teamsEnabled = useTeams;
        teamCount = validatedTeams;
        activeSettings = (requestedSettings == null ? MinigameSettings.defaults(game) : requestedSettings).validated();
        hostId = operator.getUUID();
        phase = Phase.LOBBY;
        matchTicksRemaining = 0;
        tickCounter = 0;
        persistenceTickCounter = 0;
        frozeGameTicks = false;
        TEAM_SELECTIONS.clear();
        MATCH_PARTICIPANTS.clear();
        DEAD_PARTICIPANTS.clear();
        PRACTICE_PROMOTED_OPS.clear();
        FREEZE_POINTS.clear();

        setPvp(server, false);
        prepareScoreboardTeams(server, game);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            putPlayerInPregame(player);
            if (!teamsEnabled) {
                TEAM_SELECTIONS.put(player.getUUID(), MinigameRegistry.FFA_TEAM_ID);
                removeFromAnyTeam(player);
            }
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "message.mageadditions.minigame.setup_started",
                        operator.getDisplayName(),
                        game.displayName(),
                        teamsEnabled ? Component.translatable("message.mageadditions.minigame.team_format", teamCount)
                                : Component.translatable("message.mageadditions.minigame.ffa_format")
                ).withStyle(ChatFormatting.GOLD),
                false
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            promptForTeam(player);
        }
        broadcastLobbyState(server);
        saveSession(server);
    }

    public static void promptForTeam(ServerPlayer player) {
        MinigameDefinition game = activeDefinition();
        if (phase != Phase.LOBBY || game == null || activeSettings == null) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new OpenTeamSelectionPayload(game.id(), teamsEnabled, teamCount, player.hasPermissions(2), activeSettings)
        );
    }

    public static void selectTeam(ServerPlayer player, ResourceLocation gameId, ResourceLocation teamId) {
        if (phase != Phase.LOBBY || activeGame == null || !activeGame.equals(gameId)) {
            player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.no_active_setup").withStyle(ChatFormatting.RED));
            return;
        }
        if (!teamsEnabled) {
            return;
        }

        MinigameDefinition game = activeDefinition();
        if (game == null || !isActiveTeam(game, teamId)) {
            player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.invalid_team").withStyle(ChatFormatting.RED));
            return;
        }

        TEAM_SELECTIONS.put(player.getUUID(), teamId);
        assignScoreboardTeam(player, game.team(teamId));
        MinecraftServer server = player.getServer();
        if (server != null) {
            broadcastLobbyState(server);
            saveSession(server);
        }
    }

    public static void cancelCurrentSession(ServerPlayer operator) {
        MinecraftServer server = operator.getServer();
        if (server == null || phase == Phase.IDLE) {
            return;
        }

        boolean wasLobby = phase == Phase.LOBBY;
        unfreezeGameTicks(server);
        setPvp(server, false);
        if (!wasLobby) {
            restoreDefaultWorldBorder(server);
        }
        revokePracticeOps(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, CloseTeamSelectionPayload.INSTANCE);
            PacketDistributor.sendToPlayer(player, new TeamOutlinePayload(false, List.of()));
            putPlayerInPregame(player);
        }

        reset();
        MinigameSessionStore.delete(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        wasLobby ? "message.mageadditions.minigame.lobby_cancelled" : "message.mageadditions.minigame.match_cancelled",
                        operator.getDisplayName()
                ).withStyle(ChatFormatting.YELLOW),
                false
        );
    }

    /** Backward-compatible entry point used by older callers. */
    public static void cancelLobby(ServerPlayer operator) {
        cancelCurrentSession(operator);
    }

    public static void launchMatch(ServerPlayer operator) {
        MinecraftServer server = operator.getServer();
        MinigameDefinition game = activeDefinition();
        if (server == null || phase != Phase.LOBBY || game == null || activeSettings == null) {
            operator.sendSystemMessage(Component.translatable("message.mageadditions.minigame.no_active_setup").withStyle(ChatFormatting.RED));
            return;
        }
        if (!allOnlinePlayersSelected(server)) {
            operator.sendSystemMessage(Component.translatable("message.mageadditions.minigame.not_everyone_ready").withStyle(ChatFormatting.RED));
            return;
        }

        phase = Phase.RUNNING;
        MATCH_PARTICIPANTS.clear();
        MATCH_PARTICIPANTS.addAll(TEAM_SELECTIONS.keySet());
        DEAD_PARTICIPANTS.clear();
        FREEZE_POINTS.clear();
        matchTicksRemaining = Math.max(0, activeSettings.durationSeconds() * 20);
        tickCounter = 0;
        persistenceTickCounter = 0;
        setPvp(server, true);

        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0.0, 0.0);
        // Do not allow border damage during spawn placement. Players are moved first, then damage is enabled.
        border.setDamageSafeZone(0.0);
        border.setDamagePerBlock(0.0);
        border.setSize(activeSettings.initialBorderSize());
        if (activeSettings.durationSeconds() > 0 && activeSettings.initialBorderSize() != activeSettings.finalBorderSize()) {
            border.lerpSizeBetween(
                    activeSettings.initialBorderSize(),
                    activeSettings.finalBorderSize(),
                    activeSettings.durationSeconds() * 1000L
            );
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, CloseTeamSelectionPayload.INSTANCE);
            if (game.practice()) {
                startPracticePlayer(player, game, level, activeSettings);
            } else {
                startSurvivalPlayer(player, game, level, activeSettings);
            }
        }
        border.setDamagePerBlock(0.1);
        syncTeamOutlines(server);

        if (!game.practice()) {
            grantCraftingRecipes(server);
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.mageadditions.minigame.match_started", game.displayName()).withStyle(ChatFormatting.GREEN),
                false
        );
        saveSession(server);
    }

    private static void startSurvivalPlayer(ServerPlayer player, MinigameDefinition game, ServerLevel level, MinigameSettings settings) {
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        refillPlayer(player);

        if (settings.randomTeleport()) {
            randomTeleport(player, level);
        }
        applyStarterKit(player, game, settings);
    }

    private static void startPracticePlayer(ServerPlayer player, MinigameDefinition game, ServerLevel level, MinigameSettings settings) {
        grantPracticeOp(player);
        player.setGameMode(GameType.CREATIVE);
        refillPlayer(player);
        // Practice always starts with everybody safely randomized inside the active arena border.
        if (!randomTeleport(player, level)) {
            ensureInsideBorder(player, level);
        }
        applyStarterKit(player, game, settings);
    }

    private static void applyStarterKit(ServerPlayer player, MinigameDefinition game, MinigameSettings settings) {
        MinigameSettings.KitPreset kit = settings.kitPreset();
        if (kit == MinigameSettings.KitPreset.MODE_DEFAULT) {
            if (game.practice()) {
                kit = MinigameSettings.KitPreset.PRACTICE;
            } else if (game.blitzStarterKit()) {
                kit = MinigameSettings.KitPreset.BLITZ;
            } else {
                kit = MinigameSettings.KitPreset.BUNDLE_ONLY;
            }
        }

        switch (kit) {
            case BLITZ -> giveBlitzStarterKit(player);
            case BUNDLE_ONLY -> giveItem(player, "minecraft:bundle", 1);
            case PRACTICE -> givePracticeStarterKit(player);
            case NONE -> { }
            case MODE_DEFAULT -> { }
        }
    }

    private static void givePracticeStarterKit(ServerPlayer player) {
        setEquipment(player, EquipmentSlot.HEAD, "irons_spellbooks:wizard_helmet");
        setEquipment(player, EquipmentSlot.CHEST, "irons_spellbooks:wizard_chestplate");
        setEquipment(player, EquipmentSlot.LEGS, "irons_spellbooks:wizard_leggings");
        setEquipment(player, EquipmentSlot.FEET, "irons_spellbooks:wizard_boots");
        setEquipment(player, EquipmentSlot.OFFHAND, "minecraft:shield");
        setHotbar(player, 0, "minecraft:diamond_axe", 1);
        setHotbar(player, 1, "minecraft:diamond_sword", 1);
        setHotbar(player, 2, "minecraft:crossbow", 1);
        setHotbar(player, 3, "minecraft:arrow", 64);
        setHotbar(player, 6, "minecraft:cooked_beef", 64);
        setHotbar(player, 8, "minecraft:water_bucket", 1);
        runAsPlayer(player, "curios replace spellbook 0 @s with irons_spellbooks:gold_spell_book 1");
        giveItem(player, "irons_spellbooks:inscription_table", 1);
        giveItem(player, "irons_spellbooks:fireward_ring", 1);
        giveItem(player, "irons_spellbooks:frostward_ring", 1);
        giveItem(player, "irons_spellbooks:poisonward_ring", 1);
    }

    private static void giveBlitzStarterKit(ServerPlayer player) {
        setEquipment(player, EquipmentSlot.HEAD, "irons_spellbooks:wizard_helmet");
        setEquipment(player, EquipmentSlot.CHEST, "irons_spellbooks:wizard_chestplate");
        setEquipment(player, EquipmentSlot.LEGS, "irons_spellbooks:wizard_leggings");
        setEquipment(player, EquipmentSlot.FEET, "irons_spellbooks:wizard_boots");
        setEquipment(player, EquipmentSlot.OFFHAND, "minecraft:shield");
        setHotbar(player, 0, "minecraft:iron_axe", 1);
        setHotbar(player, 3, "minecraft:iron_pickaxe", 1);
        setHotbar(player, 6, "minecraft:cooked_beef", 24);
        setHotbar(player, 8, "minecraft:water_bucket", 1);
        runAsPlayer(player, "curios replace spellbook 0 @s with irons_spellbooks:gold_spell_book 1");
        giveItem(player, "irons_spellbooks:scroll_forge", 1);
        giveItem(player, "irons_spellbooks:inscription_table", 1);
    }

    private static boolean randomTeleport(ServerPlayer player, ServerLevel level) {
        RandomSource random = level.getRandom();
        WorldBorder border = level.getWorldBorder();
        double margin = Math.min(24.0, Math.max(3.0, border.getSize() / 8.0));
        double half = Math.max(1.0, border.getSize() / 2.0 - margin);
        int minX = (int) Math.ceil(border.getCenterX() - half);
        int maxX = (int) Math.floor(border.getCenterX() + half);
        int minZ = (int) Math.ceil(border.getCenterZ() - half);
        int maxZ = (int) Math.floor(border.getCenterZ() + half);

        if (minX > maxX || minZ > maxZ) {
            return false;
        }

        for (int attempt = 0; attempt < 120; attempt++) {
            int x = random.nextIntBetweenInclusive(minX, maxX);
            int z = random.nextIntBetweenInclusive(minZ, maxZ);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) {
                continue;
            }

            BlockPos feet = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(feet.below());
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
                continue;
            }
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty() || below.isAir()) {
                continue;
            }

            player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.resetFallDistance();
            return true;
        }

        MageAdditions.LOGGER.warn("Could not find a safe random minigame spawn for {}", player.getGameProfile().getName());
        return false;
    }

    public static void onServerStarted(MinecraftServer server) {
        reset();
        MinigameSessionStore.load(server).ifPresent(snapshot -> {
            MinigameDefinition game = MinigameRegistry.get(snapshot.gameId());
            if (game == null) {
                MageAdditions.LOGGER.warn("Saved minigame session references unknown game {}; ignoring it", snapshot.gameId());
                MinigameSessionStore.delete(server);
                return;
            }

            activeGame = snapshot.gameId();
            hostId = snapshot.hostId();
            teamsEnabled = snapshot.teamsEnabled();
            teamCount = Math.max(0, Math.min(snapshot.teamCount(), game.teams().size()));
            activeSettings = snapshot.settings().validated();
            matchTicksRemaining = snapshot.matchTicksRemaining();
            TEAM_SELECTIONS.putAll(snapshot.teamSelections());
            MATCH_PARTICIPANTS.addAll(snapshot.participants());
            DEAD_PARTICIPANTS.addAll(snapshot.deadParticipants());
            PRACTICE_PROMOTED_OPS.addAll(snapshot.practicePromotedOps());
            phase = snapshot.phase();

            prepareScoreboardTeams(server, game);
            restoreScoreboardAssignments(server, game);

            if (phase == Phase.RUNNING || phase == Phase.PAUSED) {
                server.overworld().getWorldBorder().setSize(snapshot.currentBorderSize());
            }

            if (phase == Phase.RUNNING) {
                // Never let a recovered server resume the shrinking border before people reconnect.
                pauseMatchInternal(server, null, true, false);
            } else if (phase == Phase.PAUSED) {
                stopBorder(server);
                freezeGameTicks(server);
                setPvp(server, false);
                saveSession(server);
            } else if (phase == Phase.LOBBY) {
                setPvp(server, false);
            }

            MageAdditions.LOGGER.info("Recovered {} minigame session {} with {} participant(s)", phase, activeGame, MATCH_PARTICIPANTS.size());
        });
    }

    public static void onServerStopping(MinecraftServer server) {
        saveSession(server);
    }

    public static void onServerTick(MinecraftServer server) {
        if (phase != Phase.RUNNING) {
            keepProtectedPlayersFed(server);
        }

        if (phase == Phase.PAUSED) {
            freezeGameTicks(server);
            freezeOnlinePlayers(server);
            tickCounter++;
            persistenceTickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                showPausedActionbar(server);
            }
            if (persistenceTickCounter >= 20) {
                persistenceTickCounter = 0;
                saveSession(server);
            }
            return;
        }

        if (phase != Phase.RUNNING) {
            return;
        }
        if (matchTicksRemaining > 0) {
            matchTicksRemaining--;
        }
        tickCounter++;
        persistenceTickCounter++;
        if (persistenceTickCounter >= 20) {
            persistenceTickCounter = 0;
            saveSession(server);
        }
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        MinigameDefinition game = activeDefinition();
        if (game == null || game.practice() || activeSettings == null) {
            return;
        }

        WorldBorder border = server.overworld().getWorldBorder();
        int seconds = Math.max(0, matchTicksRemaining / 20);
        String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int distance = Math.max(0, (int) Math.floor(border.getDistanceToBorder(player)));
            player.displayClientMessage(
                    Component.literal("Border: ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(Integer.toString(distance)).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("  |  Time: ").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(time).withStyle(ChatFormatting.YELLOW)),
                    true
            );
        }
    }

    /**
     * Re-sends the current live match-control state to an operator whose control
     * screen is already open. This is intentionally separate from openAdminMenu()
     * so a refresh request never changes screens or re-enters lobby setup.
     */
    public static void refreshMatchControl(ServerPlayer operator) {
        if (operator == null || !operator.hasPermissions(2)) {
            return;
        }
        sendMatchControlState(operator);
    }

    public static void openAdminMenu(ServerPlayer operator) {
        MinecraftServer server = operator.getServer();
        if (server == null) {
            return;
        }
        if (phase == Phase.RUNNING || phase == Phase.PAUSED) {
            sendMatchControlState(operator);
        } else if (phase == Phase.LOBBY) {
            promptForTeam(operator);
            broadcastLobbyState(server);
        } else {
            PacketDistributor.sendToPlayer(operator, OpenMinigameMenuPayload.INSTANCE);
        }
    }

    public static void pauseMatch(ServerPlayer operator) {
        MinecraftServer server = operator.getServer();
        if (server == null || phase != Phase.RUNNING) {
            sendMatchControlState(operator);
            return;
        }
        pauseMatchInternal(server, operator, false, false);
        sendMatchControlState(operator);
    }

    public static void continueMatch(ServerPlayer operator) {
        MinecraftServer server = operator.getServer();
        MinigameDefinition game = activeDefinition();
        if (server == null || phase != Phase.PAUSED || game == null || activeSettings == null) {
            sendMatchControlState(operator);
            return;
        }

        phase = Phase.RUNNING;
        FREEZE_POINTS.clear();
        unfreezeGameTicks(server);
        setPvp(server, true);

        WorldBorder border = server.overworld().getWorldBorder();
        double currentSize = border.getSize();
        if (matchTicksRemaining > 0 && currentSize != activeSettings.finalBorderSize()) {
            border.lerpSizeBetween(currentSize, activeSettings.finalBorderSize(), matchTicksRemaining * 50L);
        } else if (matchTicksRemaining <= 0) {
            border.setSize(activeSettings.finalBorderSize());
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MATCH_PARTICIPANTS.contains(player.getUUID()) && !DEAD_PARTICIPANTS.contains(player.getUUID())) {
                ensureInsideBorder(player, server.overworld());
            }
            restorePlayerForRunningMatch(player, game);
        }
        syncTeamOutlines(server);
        saveSession(server);

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.mageadditions.minigame.match_continued", operator.getDisplayName()).withStyle(ChatFormatting.GREEN),
                false
        );
        sendMatchControlState(operator);
    }

    public static void revivePlayer(ServerPlayer operator, UUID targetId) {
        MinecraftServer server = operator.getServer();
        MinigameDefinition game = activeDefinition();
        if (server == null || game == null || (phase != Phase.RUNNING && phase != Phase.PAUSED)) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null || !MATCH_PARTICIPANTS.contains(targetId) || !DEAD_PARTICIPANTS.remove(targetId)) {
            operator.sendSystemMessage(Component.translatable("message.mageadditions.minigame.revive_unavailable").withStyle(ChatFormatting.RED));
            sendMatchControlState(operator);
            return;
        }

        target.setHealth(target.getMaxHealth());
        target.getFoodData().setFoodLevel(20);
        target.getFoodData().setSaturation(5.0F);
        target.resetFallDistance();

        if (phase == Phase.PAUSED) {
            target.setGameMode(GameType.SPECTATOR);
            freezePlayer(target);
        } else {
            ensureInsideBorder(target, server.overworld());
            target.setGameMode(game.practice() ? GameType.CREATIVE : GameType.SURVIVAL);
        }
        saveSession(server);
        syncTeamOutlines(server);

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.mageadditions.minigame.player_revived", target.getDisplayName(), operator.getDisplayName())
                        .withStyle(ChatFormatting.AQUA),
                false
        );
        sendMatchControlState(operator);
    }

    /**
     * Lets an operator move a participant to another active team from the live
     * match-control screen. The server remains authoritative: the requested
     * team is validated against the current minigame/team-count before any
     * scoreboard or saved-session state is changed.
     */
    public static void adminAssignTeam(ServerPlayer operator, UUID targetId, ResourceLocation teamId) {
        MinecraftServer server = operator.getServer();
        MinigameDefinition game = activeDefinition();
        if (server == null || game == null || phase == Phase.IDLE) {
            return;
        }

        if (!teamsEnabled || !isActiveTeam(game, teamId)) {
            operator.sendSystemMessage(
                    Component.translatable("message.mageadditions.minigame.invalid_team").withStyle(ChatFormatting.RED)
            );
            sendMatchControlState(operator);
            return;
        }

        // During a running/paused match only actual match participants may be
        // reassigned. In the lobby an online player may be assigned before launch.
        if (phase != Phase.LOBBY && !MATCH_PARTICIPANTS.contains(targetId)) {
            sendMatchControlState(operator);
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            // Keep the operation deterministic: the control screen currently
            // exposes online players, so do not silently mutate an offline entry.
            sendMatchControlState(operator);
            return;
        }

        TEAM_SELECTIONS.put(targetId, teamId);
        MinigameDefinition.TeamDefinition team = game.team(teamId);
        if (team != null) {
            assignScoreboardTeam(target, team);
        }

        syncTeamOutlines(server);
        saveSession(server);

        if (phase == Phase.LOBBY) {
            broadcastLobbyState(server);
        } else {
            // Refresh the operator's already-open match-control screen so the
            // team selector immediately reflects the authoritative server state.
            sendMatchControlState(operator);
        }
    }

    /**
     * Randomly redistributes players across the currently active teams.
     * Assignment is balanced (team sizes differ by at most one) while the
     * shuffled player order keeps the result random. In a live match we use
     * the full participant set, including temporarily offline participants, so
     * reconnecting players keep the randomized team chosen by the host.
     */
    public static void randomizeTeams(ServerPlayer operator) {
        MinecraftServer server = operator.getServer();
        MinigameDefinition game = activeDefinition();
        if (server == null || game == null || phase == Phase.IDLE) {
            return;
        }

        if (!teamsEnabled || teamCount < 2 || game.teams().isEmpty()) {
            operator.sendSystemMessage(
                    Component.translatable("message.mageadditions.minigame.invalid_team").withStyle(ChatFormatting.RED)
            );
            if (phase != Phase.LOBBY) {
                sendMatchControlState(operator);
            }
            return;
        }

        int activeTeamCount = Math.min(teamCount, game.teams().size());
        List<UUID> players = new ArrayList<>();
        if (phase == Phase.LOBBY) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                players.add(player.getUUID());
            }
        } else {
            players.addAll(MATCH_PARTICIPANTS);
        }

        if (players.isEmpty()) {
            if (phase != Phase.LOBBY) {
                sendMatchControlState(operator);
            }
            return;
        }

        // Fisher-Yates using Minecraft's own RNG avoids another dependency and
        // produces a random ordering before the balanced round-robin assignment.
        RandomSource random = RandomSource.create();
        for (int i = players.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            UUID swap = players.get(i);
            players.set(i, players.get(j));
            players.set(j, swap);
        }

        for (int i = 0; i < players.size(); i++) {
            UUID playerId = players.get(i);
            MinigameDefinition.TeamDefinition team = game.teams().get(i % activeTeamCount);
            TEAM_SELECTIONS.put(playerId, team.id());

            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                assignScoreboardTeam(online, team);
            }
        }

        syncTeamOutlines(server);
        saveSession(server);

        if (phase == Phase.LOBBY) {
            broadcastLobbyState(server);
        } else {
            sendMatchControlState(operator);
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        if (phase == Phase.RUNNING) {
            restorePlayerScoreboardTeam(player, activeDefinition());
            if (hostId != null && hostId.equals(player.getUUID())) {
                pauseMatchInternal(server, player, false, true);
            } else {
                restorePlayerForRunningMatch(player, activeDefinition());
                syncTeamOutlines(server);
            }
            return;
        }

        if (phase == Phase.PAUSED) {
            restorePlayerScoreboardTeam(player, activeDefinition());
            if (MATCH_PARTICIPANTS.contains(player.getUUID()) && !DEAD_PARTICIPANTS.contains(player.getUUID())) {
                ensureInsideBorder(player, server.overworld());
            }
            player.setGameMode(GameType.SPECTATOR);
            freezePlayer(player);
            syncTeamOutlines(server);
            player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.joined_paused").withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (phase == Phase.LOBBY) {
            putPlayerInPregame(player);
            if (!teamsEnabled) {
                TEAM_SELECTIONS.put(player.getUUID(), MinigameRegistry.FFA_TEAM_ID);
                removeFromAnyTeam(player);
            } else {
                restorePlayerScoreboardTeam(player, activeDefinition());
            }
            promptForTeam(player);
            broadcastLobbyState(server);
            saveSession(server);
            return;
        }

        // IDLE is the protected waiting-room state used between minigames.
        putPlayerInPregame(player);
        PacketDistributor.sendToPlayer(player, new TeamOutlinePayload(false, List.of()));
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        if (phase == Phase.RUNNING && hostId != null && hostId.equals(player.getUUID())) {
            pauseMatchInternal(server, player, false, true);
            FREEZE_POINTS.remove(player.getUUID());
            return;
        }

        FREEZE_POINTS.remove(player.getUUID());
        if (phase == Phase.LOBBY) {
            // Keep their selection so a temporary disconnect does not make them choose again.
            broadcastLobbyState(server);
            saveSession(server);
        } else if (phase == Phase.RUNNING || phase == Phase.PAUSED) {
            saveSession(server);
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        MinigameDefinition game = activeDefinition();
        if ((phase == Phase.RUNNING || phase == Phase.PAUSED) && game != null && game.practice()) {
            grantPracticeOp(player);
            refillPlayer(player);
            MinecraftServer server = player.getServer();
            if (phase == Phase.PAUSED) {
                player.setGameMode(GameType.SPECTATOR);
                freezePlayer(player);
            } else {
                player.setGameMode(GameType.CREATIVE);
                if (server != null && !randomTeleport(player, server.overworld())) {
                    ensureInsideBorder(player, server.overworld());
                }
            }
            return;
        }

        if ((phase == Phase.RUNNING || phase == Phase.PAUSED) && game != null) {
            if (MATCH_PARTICIPANTS.contains(player.getUUID())) {
                DEAD_PARTICIPANTS.add(player.getUUID());
            }
            player.setGameMode(GameType.SPECTATOR);
            MinecraftServer server = player.getServer();
            if (server != null) {
                if (phase == Phase.PAUSED) {
                    freezePlayer(player);
                }
                saveSession(server);
            }
        } else if (phase == Phase.LOBBY) {
            putPlayerInPregame(player);
        }
    }

    public static boolean isPregameProtected() {
        return phase != Phase.RUNNING;
    }

    public static boolean isRunning() { return phase == Phase.RUNNING; }
    public static boolean isPaused() { return phase == Phase.PAUSED; }
    public static boolean isSetupActive() { return phase == Phase.LOBBY; }

    public static void reset() {
        phase = Phase.IDLE;
        activeGame = null;
        hostId = null;
        teamsEnabled = false;
        teamCount = 0;
        activeSettings = null;
        matchTicksRemaining = 0;
        tickCounter = 0;
        persistenceTickCounter = 0;
        frozeGameTicks = false;
        TEAM_SELECTIONS.clear();
        MATCH_PARTICIPANTS.clear();
        DEAD_PARTICIPANTS.clear();
        PRACTICE_PROMOTED_OPS.clear();
        FREEZE_POINTS.clear();
    }

    private static void pauseMatchInternal(MinecraftServer server, ServerPlayer trigger, boolean recoveredAfterRestart, boolean automaticHostPause) {
        if (phase != Phase.RUNNING) {
            return;
        }

        phase = Phase.PAUSED;
        tickCounter = 0;
        stopBorder(server);
        freezeGameTicks(server);
        setPvp(server, false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            freezePlayer(player);
            player.setGameMode(GameType.SPECTATOR);
        }
        saveSession(server);

        Component reason;
        if (recoveredAfterRestart) {
            reason = Component.translatable("message.mageadditions.minigame.auto_paused_restart");
        } else if (automaticHostPause && trigger != null) {
            reason = Component.translatable("message.mageadditions.minigame.auto_paused_host", trigger.getDisplayName());
        } else {
            reason = Component.translatable("message.mageadditions.minigame.match_paused", trigger == null ? Component.literal("Server") : trigger.getDisplayName());
        }
        server.getPlayerList().broadcastSystemMessage(reason.copy().withStyle(ChatFormatting.YELLOW), false);
    }

    private static void freezeGameTicks(MinecraftServer server) {
        if (!server.tickRateManager().isFrozen()) {
            server.tickRateManager().setFrozen(true);
            frozeGameTicks = true;
        }
    }

    private static void unfreezeGameTicks(MinecraftServer server) {
        if (frozeGameTicks && server.tickRateManager().isFrozen()) {
            server.tickRateManager().setFrozen(false);
        }
        frozeGameTicks = false;
    }

    private static void stopBorder(MinecraftServer server) {
        WorldBorder border = server.overworld().getWorldBorder();
        border.setSize(border.getSize());
    }

    private static void freezeOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            freezePlayer(player);
            FreezePoint point = FREEZE_POINTS.get(player.getUUID());
            if (point == null) {
                continue;
            }

            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.resetFallDistance();

            // Pausing locks the player's position, not their camera. Previously we
            // teleported every paused player every tick using the yaw/pitch captured
            // when the pause began. That continually fought normal mouse input and
            // caused fast camera movement to jitter or snap back.
            //
            // Only send a correction when the player actually leaves the frozen
            // position, and preserve whatever rotation they currently have. This keeps
            // movement frozen while looking around remains identical to normal play.
            boolean wrongLevel = player.serverLevel() != point.level();
            boolean moved = wrongLevel || player.distanceToSqr(point.x(), point.y(), point.z()) > 1.0E-6D;
            if (moved) {
                player.teleportTo(
                        point.level(),
                        point.x(),
                        point.y(),
                        point.z(),
                        player.getYRot(),
                        player.getXRot()
                );
            }
        }
    }

    private static void freezePlayer(ServerPlayer player) {
        FREEZE_POINTS.computeIfAbsent(player.getUUID(), ignored -> new FreezePoint(
                player.serverLevel(),
                player.getX(),
                player.getY(),
                player.getZ()
        ));
    }

    private static void showPausedActionbar(MinecraftServer server) {
        int seconds = Math.max(0, matchTicksRemaining / 20);
        String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(
                    Component.translatable("message.mageadditions.minigame.paused_actionbar", time).withStyle(ChatFormatting.YELLOW),
                    true
            );
        }
    }

    private static void ensureInsideBorder(ServerPlayer player, ServerLevel level) {
        if (player.serverLevel() != level) {
            return;
        }
        WorldBorder border = level.getWorldBorder();
        double margin = Math.min(3.0, Math.max(0.5, border.getSize() / 8.0));
        double half = Math.max(0.5, border.getSize() / 2.0 - margin);
        double minX = border.getCenterX() - half;
        double maxX = border.getCenterX() + half;
        double minZ = border.getCenterZ() - half;
        double maxZ = border.getCenterZ() + half;
        double x = Math.max(minX, Math.min(maxX, player.getX()));
        double z = Math.max(minZ, Math.min(maxZ, player.getZ()));
        if (Math.abs(x - player.getX()) < 0.001 && Math.abs(z - player.getZ()) < 0.001) {
            return;
        }

        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.sendSystemMessage(Component.translatable("message.mageadditions.minigame.moved_inside_border").withStyle(ChatFormatting.YELLOW));
    }

    private static void restorePlayerForRunningMatch(ServerPlayer player, MinigameDefinition game) {
        if (game == null || !MATCH_PARTICIPANTS.contains(player.getUUID()) || DEAD_PARTICIPANTS.contains(player.getUUID())) {
            player.setGameMode(GameType.SPECTATOR);
            return;
        }
        if (game.practice()) {
            grantPracticeOp(player);
            ensureInsideBorder(player, player.getServer().overworld());
            player.setGameMode(GameType.CREATIVE);
            refillPlayer(player);
        } else {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private static void restoreScoreboardAssignments(MinecraftServer server, MinigameDefinition game) {
        if (!teamsEnabled || game == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            restorePlayerScoreboardTeam(player, game);
        }
    }

    private static void restorePlayerScoreboardTeam(ServerPlayer player, MinigameDefinition game) {
        if (game == null || !teamsEnabled) {
            return;
        }
        ResourceLocation teamId = TEAM_SELECTIONS.get(player.getUUID());
        if (teamId != null) {
            MinigameDefinition.TeamDefinition team = game.team(teamId);
            if (team != null) {
                assignScoreboardTeam(player, team);
            }
        }
    }

    private static void sendMatchControlState(ServerPlayer operator) {
        if (operator == null || activeGame == null || (phase != Phase.RUNNING && phase != Phase.PAUSED)) {
            return;
        }
        MinecraftServer server = operator.getServer();
        if (server == null) {
            return;
        }
        List<OpenMatchControlPayload.DeadPlayer> deadPlayers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (DEAD_PARTICIPANTS.contains(player.getUUID())) {
                deadPlayers.add(new OpenMatchControlPayload.DeadPlayer(player.getUUID(), player.getGameProfile().getName()));
            }
        }
        int onlineParticipants = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MATCH_PARTICIPANTS.contains(player.getUUID())) {
                onlineParticipants++;
            }
        }

        OpenMatchControlPayload payload = buildMatchControlPayload(server, onlineParticipants, deadPlayers);
        if (payload != null) {
            PacketDistributor.sendToPlayer(operator, payload);
        }
    }

    /**
     * Builds the current match-control record by component name rather than binding this
     * manager to one exact constructor revision. The match-control UI has grown several
     * times (live border values, team controls, etc.); using the record metadata here
     * keeps the server sender compatible with both the older and newer payload layouts.
     */
    private static OpenMatchControlPayload buildMatchControlPayload(
            MinecraftServer server,
            int onlineParticipants,
            List<OpenMatchControlPayload.DeadPlayer> deadPlayers
    ) {
        try {
            RecordComponent[] components = OpenMatchControlPayload.class.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] values = new Object[components.length];

            WorldBorder border = server.overworld().getWorldBorder();
            double currentSize = border.getSize();
            double currentRadius = currentSize / 2.0;
            double initialSize = activeSettings == null ? currentSize : activeSettings.initialBorderSize();
            double finalSize = activeSettings == null ? currentSize : activeSettings.finalBorderSize();
            double initialRadius = initialSize / 2.0;
            double finalRadius = finalSize / 2.0;
            int secondsRemaining = Math.max(0, matchTicksRemaining / 20);
            List<?> playerTeams = buildMatchControlTeamEntries(server);

            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                parameterTypes[i] = component.getType();
                values[i] = matchControlComponentValue(
                        component,
                        border,
                        currentSize,
                        currentRadius,
                        initialSize,
                        initialRadius,
                        finalSize,
                        finalRadius,
                        secondsRemaining,
                        onlineParticipants,
                        deadPlayers,
                        playerTeams
                );
            }

            Constructor<OpenMatchControlPayload> constructor = OpenMatchControlPayload.class.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MageAdditions.LOGGER.error("Could not build match-control payload", exception);
            return null;
        }
    }

    private static Object matchControlComponentValue(
            RecordComponent component,
            WorldBorder border,
            double currentSize,
            double currentRadius,
            double initialSize,
            double initialRadius,
            double finalSize,
            double finalRadius,
            int secondsRemaining,
            int onlineParticipants,
            List<OpenMatchControlPayload.DeadPlayer> deadPlayers,
            List<?> playerTeams
    ) {
        String name = component.getName().toLowerCase();
        Class<?> type = component.getType();

        if (type == ResourceLocation.class) {
            return activeGame;
        }
        if (type == boolean.class || type == Boolean.class) {
            if (name.contains("paused")) {
                return phase == Phase.PAUSED;
            }
            if (name.contains("team")) {
                return teamsEnabled;
            }
            return false;
        }
        if (type == int.class || type == Integer.class) {
            if (name.contains("second") || name.contains("time") || name.contains("remaining")) {
                return secondsRemaining;
            }
            if (name.contains("team") && name.contains("count")) {
                return teamCount;
            }
            if (name.contains("online") || name.contains("connected")) {
                return onlineParticipants;
            }
            if (name.contains("total") || name.contains("participant") || name.contains("player")) {
                return MATCH_PARTICIPANTS.size();
            }
            return 0;
        }
        if (type == double.class || type == Double.class) {
            boolean radius = name.contains("radius");
            if (name.contains("centerx") || name.contains("center_x")) {
                return border.getCenterX();
            }
            if (name.contains("centerz") || name.contains("center_z")) {
                return border.getCenterZ();
            }
            if (name.contains("initial") || name.contains("start")) {
                return radius ? initialRadius : initialSize;
            }
            if (name.contains("final") || name.contains("end") || name.contains("target")) {
                return radius ? finalRadius : finalSize;
            }
            // Current/live border is the safest fallback for an unrecognised border double.
            return radius ? currentRadius : currentSize;
        }
        if (List.class.isAssignableFrom(type)) {
            if (name.contains("dead")) {
                return deadPlayers;
            }
            if (name.contains("team") || name.contains("player") || name.contains("participant")) {
                return playerTeams;
            }
            return List.of();
        }
        return null;
    }

    private static List<?> buildMatchControlTeamEntries(MinecraftServer server) {
        Class<?> entryClass = null;
        for (Class<?> nested : OpenMatchControlPayload.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("PlayerTeamEntry")) {
                entryClass = nested;
                break;
            }
        }
        if (entryClass == null || !entryClass.isRecord()) {
            return List.of();
        }

        try {
            RecordComponent[] components = entryClass.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
            }
            Constructor<?> constructor = entryClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            List<Object> entries = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!MATCH_PARTICIPANTS.contains(player.getUUID())) {
                    continue;
                }

                Object[] values = new Object[components.length];
                ResourceLocation selectedTeam = TEAM_SELECTIONS.get(player.getUUID());
                for (int i = 0; i < components.length; i++) {
                    RecordComponent component = components[i];
                    String name = component.getName().toLowerCase();
                    Class<?> type = component.getType();

                    if (type == UUID.class) {
                        values[i] = player.getUUID();
                    } else if (type == String.class) {
                        values[i] = player.getGameProfile().getName();
                    } else if (type == ResourceLocation.class) {
                        values[i] = selectedTeam != null ? selectedTeam : MinigameRegistry.FFA_TEAM_ID;
                    } else if (type == boolean.class || type == Boolean.class) {
                        if (name.contains("dead")) {
                            values[i] = DEAD_PARTICIPANTS.contains(player.getUUID());
                        } else if (name.contains("host")) {
                            values[i] = player.getUUID().equals(hostId);
                        } else if (name.contains("online") || name.contains("connected")) {
                            values[i] = true;
                        } else {
                            values[i] = false;
                        }
                    } else if (type == int.class || type == Integer.class) {
                        values[i] = 0;
                    } else if (type == double.class || type == Double.class) {
                        values[i] = 0.0;
                    } else {
                        values[i] = null;
                    }
                }
                entries.add(constructor.newInstance(values));
            }
            return entries;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MageAdditions.LOGGER.warn("Could not build match-control player/team entries", exception);
            return List.of();
        }
    }

    private static void saveSession(MinecraftServer server) {
        if (server == null || phase == Phase.IDLE || activeGame == null || activeSettings == null) {
            return;
        }
        MinigameSessionStore.save(server, new MinigameSessionStore.Snapshot(
                phase,
                activeGame,
                hostId,
                teamsEnabled,
                teamCount,
                activeSettings,
                matchTicksRemaining,
                server.overworld().getWorldBorder().getSize(),
                TEAM_SELECTIONS,
                MATCH_PARTICIPANTS,
                DEAD_PARTICIPANTS,
                PRACTICE_PROMOTED_OPS
        ));
    }

    private record FreezePoint(ServerLevel level, double x, double y, double z) {}

    private static void putPlayerInPregame(ServerPlayer player) {
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        refillPlayer(player);
    }

    private static void refillPlayer(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.getFoodData().setExhaustion(0.0F);
    }

    private static void keepProtectedPlayersFed(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refillPlayer(player);
        }
    }

    private static void grantPracticeOp(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || server.getPlayerList().isOp(player.getGameProfile())) {
            return;
        }
        PRACTICE_PROMOTED_OPS.add(player.getUUID());
        server.getPlayerList().op(player.getGameProfile());
    }

    private static void revokePracticeOps(MinecraftServer server) {
        for (UUID uuid : Set.copyOf(PRACTICE_PROMOTED_OPS)) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                server.getPlayerList().deop(online.getGameProfile());
                continue;
            }
            server.getProfileCache().get(uuid).ifPresent(server.getPlayerList()::deop);
        }
        PRACTICE_PROMOTED_OPS.clear();
    }

    private static void restoreDefaultWorldBorder(MinecraftServer server) {
        server.overworld().getWorldBorder().applySettings(WorldBorder.DEFAULT_SETTINGS);
    }

    private static void broadcastLobbyState(MinecraftServer server) {
        if (phase != Phase.LOBBY || activeGame == null) {
            return;
        }
        List<LobbyStatePayload.RosterEntry> entries = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceLocation team = TEAM_SELECTIONS.get(player.getUUID());
            if (team != null) {
                LobbyStatePayload.RosterEntry entry = buildLobbyRosterEntry(player, team);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        LobbyStatePayload payload = new LobbyStatePayload(
                activeGame,
                server.getPlayerList().getPlayerCount(),
                allOnlinePlayersSelected(server),
                entries
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        syncTeamOutlines(server);
    }

    /**
     * Builds a lobby roster entry by record component rather than constructor arity.
     * Newer lobby payloads include the player's UUID while older revisions only
     * stored name + team; this keeps MinigameManager source-compatible with both.
     */
    private static LobbyStatePayload.RosterEntry buildLobbyRosterEntry(
            ServerPlayer player,
            ResourceLocation team
    ) {
        try {
            RecordComponent[] components = LobbyStatePayload.RosterEntry.class.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] values = new Object[components.length];

            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                parameterTypes[i] = component.getType();
                Class<?> type = component.getType();

                if (type == UUID.class) {
                    values[i] = player.getUUID();
                } else if (type == String.class) {
                    values[i] = player.getGameProfile().getName();
                } else if (type == ResourceLocation.class) {
                    values[i] = team;
                } else if (type == boolean.class || type == Boolean.class) {
                    values[i] = false;
                } else if (type == int.class || type == Integer.class) {
                    values[i] = 0;
                } else if (type == double.class || type == Double.class) {
                    values[i] = 0.0;
                } else {
                    values[i] = null;
                }
            }

            Constructor<LobbyStatePayload.RosterEntry> constructor =
                    LobbyStatePayload.RosterEntry.class.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MageAdditions.LOGGER.warn(
                    "Could not build lobby roster entry for {}",
                    player.getGameProfile().getName(),
                    exception
            );
            return null;
        }
    }

    private static void syncTeamOutlines(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTeamOutline(player, server);
        }
    }

    private static void syncTeamOutline(ServerPlayer viewer, MinecraftServer server) {
        ResourceLocation viewerTeam = TEAM_SELECTIONS.get(viewer.getUUID());
        if (!teamsEnabled || viewerTeam == null || viewerTeam.equals(MinigameRegistry.FFA_TEAM_ID)) {
            PacketDistributor.sendToPlayer(viewer, new TeamOutlinePayload(false, List.of()));
            return;
        }

        List<UUID> teammates = new ArrayList<>();
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (!candidate.getUUID().equals(viewer.getUUID()) && viewerTeam.equals(TEAM_SELECTIONS.get(candidate.getUUID()))) {
                teammates.add(candidate.getUUID());
            }
        }
        PacketDistributor.sendToPlayer(viewer, new TeamOutlinePayload(true, teammates));
    }

    private static void prepareScoreboardTeams(MinecraftServer server, MinigameDefinition game) {
        Scoreboard scoreboard = server.getScoreboard();
        for (MinigameDefinition.TeamDefinition team : game.teams()) {
            String scoreboardName = scoreboardTeamName(team.id());
            PlayerTeam scoreboardTeam = scoreboard.getPlayerTeam(scoreboardName);
            if (scoreboardTeam == null) {
                scoreboardTeam = scoreboard.addPlayerTeam(scoreboardName);
            }
            for (String playerName : List.copyOf(scoreboardTeam.getPlayers())) {
                scoreboard.removePlayerFromTeam(playerName, scoreboardTeam);
            }
            configureScoreboardTeam(scoreboardTeam, team);
        }
    }

    private static void assignScoreboardTeam(ServerPlayer player, MinigameDefinition.TeamDefinition team) {
        MinecraftServer server = player.getServer();
        if (server == null || team == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam scoreboardTeam = scoreboard.getPlayerTeam(scoreboardTeamName(team.id()));
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.addPlayerTeam(scoreboardTeamName(team.id()));
        }
        configureScoreboardTeam(scoreboardTeam, team);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), scoreboardTeam);
    }

    private static void configureScoreboardTeam(PlayerTeam scoreboardTeam, MinigameDefinition.TeamDefinition team) {
        scoreboardTeam.setDisplayName(team.displayName());
        // Keep vanilla nameplates neutral for opponents; teammate identification is client-private.
        scoreboardTeam.setColor(ChatFormatting.RESET);
        scoreboardTeam.setAllowFriendlyFire(false);
    }

    private static void removeFromAnyTeam(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        }
    }

    private static boolean isActiveTeam(MinigameDefinition game, ResourceLocation teamId) {
        for (int i = 0; i < teamCount; i++) {
            if (game.teams().get(i).id().equals(teamId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allOnlinePlayersSelected(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return !players.isEmpty() && players.stream().allMatch(player -> TEAM_SELECTIONS.containsKey(player.getUUID()));
    }

    private static MinigameDefinition activeDefinition() {
        return activeGame == null ? null : MinigameRegistry.get(activeGame);
    }

    private static String scoreboardTeamName(ResourceLocation teamId) {
        String raw = "ma_" + teamId.getPath();
        return raw.length() <= 16 ? raw : raw.substring(0, 16);
    }

    private static void setPvp(MinecraftServer server, boolean enabled) {
        server.setPvpAllowed(enabled);
    }

    private static void grantCraftingRecipes(MinecraftServer server) {
        for (String recipe : RECIPE_IDS) {
            runServerCommand(server, "recipe give @a " + recipe);
        }
    }

    private static void runServerCommand(MinecraftServer server, String command) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
        } catch (RuntimeException ex) {
            MageAdditions.LOGGER.debug("Optional minigame command failed: {}", command, ex);
        }
    }

    private static void runAsPlayer(ServerPlayer player, String command) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        try {
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
        } catch (RuntimeException ex) {
            MageAdditions.LOGGER.warn("Could not run starter-kit command '{}' for {}", command, player.getGameProfile().getName(), ex);
        }
    }

    private static void setEquipment(ServerPlayer player, EquipmentSlot slot, String itemId) {
        player.setItemSlot(slot, itemStack(itemId, 1));
    }

    private static void setHotbar(ServerPlayer player, int slot, String itemId, int count) {
        player.getInventory().setItem(slot, itemStack(itemId, count));
    }

    private static void giveItem(ServerPlayer player, String itemId, int count) {
        player.getInventory().add(itemStack(itemId, count));
    }

    private static ItemStack itemStack(String itemId, int count) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            MageAdditions.LOGGER.warn("Missing starter-kit item {}", itemId);
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }
}
