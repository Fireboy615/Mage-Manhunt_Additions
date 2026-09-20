package net.fireboy.mageadditions.minigame;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Small world-local recovery file so an interrupted match can come back paused after a restart. */
final class MinigameSessionStore {
    private static final String FILE_NAME = "mageadditions-minigame-session.properties";

    private MinigameSessionStore() {}

    static void save(MinecraftServer server, Snapshot snapshot) {
        if (server == null || snapshot == null || snapshot.phase() == MinigameManager.Phase.IDLE) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("phase", snapshot.phase().name());
        properties.setProperty("game", snapshot.gameId().toString());
        properties.setProperty("host", snapshot.hostId() == null ? "" : snapshot.hostId().toString());
        properties.setProperty("teamsEnabled", Boolean.toString(snapshot.teamsEnabled()));
        properties.setProperty("teamCount", Integer.toString(snapshot.teamCount()));
        properties.setProperty("durationSeconds", Integer.toString(snapshot.settings().durationSeconds()));
        properties.setProperty("initialBorderSize", Double.toString(snapshot.settings().initialBorderSize()));
        properties.setProperty("finalBorderSize", Double.toString(snapshot.settings().finalBorderSize()));
        properties.setProperty("randomTeleport", Boolean.toString(snapshot.settings().randomTeleport()));
        properties.setProperty("kitPreset", snapshot.settings().kitPreset().name());
        properties.setProperty("matchTicksRemaining", Integer.toString(snapshot.matchTicksRemaining()));
        properties.setProperty("currentBorderSize", Double.toString(snapshot.currentBorderSize()));
        properties.setProperty("teamSelections", encodeTeamSelections(snapshot.teamSelections()));
        properties.setProperty("participants", encodeUuids(snapshot.participants()));
        properties.setProperty("deadParticipants", encodeUuids(snapshot.deadParticipants()));

        Path file = stateFile(server);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Mage Additions active minigame recovery state");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            MageAdditions.LOGGER.warn("Could not save minigame recovery state to {}", file, ex);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    static Optional<Snapshot> load(MinecraftServer server) {
        Path file = stateFile(server);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);

            MinigameManager.Phase phase = MinigameManager.Phase.valueOf(properties.getProperty("phase", "IDLE"));
            if (phase == MinigameManager.Phase.IDLE) {
                return Optional.empty();
            }
            ResourceLocation gameId = ResourceLocation.parse(properties.getProperty("game"));
            UUID hostId = parseUuid(properties.getProperty("host", ""));
            boolean teamsEnabled = Boolean.parseBoolean(properties.getProperty("teamsEnabled", "false"));
            int teamCount = Integer.parseInt(properties.getProperty("teamCount", "0"));
            MinigameSettings settings = new MinigameSettings(
                    Integer.parseInt(properties.getProperty("durationSeconds", "0")),
                    Double.parseDouble(properties.getProperty("initialBorderSize", "151")),
                    Double.parseDouble(properties.getProperty("finalBorderSize", "151")),
                    Boolean.parseBoolean(properties.getProperty("randomTeleport", "false")),
                    MinigameSettings.KitPreset.valueOf(properties.getProperty("kitPreset", MinigameSettings.KitPreset.MODE_DEFAULT.name()))
            ).validated();
            int matchTicksRemaining = Math.max(0, Integer.parseInt(properties.getProperty("matchTicksRemaining", "0")));
            double currentBorderSize = Double.parseDouble(properties.getProperty("currentBorderSize", Double.toString(settings.initialBorderSize())));

            return Optional.of(new Snapshot(
                    phase,
                    gameId,
                    hostId,
                    teamsEnabled,
                    teamCount,
                    settings,
                    matchTicksRemaining,
                    currentBorderSize,
                    decodeTeamSelections(properties.getProperty("teamSelections", "")),
                    decodeUuids(properties.getProperty("participants", "")),
                    decodeUuids(properties.getProperty("deadParticipants", ""))
            ));
        } catch (Exception ex) {
            MageAdditions.LOGGER.warn("Could not load minigame recovery state from {}; starting without a recovered session", file, ex);
            return Optional.empty();
        }
    }

    static void delete(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Files.deleteIfExists(stateFile(server));
        } catch (IOException ex) {
            MageAdditions.LOGGER.warn("Could not delete minigame recovery state", ex);
        }
    }

    private static Path stateFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    private static String encodeUuids(Set<UUID> values) {
        StringBuilder encoded = new StringBuilder();
        for (UUID uuid : values) {
            if (!encoded.isEmpty()) {
                encoded.append(',');
            }
            encoded.append(uuid);
        }
        return encoded.toString();
    }

    private static Set<UUID> decodeUuids(String value) {
        Set<UUID> decoded = new HashSet<>();
        if (value == null || value.isBlank()) {
            return decoded;
        }
        for (String token : value.split(",")) {
            UUID uuid = parseUuid(token);
            if (uuid != null) {
                decoded.add(uuid);
            }
        }
        return decoded;
    }

    private static String encodeTeamSelections(Map<UUID, ResourceLocation> values) {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<UUID, ResourceLocation> entry : values.entrySet()) {
            if (!encoded.isEmpty()) {
                encoded.append(';');
            }
            encoded.append(entry.getKey()).append('|').append(entry.getValue());
        }
        return encoded.toString();
    }

    private static Map<UUID, ResourceLocation> decodeTeamSelections(String value) {
        Map<UUID, ResourceLocation> decoded = new HashMap<>();
        if (value == null || value.isBlank()) {
            return decoded;
        }
        for (String token : value.split(";")) {
            int separator = token.indexOf('|');
            if (separator <= 0 || separator >= token.length() - 1) {
                continue;
            }
            UUID uuid = parseUuid(token.substring(0, separator));
            if (uuid != null) {
                decoded.put(uuid, ResourceLocation.parse(token.substring(separator + 1)));
            }
        }
        return decoded;
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    record Snapshot(
            MinigameManager.Phase phase,
            ResourceLocation gameId,
            UUID hostId,
            boolean teamsEnabled,
            int teamCount,
            MinigameSettings settings,
            int matchTicksRemaining,
            double currentBorderSize,
            Map<UUID, ResourceLocation> teamSelections,
            Set<UUID> participants,
            Set<UUID> deadParticipants
    ) {
        Snapshot {
            teamSelections = Map.copyOf(teamSelections);
            participants = Set.copyOf(participants);
            deadParticipants = Set.copyOf(deadParticipants);
        }
    }
}
