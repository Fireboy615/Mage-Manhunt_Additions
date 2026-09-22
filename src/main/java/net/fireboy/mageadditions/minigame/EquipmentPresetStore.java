package net.fireboy.mageadditions.minigame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fireboy.mageadditions.MageAdditions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** World-local saved player loadouts used by the minigame setup menu. */
public final class EquipmentPresetStore {
    private static final String FILE_NAME = "mageadditions-equipment-presets.nbt";
    private static final int MAX_NAME_LENGTH = 32;

    private EquipmentPresetStore() {}

    public static List<String> names(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        CompoundTag root = readRoot(server);
        ListTag presets = root.getList("Presets", 10);
        for (int i = 0; i < presets.size(); i++) {
            String name = presets.getCompound(i).getString("Name");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    public static String saveCurrent(ServerPlayer player, String requestedName) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return "";
        }
        String name = sanitizeName(requestedName);
        if (name.isBlank()) {
            return "";
        }

        CompoundTag root = readRoot(server);
        ListTag presets = root.getList("Presets", 10);
        ListTag replacement = new ListTag();
        boolean replaced = false;
        for (int i = 0; i < presets.size(); i++) {
            CompoundTag existing = presets.getCompound(i);
            if (existing.getString("Name").equalsIgnoreCase(name)) {
                replacement.add(makePreset(player, name));
                replaced = true;
            } else {
                replacement.add(existing.copy());
            }
        }
        if (!replaced) {
            replacement.add(makePreset(player, name));
        }
        root.put("Presets", replacement);
        writeRoot(server, root);
        return name;
    }

    public static boolean delete(MinecraftServer server, String requestedName) {
        String name = sanitizeName(requestedName);
        if (name.isBlank()) {
            return false;
        }
        CompoundTag root = readRoot(server);
        ListTag presets = root.getList("Presets", 10);
        ListTag replacement = new ListTag();
        boolean removed = false;
        for (int i = 0; i < presets.size(); i++) {
            CompoundTag existing = presets.getCompound(i);
            if (existing.getString("Name").equalsIgnoreCase(name)) {
                removed = true;
            } else {
                replacement.add(existing.copy());
            }
        }
        if (removed) {
            root.put("Presets", replacement);
            writeRoot(server, root);
        }
        return removed;
    }

    public static boolean apply(ServerPlayer player, String requestedName) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        String name = sanitizeName(requestedName);
        Optional<CompoundTag> preset = find(server, name);
        if (preset.isEmpty()) {
            return false;
        }
        ListTag items = preset.get().getList("Inventory", 10);
        player.getInventory().load(items.copy());
        player.getInventory().setChanged();
        return true;
    }

    private static Optional<CompoundTag> find(MinecraftServer server, String name) {
        if (name.isBlank()) {
            return Optional.empty();
        }
        ListTag presets = readRoot(server).getList("Presets", 10);
        for (int i = 0; i < presets.size(); i++) {
            CompoundTag preset = presets.getCompound(i);
            if (preset.getString("Name").equalsIgnoreCase(name)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    private static CompoundTag makePreset(ServerPlayer player, String name) {
        CompoundTag preset = new CompoundTag();
        preset.putString("Name", name);
        preset.put("Inventory", player.getInventory().save(new ListTag()));
        return preset;
    }

    private static CompoundTag readRoot(MinecraftServer server) {
        Path path = file(server);
        if (!Files.isRegularFile(path)) {
            return new CompoundTag();
        }
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException ex) {
            MageAdditions.LOGGER.warn("Could not read equipment presets from {}", path, ex);
            return new CompoundTag();
        }
    }

    private static void writeRoot(MinecraftServer server, CompoundTag root) {
        Path path = file(server);
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, path);
        } catch (IOException ex) {
            MageAdditions.LOGGER.warn("Could not save equipment presets to {}", path, ex);
        }
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    public static String sanitizeName(String requestedName) {
        if (requestedName == null) {
            return "";
        }
        String cleaned = requestedName.strip().replaceAll("[\\p{Cntrl}\\r\\n\\t]", "");
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return cleaned;
    }
}
