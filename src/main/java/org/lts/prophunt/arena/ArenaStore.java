package org.lts.prophunt.arena;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArenaStore {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final JavaPlugin plugin;
    private final ExecutorService io;
    private final File folder;
    private final Map<String, Arena> arenas = new TreeMap<>();

    public ArenaStore(JavaPlugin plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
        this.folder = new File(plugin.getDataFolder(), "arenas");
    }

    public void load() {
        arenas.clear();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!validId(id)) {
                plugin.getLogger().warning("Skipping arena file with a bad name: " + file.getName());
                continue;
            }
            arenas.put(id, Arena.load(id, YamlConfiguration.loadConfiguration(file), plugin.getLogger()::warning));
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s)");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public Arena get(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public List<String> ids() {
        return new ArrayList<>(arenas.keySet());
    }

    public Arena create(String id, String world) {
        Arena arena = new Arena(id, world);
        arenas.put(id, arena);
        save(arena);
        return arena;
    }

    public void delete(Arena arena) {
        arenas.remove(arena.id());
        File file = file(arena.id());
        io.execute(() -> {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete " + file.getName(), e);
            }
        });
    }

    public void save(Arena arena) {
        String text = arena.save().saveToString();
        File file = file(arena.id());
        io.execute(() -> {
            try {
                Files.createDirectories(folder.toPath());
                File temp = new File(folder, file.getName() + ".tmp");
                Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + file.getName(), e);
            }
        });
    }

    private File file(String id) {
        return new File(folder, id + ".yml");
    }
}
