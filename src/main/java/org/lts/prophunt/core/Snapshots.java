package org.lts.prophunt.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

public final class Snapshots {

    private final JavaPlugin plugin;
    private final ExecutorService io;
    private final File folder;
    private final Map<UUID, YamlConfiguration> live = new HashMap<>();
    private final Set<UUID> recovering = new HashSet<>();

    public Snapshots(JavaPlugin plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
        this.folder = new File(plugin.getDataFolder(), "snapshots");
    }

    public void capture(Player player) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("name", player.getName());
        data.set("location", player.getLocation());
        data.set("gamemode", player.getGameMode().name());
        data.set("inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(player.getInventory().getContents())));
        data.set("level", player.getLevel());
        data.set("exp", (double) player.getExp());
        data.set("health", player.getHealth());
        data.set("food", player.getFoodLevel());
        data.set("saturation", (double) player.getSaturation());
        data.set("fire", player.getFireTicks());
        data.set("allow-flight", player.getAllowFlight());
        data.set("flying", player.isFlying());
        data.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        data.set("scale", scale == null ? 1.0 : scale.getBaseValue());
        live.put(player.getUniqueId(), data);
        String text = data.saveToString();
        File file = file(player.getUniqueId());
        io.execute(() -> write(file, text));
    }

    public boolean has(UUID id) {
        return live.containsKey(id);
    }

    public boolean recovering(UUID id) {
        return recovering.contains(id);
    }

    public void restore(Player player) {
        YamlConfiguration data = live.remove(player.getUniqueId());
        if (data == null) {
            return;
        }
        apply(player, data);
        deleteLater(file(player.getUniqueId()));
    }

    private void deleteLater(File file) {
        try {
            io.execute(() -> delete(file));
        } catch (RejectedExecutionException e) {
            delete(file);
        }
    }

    public void recover(Player player) {
        UUID id = player.getUniqueId();
        File file = file(id);
        recovering.add(id);
        io.execute(() -> {
            String text = null;
            if (file.isFile()) {
                try {
                    text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not read snapshot " + file.getName(), e);
                }
            }
            String found = text;
            Bukkit.getScheduler().runTask(plugin, () -> {
                recovering.remove(id);
                if (found == null) {
                    return;
                }
                Player online = Bukkit.getPlayer(id);
                if (online == null || live.containsKey(id)) {
                    return;
                }
                YamlConfiguration data = new YamlConfiguration();
                try {
                    data.loadFromString(found);
                } catch (InvalidConfigurationException e) {
                    plugin.getLogger().log(Level.WARNING, "Broken snapshot " + file.getName(), e);
                    return;
                }
                apply(online, data);
                plugin.getLogger().info("Restored " + online.getName() + " from a snapshot left by an unfinished game");
                deleteLater(file);
            });
        });
    }

    private void apply(Player player, YamlConfiguration data) {
        try {
            String inventory = data.getString("inventory");
            if (inventory != null) {
                ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(inventory));
                ItemStack[] contents = new ItemStack[player.getInventory().getSize()];
                System.arraycopy(items, 0, contents, 0, Math.min(items.length, contents.length));
                player.getInventory().setContents(contents);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore the inventory of " + player.getName(), e);
        }
        player.closeInventory();
        GameMode mode = parseMode(data.getString("gamemode"));
        player.setGameMode(mode);
        player.setAllowFlight(data.getBoolean("allow-flight") || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        player.setFlying(player.getAllowFlight() && data.getBoolean("flying"));
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(data.getDouble("scale", 1.0));
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.max(0.5, Math.min(max, data.getDouble("health", max))));
        player.setFoodLevel(data.getInt("food", 20));
        player.setSaturation((float) data.getDouble("saturation", 5.0));
        player.setLevel(data.getInt("level"));
        player.setExp((float) Math.max(0, Math.min(0.9999, data.getDouble("exp"))));
        player.setFireTicks(data.getInt("fire"));
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        List<?> effects = data.getList("effects", List.of());
        for (Object effect : effects) {
            if (effect instanceof PotionEffect potion) {
                player.addPotionEffect(potion);
            }
        }
        Location location = data.getLocation("location");
        if (location == null || location.getWorld() == null) {
            location = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        player.teleport(location);
    }

    private static GameMode parseMode(String name) {
        try {
            return name == null ? GameMode.SURVIVAL : GameMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    private void write(File file, String text) {
        try {
            Files.createDirectories(folder.toPath());
            File temp = new File(folder, file.getName() + ".tmp");
            Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save snapshot " + file.getName(), e);
        }
    }

    private void delete(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not delete snapshot " + file.getName(), e);
        }
    }
}
