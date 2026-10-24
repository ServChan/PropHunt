package org.lts.prophunt;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.lts.prophunt.arena.ArenaStore;
import org.lts.prophunt.command.PropHuntCommand;
import org.lts.prophunt.core.Messages;
import org.lts.prophunt.core.Snapshots;
import org.lts.prophunt.core.Tools;
import org.lts.prophunt.game.GameListener;
import org.lts.prophunt.game.GameManager;

public final class PropHunt extends JavaPlugin {

    private ExecutorService io;
    private Messages msg;
    private Tools tools;
    private Snapshots snapshots;
    private ArenaStore arenas;
    private GameManager games;
    private List<Key> tauntSounds = List.of();
    private List<String> allowedCommands = List.of();

    @Override
    public void onEnable() {
        io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PropHunt-IO");
            thread.setDaemon(true);
            return thread;
        });
        saveDefaultConfig();
        msg = new Messages(this);
        tools = new Tools(this, msg);
        snapshots = new Snapshots(this, io);
        arenas = new ArenaStore(this, io);
        games = new GameManager(this);
        loadSettings();
        arenas.load();
        getServer().getPluginManager().registerEvents(new GameListener(this, games), this);
        PropHuntCommand command = new PropHuntCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register("prophunt", "Prop hunt", List.of("ph"), command));
        games.start();
        Bukkit.getOnlinePlayers().forEach(snapshots::recover);
        getLogger().info("PropHunt " + getPluginMeta().getVersion() + " ready, " + arenas.all().size() + " arena(s)");
    }

    @Override
    public void onDisable() {
        if (io != null) {
            io.shutdown();
            try {
                io.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (games != null) {
            games.shutdown();
        }
    }

    public void reload() {
        games.shutdown();
        flushIo();
        reloadConfig();
        loadSettings();
        arenas.load();
        games.start();
    }

    private void flushIo() {
        try {
            io.submit(() -> {
            }).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            getLogger().log(Level.WARNING, "Pending arena saves did not finish before reload", e);
        }
    }

    private void loadSettings() {
        msg.load(getConfig().getString("language", "ru").toLowerCase(Locale.ROOT));
        List<Key> sounds = new ArrayList<>();
        for (String name : getConfig().getStringList("taunt-sounds")) {
            if (Key.parseable(name)) {
                sounds.add(Key.key(name));
            } else {
                getLogger().warning("Bad taunt sound key: " + name);
            }
        }
        tauntSounds = List.copyOf(sounds);
        List<String> commands = new ArrayList<>();
        for (String name : getConfig().getStringList("allowed-commands")) {
            commands.add(name.toLowerCase(Locale.ROOT).replace("/", ""));
        }
        commands.add("prophunt");
        commands.add("ph");
        allowedCommands = List.copyOf(commands);
    }

    public ConfigurationSection defaults() {
        ConfigurationSection section = getConfig().getConfigurationSection("defaults");
        return section != null ? section : new MemoryConfiguration();
    }

    public Messages msg() {
        return msg;
    }

    public Tools tools() {
        return tools;
    }

    public Snapshots snapshots() {
        return snapshots;
    }

    public ArenaStore arenas() {
        return arenas;
    }

    public GameManager games() {
        return games;
    }

    public List<Key> tauntSounds() {
        return tauntSounds;
    }

    public List<String> allowedCommands() {
        return allowedCommands;
    }
}
