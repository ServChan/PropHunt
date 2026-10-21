package org.lts.prophunt.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.lts.prophunt.PropHunt;
import org.lts.prophunt.arena.Arena;
import org.lts.prophunt.core.Messages;

public final class GameManager {

    private final PropHunt plugin;
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private BukkitTask task;

    public GameManager(PropHunt plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (Game game : new ArrayList<>(games.values())) {
            game.tick(now);
            if (now % 20 == 0) {
                game.second();
            }
        }
    }

    public Game game(Arena arena) {
        return games.computeIfAbsent(arena.id(), id -> new Game(plugin, arena));
    }

    public Game existing(Arena arena) {
        return games.get(arena.id());
    }

    public Game of(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public Collection<Game> all() {
        return games.values();
    }

    public void join(Player player, Arena arena) {
        Messages msg = plugin.msg();
        if (byPlayer.containsKey(player.getUniqueId())) {
            msg.send(player, "errors.already-in-game");
            return;
        }
        if (plugin.snapshots().recovering(player.getUniqueId()) || plugin.snapshots().has(player.getUniqueId())) {
            msg.send(player, "errors.restoring");
            return;
        }
        if (!arena.enabled()) {
            msg.send(player, "errors.arena-disabled", Messages.p("arena", arena.id()));
            return;
        }
        List<String> problems = arena.problems();
        if (!problems.isEmpty()) {
            msg.send(player, "errors.arena-not-ready", Messages.p("arena", arena.id()), Messages.p("problems", describe(problems)));
            return;
        }
        Game game = game(arena);
        if (game.join(player)) {
            byPlayer.put(player.getUniqueId(), game);
        }
    }

    public boolean leave(Player player) {
        Game game = byPlayer.remove(player.getUniqueId());
        if (game == null) {
            return false;
        }
        game.leave(player);
        return true;
    }

    public void close(Arena arena) {
        Game game = games.remove(arena.id());
        if (game == null) {
            return;
        }
        byPlayer.values().removeIf(value -> value == game);
        game.close();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Game game : games.values()) {
            game.close();
        }
        games.clear();
        byPlayer.clear();
    }

    public void welcome(Player newcomer) {
        for (Game game : games.values()) {
            game.hideFrom(newcomer);
        }
    }

    public String describe(List<String> problems) {
        List<String> out = new ArrayList<>();
        for (String problem : problems) {
            out.add(plugin.msg().raw("problems." + problem));
        }
        return String.join(", ", out);
    }
}
