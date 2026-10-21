package org.lts.prophunt.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.lts.prophunt.core.Pos;

public final class Arena {

    private final String id;
    private String world;
    private Pos lobby;
    private Pos hunterSpawn;
    private final List<Pos> propSpawns = new ArrayList<>();
    private Pos corner1;
    private Pos corner2;
    private final List<Material> blocks = new ArrayList<>();
    private boolean enabled;
    private final Map<String, Object> overrides = new LinkedHashMap<>();

    public Arena(String id, String world) {
        this.id = id;
        this.world = world;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return world;
    }

    public World world() {
        return world == null ? null : Bukkit.getWorld(world);
    }

    public void world(String name) {
        this.world = name;
    }

    public Pos lobby() {
        return lobby != null ? lobby : hunterSpawn;
    }

    public void lobby(Pos pos) {
        this.lobby = pos;
    }

    public Pos hunterSpawn() {
        return hunterSpawn;
    }

    public void hunterSpawn(Pos pos) {
        this.hunterSpawn = pos;
    }

    public List<Pos> propSpawns() {
        return propSpawns;
    }

    public void corner(int index, Pos pos) {
        if (index == 1) {
            corner1 = pos;
        } else {
            corner2 = pos;
        }
    }

    public BoundingBox region() {
        if (corner1 == null || corner2 == null) {
            return null;
        }
        return new BoundingBox(
                Math.floor(Math.min(corner1.x(), corner2.x())), Math.floor(Math.min(corner1.y(), corner2.y())), Math.floor(Math.min(corner1.z(), corner2.z())),
                Math.floor(Math.max(corner1.x(), corner2.x())) + 1, Math.floor(Math.max(corner1.y(), corner2.y())) + 1, Math.floor(Math.max(corner1.z(), corner2.z())) + 1);
    }

    public void clearRegion() {
        corner1 = null;
        corner2 = null;
    }

    public List<Material> blocks() {
        return blocks;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean value) {
        this.enabled = value;
    }

    public Map<String, Object> overrides() {
        return overrides;
    }

    public ArenaSettings settings(ConfigurationSection defaults) {
        return ArenaSettings.resolve(defaults, overrides);
    }

    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (world() == null) {
            out.add("world");
        }
        if (hunterSpawn == null) {
            out.add("hunter-spawn");
        }
        if (propSpawns.isEmpty()) {
            out.add("prop-spawns");
        }
        if (blocks.isEmpty()) {
            out.add("blocks");
        }
        return out;
    }

    public static boolean usable(Material material) {
        return material != null && material.isBlock() && material.isItem() && !material.isAir()
                && material != Material.BARRIER && material != Material.LIGHT && material != Material.STRUCTURE_VOID;
    }

    public YamlConfiguration save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("world", world);
        out.set("enabled", enabled);
        writePos(out, "lobby", lobby);
        writePos(out, "hunter-spawn", hunterSpawn);
        writePos(out, "corner1", corner1);
        writePos(out, "corner2", corner2);
        for (int i = 0; i < propSpawns.size(); i++) {
            propSpawns.get(i).write(out.createSection("prop-spawns." + i));
        }
        List<String> names = new ArrayList<>();
        for (Material material : blocks) {
            names.add(material.getKey().getKey());
        }
        out.set("blocks", names);
        if (!overrides.isEmpty()) {
            out.createSection("settings", overrides);
        }
        return out;
    }

    public static Arena load(String id, YamlConfiguration in, Consumer<String> warn) {
        Arena arena = new Arena(id, in.getString("world"));
        arena.enabled = in.getBoolean("enabled");
        arena.lobby = Pos.read(in.getConfigurationSection("lobby"));
        arena.hunterSpawn = Pos.read(in.getConfigurationSection("hunter-spawn"));
        arena.corner1 = Pos.read(in.getConfigurationSection("corner1"));
        arena.corner2 = Pos.read(in.getConfigurationSection("corner2"));
        ConfigurationSection spawns = in.getConfigurationSection("prop-spawns");
        if (spawns != null) {
            for (String key : spawns.getKeys(false)) {
                Pos pos = Pos.read(spawns.getConfigurationSection(key));
                if (pos != null) {
                    arena.propSpawns.add(pos);
                }
            }
        }
        for (String name : in.getStringList("blocks")) {
            Material material = Material.matchMaterial(name.toLowerCase(Locale.ROOT));
            if (usable(material)) {
                arena.blocks.add(material);
            } else {
                warn.accept("Arena " + id + ": unknown block " + name);
            }
        }
        ConfigurationSection settings = in.getConfigurationSection("settings");
        if (settings != null) {
            for (String key : settings.getKeys(false)) {
                if (ArenaSettings.KEYS.contains(key)) {
                    arena.overrides.put(key, settings.get(key));
                } else {
                    warn.accept("Arena " + id + ": unknown setting " + key);
                }
            }
        }
        return arena;
    }

    private static void writePos(YamlConfiguration out, String key, Pos pos) {
        if (pos != null) {
            pos.write(out.createSection(key));
        }
    }
}
