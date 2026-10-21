package org.lts.prophunt.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.lts.prophunt.PropHunt;
import org.lts.prophunt.arena.Arena;
import org.lts.prophunt.arena.ArenaSettings;
import org.lts.prophunt.arena.ArenaStore;
import org.lts.prophunt.core.Messages;
import org.lts.prophunt.core.Pos;
import org.lts.prophunt.game.Game;

public final class PropHuntCommand implements BasicCommand {

    private static final String ADMIN = "prophunt.admin";
    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "list", "help");
    private static final List<String> ADMIN_SUBS = List.of("create", "delete", "setlobby", "sethunterspawn", "addspawn", "clearspawns",
            "pos1", "pos2", "clearregion", "addblock", "removeblock", "blocks", "set", "enable", "disable", "start", "stop", "info", "reload");
    private static final List<String> NO_ARENA = List.of("create", "reload", "leave", "list", "help", "join");
    private static final List<String> EDITS = List.of("delete", "setlobby", "sethunterspawn", "addspawn", "clearspawns", "pos1", "pos2",
            "clearregion", "addblock", "removeblock", "set", "disable");

    private static final class Failure extends RuntimeException {
        private final String key;
        private final TagResolver[] resolvers;

        Failure(String key, TagResolver... resolvers) {
            super(key, null, false, false);
            this.key = key;
            this.resolvers = resolvers;
        }
    }

    private final PropHunt plugin;

    public PropHuntCommand(PropHunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "prophunt.play";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            run(sender, args);
        } catch (Failure failure) {
            plugin.msg().send(sender, failure.key, failure.resolvers);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "/prophunt " + String.join(" ", args) + " failed", e);
            plugin.msg().send(sender, "errors.internal");
        }
    }

    private void run(CommandSender sender, String[] args) {
        Messages msg = plugin.msg();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission(ADMIN)) {
            throw new Failure("errors.no-permission");
        }
        if (!PLAYER_SUBS.contains(sub) && !ADMIN_SUBS.contains(sub)) {
            throw new Failure("errors.unknown-subcommand", Messages.p("sub", sub));
        }
        switch (sub) {
            case "help" -> {
                msg.sendList(sender, "help.player");
                if (sender.hasPermission(ADMIN)) {
                    msg.sendList(sender, "help.admin");
                }
                return;
            }
            case "list" -> {
                list(sender);
                return;
            }
            case "leave" -> {
                if (!plugin.games().leave(player(sender))) {
                    throw new Failure("errors.not-in-game");
                }
                return;
            }
            case "join" -> {
                join(player(sender), args.length > 1 ? args[1] : null);
                return;
            }
            case "reload" -> {
                plugin.reload();
                msg.send(sender, "admin.reloaded");
                return;
            }
            case "create" -> {
                create(player(sender), arg(args, 1, "usage.create"));
                return;
            }
            default -> {
            }
        }
        Arena arena = arena(arg(args, 1, "usage." + sub));
        Game game = plugin.games().existing(arena);
        if (EDITS.contains(sub) && game != null && game.busy()) {
            throw new Failure("errors.game-running", Messages.p("arena", arena.id()));
        }
        TagResolver id = Messages.p("arena", arena.id());
        switch (sub) {
            case "delete" -> {
                plugin.games().close(arena);
                plugin.arenas().delete(arena);
                msg.send(sender, "admin.deleted", id);
                return;
            }
            case "setlobby" -> {
                arena.lobby(here(player(sender), arena));
                msg.send(sender, "admin.lobby-set", id);
            }
            case "sethunterspawn" -> {
                arena.hunterSpawn(here(player(sender), arena));
                msg.send(sender, "admin.hunter-spawn-set", id);
            }
            case "addspawn" -> {
                arena.propSpawns().add(here(player(sender), arena));
                msg.send(sender, "admin.spawn-added", id, Messages.p("index", arena.propSpawns().size()));
            }
            case "clearspawns" -> {
                arena.propSpawns().clear();
                msg.send(sender, "admin.spawns-cleared", id);
            }
            case "pos1", "pos2" -> {
                arena.corner(sub.equals("pos1") ? 1 : 2, here(player(sender), arena));
                msg.send(sender, "admin.corner-set", id, Messages.p("corner", sub.substring(3)));
            }
            case "clearregion" -> {
                arena.clearRegion();
                msg.send(sender, "admin.region-cleared", id);
            }
            case "addblock" -> {
                Material material = args.length > 2 ? material(args[2]) : pointed(sender);
                if (arena.blocks().contains(material)) {
                    throw new Failure("errors.block-exists", id, block(material));
                }
                if (arena.blocks().size() >= 54) {
                    throw new Failure("errors.too-many-blocks", id);
                }
                arena.blocks().add(material);
                msg.send(sender, "admin.block-added", id, block(material), Messages.p("count", arena.blocks().size()));
            }
            case "removeblock" -> {
                Material material = material(arg(args, 2, "usage.removeblock"));
                if (!arena.blocks().remove(material)) {
                    throw new Failure("errors.block-missing", id, block(material));
                }
                msg.send(sender, "admin.block-removed", id, block(material));
            }
            case "blocks" -> {
                showBlocks(sender, arena);
                return;
            }
            case "set" -> set(sender, arena, args);
            case "enable" -> {
                List<String> problems = arena.problems();
                if (!problems.isEmpty()) {
                    throw new Failure("errors.arena-not-ready", id, Messages.p("problems", plugin.games().describe(problems)));
                }
                arena.enabled(true);
                msg.send(sender, "admin.enabled", id);
            }
            case "disable" -> {
                arena.enabled(false);
                plugin.games().close(arena);
                msg.send(sender, "admin.disabled", id);
            }
            case "start" -> {
                Game running = plugin.games().existing(arena);
                if (running == null || !running.forceStart()) {
                    throw new Failure("errors.cannot-start", id);
                }
                msg.send(sender, "admin.started", id);
                return;
            }
            case "stop" -> {
                Game running = plugin.games().existing(arena);
                if (running == null) {
                    throw new Failure("errors.nothing-to-stop", id);
                }
                running.stop();
                msg.send(sender, "admin.stopped", id);
                return;
            }
            case "info" -> {
                info(sender, arena);
                return;
            }
            default -> {
                return;
            }
        }
        plugin.arenas().save(arena);
    }

    private void join(Player player, String name) {
        Arena arena;
        if (name == null) {
            List<Arena> open = new ArrayList<>();
            for (Arena candidate : plugin.arenas().all()) {
                if (candidate.enabled()) {
                    open.add(candidate);
                }
            }
            if (open.isEmpty()) {
                throw new Failure("errors.no-arenas");
            }
            arena = open.getFirst();
            for (Arena candidate : open) {
                Game game = plugin.games().existing(candidate);
                if (game != null && !game.busy() && game.size() < game.settings().maxPlayers()) {
                    Game best = plugin.games().existing(arena);
                    if (best == null || best.busy() || game.size() > best.size()) {
                        arena = candidate;
                    }
                }
            }
        } else {
            arena = arena(name);
        }
        plugin.games().join(player, arena);
    }

    private void create(Player player, String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (!ArenaStore.validId(id)) {
            throw new Failure("errors.bad-id");
        }
        if (plugin.arenas().get(id) != null) {
            throw new Failure("errors.arena-exists", Messages.p("arena", id));
        }
        plugin.arenas().create(id, player.getWorld().getName());
        plugin.msg().send(player, "admin.created", Messages.p("arena", id), Messages.p("world", player.getWorld().getName()));
    }

    private void set(CommandSender sender, Arena arena, String[] args) {
        String key = arg(args, 2, "usage.set").toLowerCase(Locale.ROOT);
        if (!ArenaSettings.KEYS.contains(key)) {
            throw new Failure("errors.unknown-setting", Messages.p("key", key), Messages.p("keys", String.join(", ", ArenaSettings.KEYS)));
        }
        TagResolver id = Messages.p("arena", arena.id());
        if (args.length < 4) {
            arena.overrides().remove(key);
            plugin.msg().send(sender, "admin.setting-reset", id, Messages.p("key", key), Messages.p("value", String.valueOf(plugin.defaults().get(key))));
            return;
        }
        Object value = ArenaSettings.parse(plugin.defaults(), key, args[3]);
        if (value == null) {
            throw new Failure("errors.bad-value", Messages.p("key", key), Messages.p("value", args[3]));
        }
        arena.overrides().put(key, value);
        plugin.msg().send(sender, "admin.setting-set", id, Messages.p("key", key), Messages.p("value", String.valueOf(value)));
    }

    private void list(CommandSender sender) {
        Messages msg = plugin.msg();
        if (plugin.arenas().all().isEmpty()) {
            throw new Failure("errors.no-arenas");
        }
        msg.send(sender, "list.header");
        for (Arena arena : plugin.arenas().all()) {
            if (!arena.enabled() && !sender.hasPermission(ADMIN)) {
                continue;
            }
            Game game = plugin.games().existing(arena);
            String state = !arena.enabled() ? msg.raw("phase.disabled") : game == null ? msg.raw("phase.waiting") : game.phaseName();
            int max = game == null ? arena.settings(plugin.defaults()).maxPlayers() : game.settings().maxPlayers();
            msg.send(sender, "list.entry", Messages.p("arena", arena.id()), Messages.p("state", state),
                    Messages.p("count", game == null ? 0 : game.size()), Messages.p("max", max));
        }
    }

    private void info(CommandSender sender, Arena arena) {
        Messages msg = plugin.msg();
        ArenaSettings s = arena.settings(plugin.defaults());
        String none = msg.raw("admin.info-none");
        List<String> problems = arena.problems();
        msg.sendList(sender, "admin.info",
                Messages.p("arena", arena.id()),
                Messages.p("world", arena.worldName() == null ? none : arena.worldName()),
                Messages.p("enabled", msg.raw(arena.enabled() ? "admin.yes" : "admin.no")),
                Messages.p("lobby", arena.lobby() == null ? none : arena.lobby().describe()),
                Messages.p("hunter", arena.hunterSpawn() == null ? none : arena.hunterSpawn().describe()),
                Messages.p("spawns", arena.propSpawns().size()),
                Messages.p("region", arena.region() == null ? none : describe(arena)),
                Messages.p("blocks", arena.blocks().size()),
                Messages.p("players", s.minPlayers() + "-" + s.maxPlayers()),
                Messages.p("hunters", s.minHunters() + "-" + s.maxHunters()),
                Messages.p("times", s.hideSeconds() + " / " + s.seekSeconds()),
                Messages.p("overrides", arena.overrides().isEmpty() ? none : arena.overrides().toString()),
                Messages.p("problems", problems.isEmpty() ? none : plugin.games().describe(problems)));
    }

    private static String describe(Arena arena) {
        var box = arena.region();
        return (int) box.getWidthX() + "x" + (int) box.getHeight() + "x" + (int) box.getWidthZ();
    }

    private void showBlocks(CommandSender sender, Arena arena) {
        if (arena.blocks().isEmpty()) {
            throw new Failure("errors.no-blocks", Messages.p("arena", arena.id()));
        }
        Component line = Component.empty();
        boolean first = true;
        for (Material material : arena.blocks()) {
            if (!first) {
                line = line.append(Component.text(", "));
            }
            first = false;
            line = line.append(Component.translatable(material.translationKey()))
                    .append(Component.text(" (" + material.getKey().getKey() + ")"));
        }
        plugin.msg().send(sender, "admin.blocks", Messages.p("arena", arena.id()), Messages.p("count", arena.blocks().size()), Messages.c("list", line));
    }

    private Pos here(Player player, Arena arena) {
        if (!player.getWorld().getName().equals(arena.worldName())) {
            throw new Failure("errors.wrong-world", Messages.p("arena", arena.id()), Messages.p("world", String.valueOf(arena.worldName())));
        }
        return Pos.of(player.getLocation());
    }

    private Material pointed(CommandSender sender) {
        Player player = player(sender);
        Block target = player.getTargetBlockExact(6);
        if (target != null && Arena.usable(target.getType())) {
            return target.getType();
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (Arena.usable(hand.getType())) {
            return hand.getType();
        }
        throw new Failure("errors.no-target-block");
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name.toLowerCase(Locale.ROOT));
        if (!Arena.usable(material)) {
            throw new Failure("errors.unknown-block", Messages.p("block", name));
        }
        return material;
    }

    private Arena arena(String name) {
        Arena arena = plugin.arenas().get(name);
        if (arena == null) {
            throw new Failure("errors.unknown-arena", Messages.p("arena", name));
        }
        return arena;
    }

    private static TagResolver block(Material material) {
        return Messages.c("block", Component.translatable(material.translationKey()));
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new Failure("errors.only-players");
    }

    private static String arg(String[] args, int index, String usage) {
        if (index >= args.length) {
            throw new Failure("errors.usage", Messages.p("usage", usageOf(usage)));
        }
        return args[index];
    }

    private static String usageOf(String key) {
        String sub = key.substring("usage.".length());
        return switch (sub) {
            case "set" -> "/ph set <arena> <key> [value]";
            case "addblock" -> "/ph addblock <arena> [block]";
            case "removeblock" -> "/ph removeblock <arena> <block>";
            default -> "/ph " + sub + " <arena>";
        };
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = sender.hasPermission(ADMIN);
        List<String> options = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        int position = Math.max(0, args.length - 1);
        if (args.length <= 1) {
            options.addAll(PLAYER_SUBS);
            if (admin) {
                options.addAll(ADMIN_SUBS);
            }
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (position == 1 && (sub.equals("join") || (admin && ADMIN_SUBS.contains(sub) && !NO_ARENA.contains(sub)))) {
                for (Arena arena : plugin.arenas().all()) {
                    if (admin || arena.enabled()) {
                        options.add(arena.id());
                    }
                }
            } else if (admin && position == 2 && sub.equals("set")) {
                options.addAll(ArenaSettings.KEYS);
            } else if (admin && position == 2 && sub.equals("removeblock")) {
                Arena arena = plugin.arenas().get(args[1]);
                if (arena != null) {
                    for (Material material : arena.blocks()) {
                        options.add(material.getKey().getKey());
                    }
                }
            } else if (admin && position == 2 && sub.equals("addblock")) {
                for (Material material : Material.values()) {
                    String key = material.getKey().getKey();
                    if (key.startsWith(last) && !material.isLegacy() && Arena.usable(material)) {
                        options.add(key);
                        if (options.size() >= 60) {
                            break;
                        }
                    }
                }
            } else if (admin && position == 3 && sub.equals("set")) {
                Object sample = plugin.defaults().get(args[2].toLowerCase(Locale.ROOT));
                if (sample instanceof Boolean) {
                    options.addAll(List.of("true", "false"));
                } else if (sample != null) {
                    options.add(String.valueOf(sample));
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(option);
            }
        }
        return out;
    }
}
