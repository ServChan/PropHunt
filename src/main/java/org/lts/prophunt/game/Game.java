package org.lts.prophunt.game;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.lts.prophunt.PropHunt;
import org.lts.prophunt.arena.Arena;
import org.lts.prophunt.arena.ArenaSettings;
import org.lts.prophunt.core.Messages;
import org.lts.prophunt.core.Pos;
import org.lts.prophunt.core.Tools.Tool;
import org.lts.prophunt.game.GamePlayer.Role;
import org.lts.prophunt.logic.HitPicker;
import org.lts.prophunt.logic.Roles;
import org.lts.prophunt.logic.RoundClock;

public final class Game {

    public enum Phase {
        WAITING,
        STARTING,
        HIDING,
        SEEKING,
        ENDING
    }

    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(600));

    private final PropHunt plugin;
    private final Messages msg;
    private final Arena arena;
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final Map<UUID, Integer> hunterHistory = new HashMap<>();
    private final Random random = new Random();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private ArenaSettings settings;
    private Phase phase = Phase.WAITING;
    private World world;
    private int countdown;
    private boolean forced;
    private RoundClock clock = new RoundClock(0);
    private int autoTauntIn;
    private boolean propsWon;

    Game(PropHunt plugin, Arena arena) {
        this.plugin = plugin;
        this.msg = plugin.msg();
        this.arena = arena;
        this.settings = arena.settings(plugin.defaults());
    }

    public Arena arena() {
        return arena;
    }

    public Phase phase() {
        return phase;
    }

    public int size() {
        return players.size();
    }

    public ArenaSettings settings() {
        return settings;
    }

    public boolean running() {
        return phase == Phase.HIDING || phase == Phase.SEEKING;
    }

    public boolean busy() {
        return running() || phase == Phase.ENDING;
    }

    boolean contains(UUID id) {
        return players.containsKey(id);
    }

    boolean join(Player player) {
        if (players.size() >= settings.maxPlayers()) {
            msg.send(player, "errors.game-full", Messages.p("arena", arena.id()));
            return false;
        }
        World target = arena.world();
        if (target == null) {
            msg.send(player, "errors.arena-not-ready", Messages.p("arena", arena.id()), Messages.p("problems", msg.raw("problems.world")));
            return false;
        }
        if (players.isEmpty()) {
            world = target;
            settings = arena.settings(plugin.defaults());
        }
        plugin.snapshots().capture(player);
        GamePlayer gp = new GamePlayer(player.getUniqueId(), player.getName());
        players.put(gp.id, gp);
        prepare(player);
        player.showBossBar(bar);
        if (busy()) {
            gp.role = Role.SPECTATOR;
            spectate(player);
            msg.send(player, "game.joined-late", Messages.p("arena", arena.id()));
        } else {
            toLobby(player);
            msg.send(player, "game.joined", Messages.p("arena", arena.id()));
        }
        broadcast("game.join-broadcast", Messages.p("player", player.getName()),
                Messages.p("count", players.size()), Messages.p("max", settings.maxPlayers()));
        updateBar();
        return true;
    }

    void leave(Player player) {
        GamePlayer gp = players.remove(player.getUniqueId());
        if (gp == null) {
            return;
        }
        cleanup(player, gp);
        forgetFakeBlocks(player);
        plugin.snapshots().restore(player);
        msg.send(player, "game.left", Messages.p("arena", arena.id()));
        broadcast("game.leave-broadcast", Messages.p("player", player.getName()),
                Messages.p("count", players.size()), Messages.p("max", settings.maxPlayers()));
        if (players.isEmpty()) {
            phase = Phase.WAITING;
            forced = false;
        } else if (running()) {
            checkEnd();
        }
        updateBar();
    }

    void close() {
        for (GamePlayer gp : new ArrayList<>(players.values())) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player != null) {
                cleanup(player, gp);
                forgetFakeBlocks(player);
                plugin.snapshots().restore(player);
            }
        }
        players.clear();
        phase = Phase.WAITING;
    }

    public boolean forceStart() {
        if (busy() || players.size() < 2) {
            return false;
        }
        forced = true;
        countdown = phase == Phase.STARTING ? Math.min(countdown, 5) : 5;
        phase = Phase.STARTING;
        updateBar();
        return true;
    }

    public void stop() {
        if (busy()) {
            broadcast("game.stopped");
            revealAll();
            backToLobby();
        } else if (phase == Phase.STARTING) {
            phase = Phase.WAITING;
            forced = false;
            updateBar();
        }
    }

    void tick(int now) {
        if (!running()) {
            return;
        }
        boolean slow = now % 10 == 0;
        BoundingBox region = slow ? arena.region() : null;
        for (GamePlayer gp : players.values()) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player == null) {
                continue;
            }
            if (gp.role == Role.PROP) {
                tickProp(player, gp);
            }
            if (slow) {
                keepInside(player, gp, region);
                statusBar(player, gp, now);
            }
        }
    }

    void second() {
        switch (phase) {
            case WAITING -> {
                if (players.size() >= settings.minPlayers() && arena.problems().isEmpty()) {
                    phase = Phase.STARTING;
                    countdown = settings.lobbyCountdown();
                    broadcast("game.countdown", Messages.p("seconds", countdown));
                }
            }
            case STARTING -> countdownSecond();
            case HIDING -> {
                boolean done = clock.tick();
                if (!done && clock.remaining() <= 5) {
                    for (Player player : online(Role.HUNTER)) {
                        play(player, "block.note_block.hat", 1f);
                    }
                }
                if (done) {
                    seek();
                }
            }
            case SEEKING -> {
                for (GamePlayer gp : players.values()) {
                    Player player = Bukkit.getPlayer(gp.id);
                    if (gp.role == Role.PROP && gp.disguise != null && player != null) {
                        gp.disguise.send(viewers(player));
                    }
                }
                if (settings.autoTaunt() > 0 && --autoTauntIn <= 0) {
                    autoTauntIn = settings.autoTaunt();
                    for (GamePlayer gp : players.values()) {
                        if (gp.role == Role.PROP) {
                            taunt(gp);
                        }
                    }
                    broadcast("game.auto-taunt");
                }
                if (clock.tick()) {
                    finish(true);
                }
            }
            case ENDING -> {
                if (clock.tick()) {
                    backToLobby();
                }
            }
        }
        updateBar();
    }

    private void countdownSecond() {
        if (players.size() < 2 || (!forced && players.size() < settings.minPlayers())) {
            phase = Phase.WAITING;
            forced = false;
            broadcast("game.countdown-cancelled");
            return;
        }
        if (players.size() >= settings.maxPlayers() && countdown > 10) {
            countdown = 10;
        }
        countdown--;
        if (countdown <= 0) {
            begin();
            return;
        }
        if (countdown <= 5 || countdown % 10 == 0) {
            broadcast("game.countdown", Messages.p("seconds", countdown));
            for (Player player : online(null)) {
                play(player, "block.note_block.pling", countdown <= 5 ? 1.4f : 1f);
            }
        }
    }

    private void begin() {
        settings = arena.settings(plugin.defaults());
        world = arena.world();
        if (world == null || !arena.problems().isEmpty()) {
            broadcast("errors.arena-not-ready", Messages.p("arena", arena.id()), Messages.p("problems", problemList()));
            phase = Phase.WAITING;
            return;
        }
        List<UUID> roster = new ArrayList<>();
        for (GamePlayer gp : players.values()) {
            if (Bukkit.getPlayer(gp.id) != null) {
                roster.add(gp.id);
            }
        }
        int hunterCount = Roles.hunterCount(roster.size(), settings.minHunters(), settings.maxHunters(), settings.playersPerHunter());
        if (hunterCount == 0) {
            phase = Phase.WAITING;
            return;
        }
        List<UUID> hunters = Roles.pickHunters(roster, hunterHistory, hunterCount, random);
        List<Pos> spawns = new ArrayList<>(arena.propSpawns());
        Collections.shuffle(spawns, random);
        int spawnIndex = 0;
        List<String> hunterNames = new ArrayList<>();
        for (GamePlayer gp : players.values()) {
            gp.resetRound();
            Player player = Bukkit.getPlayer(gp.id);
            if (player == null) {
                continue;
            }
            prepare(player);
            if (hunters.contains(gp.id)) {
                gp.role = Role.HUNTER;
                hunterHistory.merge(gp.id, 1, Integer::sum);
                hunterNames.add(gp.name);
                player.teleport(arena.hunterSpawn().at(world));
                hunterKit(player);
                if (settings.hideSeconds() > 0) {
                    int ticks = settings.hideSeconds() * 20 + 40;
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, false));
                }
                title(player, "titles.hunter", "titles.hunter-sub", Messages.p("seconds", settings.hideSeconds()));
            } else {
                gp.role = Role.PROP;
                player.teleport(spawns.get(spawnIndex++ % spawns.size()).at(world));
                becomeProp(player, gp);
                title(player, "titles.prop", "titles.prop-sub", Messages.p("seconds", settings.hideSeconds()));
            }
            gp.lastInside = player.getLocation();
        }
        broadcast("game.started", Messages.p("hunters", String.join(", ", hunterNames)),
                Messages.p("props", players.size() - hunterNames.size()), Messages.p("seconds", settings.hideSeconds()));
        phase = Phase.HIDING;
        clock = new RoundClock(settings.hideSeconds());
        propsWon = false;
        if (settings.hideSeconds() == 0) {
            seek();
        }
    }

    private void seek() {
        phase = Phase.SEEKING;
        clock = new RoundClock(settings.seekSeconds());
        autoTauntIn = settings.autoTaunt();
        for (GamePlayer gp : players.values()) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player == null) {
                continue;
            }
            if (gp.role == Role.HUNTER) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                title(player, "titles.seek", "titles.seek-sub", Messages.p("seconds", settings.seekSeconds()));
                play(player, "entity.wither.spawn", 1.2f);
            } else {
                play(player, "entity.elder_guardian.curse", 1.4f);
            }
        }
        broadcast("game.seek-started", Messages.p("time", RoundClock.format(settings.seekSeconds())));
    }

    private void finish(boolean propsWin) {
        propsWon = propsWin;
        phase = Phase.ENDING;
        clock = new RoundClock(settings.endSeconds());
        List<String> survivors = new ArrayList<>();
        List<String> hunters = new ArrayList<>();
        for (GamePlayer gp : players.values()) {
            if (gp.role == Role.PROP) {
                survivors.add(gp.name);
            } else if (gp.role == Role.HUNTER) {
                hunters.add(gp.found > 0 ? gp.name + " (" + gp.found + ")" : gp.name);
            }
        }
        revealAll();
        for (GamePlayer gp : players.values()) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player == null) {
                continue;
            }
            player.getInventory().clear();
            player.getInventory().setItem(8, plugin.tools().make(Tool.LEAVE));
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            title(player, propsWin ? "titles.props-win" : "titles.hunters-win", propsWin ? "titles.props-win-sub" : "titles.hunters-win-sub");
            play(player, propsWin ? "ui.toast.challenge_complete" : "entity.player.levelup", 1f);
        }
        String none = msg.raw("game.nobody");
        sendAll("game.summary",
                Messages.c("winner", msg.get(propsWin ? "game.winner-props" : "game.winner-hunters")),
                Messages.p("hunters", hunters.isEmpty() ? none : String.join(", ", hunters)),
                Messages.p("survivors", survivors.isEmpty() ? none : String.join(", ", survivors)));
    }

    private void backToLobby() {
        phase = Phase.WAITING;
        forced = false;
        for (GamePlayer gp : players.values()) {
            gp.role = Role.WAITING;
            gp.resetRound();
            Player player = Bukkit.getPlayer(gp.id);
            if (player != null) {
                prepare(player);
                toLobby(player);
            }
        }
        updateBar();
    }

    private void checkEnd() {
        if (!running()) {
            return;
        }
        int props = 0;
        int hunters = 0;
        for (GamePlayer gp : players.values()) {
            if (gp.role == Role.PROP) {
                props++;
            } else if (gp.role == Role.HUNTER) {
                hunters++;
            }
        }
        if (props == 0) {
            finish(false);
        } else if (hunters == 0) {
            finish(true);
        }
    }

    private void tickProp(Player player, GamePlayer gp) {
        Disguise disguise = gp.disguise;
        if (disguise == null) {
            return;
        }
        Location at = player.getLocation();
        if (disguise.solid()) {
            if (gp.lockedAt == null || !at.getWorld().equals(gp.lockedAt.getWorld()) || at.distanceSquared(gp.lockedAt) > 0.09) {
                unlock(player, gp);
            }
            return;
        }
        disguise.follow();
        int still = gp.stillness.update(at.getX(), at.getY(), at.getZ());
        if (still == 0) {
            gp.lockRefused = false;
        }
        if (!gp.lockRefused && still >= lockTicks()) {
            tryLock(player, gp);
        }
    }

    private int lockTicks() {
        return (int) Math.round(settings.lockSeconds() * 20);
    }

    private void tryLock(Player player, GamePlayer gp) {
        Location at = player.getLocation();
        Block cell = at.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(at.getY() + 0.2), at.getBlockZ());
        boolean free = cell.getType().isAir() || (cell.isReplaceable() && !cell.isLiquid());
        BoundingBox region = arena.region();
        if (!free || takenBy(cell, gp) || (region != null && !region.contains(cell.getLocation().add(0.5, 0.5, 0.5).toVector()))) {
            gp.lockRefused = true;
            notice(player, "prop.cannot-lock");
            return;
        }
        BoundingBox box = HitPicker.cell(cell.getX(), cell.getY(), cell.getZ());
        for (Player other : viewers(player)) {
            if (other.getGameMode() != GameMode.SPECTATOR && other.getBoundingBox().overlaps(box)) {
                return;
            }
        }
        gp.lockedAt = at.clone();
        gp.disguise.solidify(cell, viewers(player));
        play(player, "block.wool.place", 0.8f);
        notice(player, "prop.locked");
    }

    private boolean takenBy(Block cell, GamePlayer self) {
        for (GamePlayer gp : players.values()) {
            if (gp != self && gp.disguise != null && gp.disguise.solid() && gp.disguise.cell().equals(cell)) {
                return true;
            }
        }
        return false;
    }

    private void unlock(Player player, GamePlayer gp) {
        gp.disguise.melt(viewers(player));
        gp.lockedAt = null;
        Location at = player.getLocation();
        gp.stillness.reset(at.getX(), at.getY(), at.getZ());
    }

    private void keepInside(Player player, GamePlayer gp, BoundingBox region) {
        if (gp.role != Role.PROP && gp.role != Role.HUNTER) {
            return;
        }
        Location at = player.getLocation();
        boolean fell = at.getY() < at.getWorld().getMinHeight() - 4;
        boolean outside = region != null && !region.contains(at.toVector());
        if (!fell && !outside) {
            gp.lastInside = at.clone();
            return;
        }
        Location back = gp.lastInside != null && !fell ? gp.lastInside : spawnOf(gp);
        player.setFallDistance(0);
        player.setVelocity(new Vector());
        player.teleport(back);
        notice(player, "game.out-of-bounds");
    }

    private Location spawnOf(GamePlayer gp) {
        if (gp.role == Role.PROP && !arena.propSpawns().isEmpty()) {
            return arena.propSpawns().get(random.nextInt(arena.propSpawns().size())).at(world);
        }
        Pos pos = gp.role == Role.WAITING ? arena.lobby() : arena.hunterSpawn();
        return pos.at(world);
    }

    private void notice(Player player, String key, TagResolver... resolvers) {
        msg.actionBar(player, key, resolvers);
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp != null) {
            gp.barHold = Bukkit.getCurrentTick() + 30;
        }
    }

    private void statusBar(Player player, GamePlayer gp, int now) {
        if (now < gp.barHold) {
            return;
        }
        if (gp.role == Role.PROP && gp.disguise != null) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlockMenu) {
                return;
            }
            if (gp.disguise.solid()) {
                msg.actionBar(player, "prop.bar-solid", block(gp.disguise.block().getMaterial()), cooldown("taunt", gp.tauntReady, now));
            } else if (gp.lockRefused) {
                msg.actionBar(player, "prop.cannot-lock");
            } else {
                int left = Math.max(0, lockTicks() - gp.stillness.ticks());
                msg.actionBar(player, "prop.bar-moving", block(gp.disguise.block().getMaterial()),
                        Messages.p("seconds", String.format(Locale.ROOT, "%.1f", left / 20.0)), cooldown("taunt", gp.tauntReady, now));
            }
        } else if (gp.role == Role.HUNTER && phase == Phase.SEEKING) {
            msg.actionBar(player, "hunter.bar", cooldown("whistle", gp.whistleReady, now));
        }
    }

    private TagResolver cooldown(String key, int readyAt, int now) {
        int left = (readyAt - now + 19) / 20;
        return Messages.c(key, left <= 0 ? msg.get("cooldown.ready") : msg.get("cooldown.wait", Messages.p("seconds", left)));
    }

    void swing(Player hunter, int now) {
        GamePlayer gp = players.get(hunter.getUniqueId());
        if (gp == null || phase != Phase.SEEKING || gp.role != Role.HUNTER || now < gp.hitReady || gp.dropTick == now) {
            return;
        }
        gp.hitReady = now + settings.hitCooldownTicks();
        Location eye = hunter.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = settings.hunterReach();
        RayTraceResult wall = eye.getWorld().rayTraceBlocks(eye, dir, reach, FluidCollisionMode.NEVER, true);
        double wallDistance = wall == null ? Double.MAX_VALUE : wall.getHitPosition().distance(eye.toVector());
        List<HitPicker.Target<GamePlayer>> targets = new ArrayList<>();
        for (GamePlayer other : players.values()) {
            if (other.role == Role.PROP && other.disguise != null && Bukkit.getPlayer(other.id) != null) {
                targets.add(new HitPicker.Target<>(other, other.disguise.box()));
            }
        }
        Optional<HitPicker.Hit<GamePlayer>> hit = HitPicker.pick(eye.toVector(), dir, reach, wallDistance, targets);
        if (hit.isPresent()) {
            hitProp(hunter, gp, hit.get().owner());
        } else if (wall != null && wall.getHitBlock() != null && settings.missDamage() > 0) {
            miss(hunter);
        }
    }

    private void hitProp(Player hunter, GamePlayer hunterData, GamePlayer target) {
        Player prop = Bukkit.getPlayer(target.id);
        if (prop == null) {
            return;
        }
        Disguise disguise = target.disguise;
        Location fx = disguise.center();
        fx.getWorld().spawnParticle(Particle.BLOCK, fx, 24, 0.3, 0.3, 0.3, disguise.block());
        playAt(fx, "entity.player.hurt", 1f);
        heal(hunter, settings.hunterHealOnHit());
        double health = prop.getHealth() - settings.hunterDamage();
        if (health <= 0.01) {
            found(prop, target, hunter, hunterData);
            return;
        }
        if (disguise.solid()) {
            unlock(prop, target);
        }
        prop.setHealth(health);
        prop.playHurtAnimation(hunter.getLocation().getYaw());
        Vector push = hunter.getLocation().getDirection().setY(0);
        if (push.lengthSquared() > 1.0E-4) {
            push.normalize().multiply(0.45);
        }
        prop.setVelocity(push.setY(0.32));
        notice(prop, "prop.hit", Messages.p("hearts", hearts(health)));
        notice(hunter, "hunter.hit", Messages.p("hearts", hearts(health)));
    }

    private void miss(Player hunter) {
        double health = hunter.getHealth() - settings.missDamage();
        if (health <= 0.01) {
            heal(hunter, 40);
            hunter.teleport(arena.hunterSpawn().at(world));
            hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
            hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4, false, false, false));
            msg.send(hunter, "hunter.exhausted");
            return;
        }
        hunter.setHealth(health);
        hunter.playHurtAnimation(hunter.getLocation().getYaw());
        notice(hunter, "hunter.miss", Messages.p("hearts", hearts(health)));
    }

    private void found(Player prop, GamePlayer gp, Player hunter, GamePlayer hunterData) {
        if (hunterData != null) {
            hunterData.found++;
        }
        Location at = gp.disguise != null ? gp.disguise.center() : prop.getLocation();
        removeDisguise(prop, gp);
        at.getWorld().spawnParticle(Particle.POOF, at, 20, 0.3, 0.4, 0.3, 0.02);
        playAt(at, "entity.generic.death", 1f);
        if (hunter != null) {
            broadcast("game.found", Messages.p("prop", gp.name), Messages.p("hunter", hunter.getName()));
        } else {
            broadcast("game.found-alone", Messages.p("prop", gp.name));
        }
        if (settings.foundBecomeHunters()) {
            gp.role = Role.HUNTER;
            prepare(prop);
            prop.teleport(arena.hunterSpawn().at(world));
            hunterKit(prop);
            title(prop, "titles.found", "titles.found-hunter-sub");
        } else {
            gp.role = Role.SPECTATOR;
            spectate(prop);
            title(prop, "titles.found", "titles.found-spectator-sub");
        }
        checkEnd();
    }

    void died(Player player) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp != null && gp.role == Role.PROP && running()) {
            found(player, gp, null, null);
        }
    }

    Location respawn(Player player) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp == null || world == null) {
            return null;
        }
        return spawnOf(gp);
    }

    void useTool(Player player, Tool tool, Block clicked) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        switch (tool) {
            case CHOOSE -> {
                if (gp.role != Role.PROP || !running() || gp.disguise == null) {
                    return;
                }
                if (clicked != null && arena.blocks().contains(clicked.getType())) {
                    choose(player, gp, clicked.getBlockData(), true);
                } else {
                    player.openInventory(new BlockMenu(msg, arena.blocks(), gp.disguise.block().getMaterial()).getInventory());
                }
            }
            case TAUNT -> {
                if (gp.role != Role.PROP || !running() || gp.disguise == null) {
                    return;
                }
                if (now < gp.tauntReady) {
                    notice(player, "prop.taunt-cooldown", Messages.p("seconds", (gp.tauntReady - now + 19) / 20));
                    return;
                }
                gp.tauntReady = now + settings.tauntCooldown() * 20;
                taunt(gp);
                gp.taunts++;
                if (phase == Phase.SEEKING) {
                    int cut = clock.shorten(settings.tauntTimeBonus(), settings.tauntFloor());
                    msg.send(player, cut > 0 ? "prop.taunted" : "prop.taunted-no-bonus", Messages.p("seconds", cut));
                } else {
                    msg.send(player, "prop.taunted-hiding");
                }
            }
            case WHISTLE -> {
                if (gp.role != Role.HUNTER || phase != Phase.SEEKING) {
                    return;
                }
                if (now < gp.whistleReady) {
                    notice(player, "hunter.whistle-cooldown", Messages.p("seconds", (gp.whistleReady - now + 19) / 20));
                    return;
                }
                gp.whistleReady = now + settings.whistleCooldown() * 20;
                play(player, "entity.breeze.whirl", 1.6f);
                for (GamePlayer other : players.values()) {
                    Player prop = Bukkit.getPlayer(other.id);
                    if (other.role == Role.PROP && prop != null) {
                        taunt(other);
                        notice(prop, "prop.whistled");
                    }
                }
                broadcast("hunter.whistled", Messages.p("player", player.getName()));
            }
            default -> {
            }
        }
    }

    void menuPick(Player player, Material material) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp != null && gp.role == Role.PROP && running() && material != null) {
            player.closeInventory();
            choose(player, gp, material.createBlockData(), false);
        }
    }

    private void choose(Player player, GamePlayer gp, BlockData data, boolean copied) {
        if (gp.disguise == null || gp.disguise.block().equals(data)) {
            return;
        }
        Material material = data.getMaterial();
        int now = Bukkit.getCurrentTick();
        if (phase == Phase.SEEKING) {
            if (now < gp.changeReady) {
                notice(player, "prop.change-cooldown", Messages.p("seconds", (gp.changeReady - now + 19) / 20));
                return;
            }
            gp.changeReady = now + settings.changeCooldown() * 20;
        }
        gp.disguise.change(data, copied, viewers(player));
        play(player, "entity.illusioner.mirror_move", 1.2f);
        msg.send(player, "prop.disguised", block(material));
    }

    private void taunt(GamePlayer gp) {
        Player player = Bukkit.getPlayer(gp.id);
        if (player == null || gp.disguise == null) {
            return;
        }
        Location at = gp.disguise.center();
        List<Key> sounds = plugin.tauntSounds();
        if (!sounds.isEmpty()) {
            Key key = sounds.get(random.nextInt(sounds.size()));
            at.getWorld().playSound(Sound.sound(key, Sound.Source.PLAYER, 2f, 0.8f + random.nextFloat() * 0.4f), at.getX(), at.getY(), at.getZ());
        }
        at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 0.8, 0), 5, 0.3, 0.2, 0.3, 1);
    }

    private void becomeProp(Player player, GamePlayer gp) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(settings.propScale());
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
        Material material = arena.blocks().get(random.nextInt(arena.blocks().size()));
        gp.disguise = new Disguise(plugin, player, material.createBlockData());
        gp.disguise.spawn();
        Location at = player.getLocation();
        gp.stillness.reset(at.getX(), at.getY(), at.getZ());
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(0, plugin.tools().make(Tool.CHOOSE));
        inventory.setItem(1, plugin.tools().make(Tool.TAUNT));
        inventory.setItem(8, plugin.tools().make(Tool.LEAVE));
        inventory.setHeldItemSlot(0);
        msg.send(player, "prop.disguised", block(material));
    }

    private void hunterKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(0, plugin.tools().make(Tool.SWORD));
        inventory.setItem(1, plugin.tools().make(Tool.WHISTLE));
        inventory.setItem(8, plugin.tools().make(Tool.LEAVE));
        inventory.setHeldItemSlot(0);
    }

    private void removeDisguise(Player player, GamePlayer gp) {
        if (gp.disguise != null) {
            gp.disguise.remove(viewers(player));
            gp.disguise = null;
        }
        gp.lockedAt = null;
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(1.0);
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    private void revealAll() {
        for (GamePlayer gp : players.values()) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player != null && gp.disguise != null) {
                Location at = gp.disguise.center();
                removeDisguise(player, gp);
                at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.3, 0.4, 0.3, 0);
            }
        }
    }

    private void cleanup(Player player, GamePlayer gp) {
        if (gp.disguise != null || gp.role == Role.PROP) {
            removeDisguise(player, gp);
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlockMenu) {
            player.closeInventory();
        }
        player.hideBossBar(bar);
    }

    private void forgetFakeBlocks(Player viewer) {
        for (GamePlayer gp : players.values()) {
            if (gp.disguise != null && gp.disguise.solid()) {
                Block cell = gp.disguise.cell();
                viewer.sendBlockChange(cell.getLocation(), cell.getBlockData());
            }
        }
    }

    void hideFrom(Player newcomer) {
        for (GamePlayer gp : players.values()) {
            Player prop = Bukkit.getPlayer(gp.id);
            if (gp.disguise != null && prop != null && !prop.equals(newcomer)) {
                newcomer.hidePlayer(plugin, prop);
            }
        }
    }

    boolean frozen(Player player) {
        GamePlayer gp = players.get(player.getUniqueId());
        return gp != null && phase == Phase.HIDING && gp.role == Role.HUNTER;
    }

    void dropped(Player player, int now) {
        GamePlayer gp = players.get(player.getUniqueId());
        if (gp != null) {
            gp.dropTick = now;
        }
    }

    private void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(false);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(1.0);
        }
        heal(player, 40);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setLevel(0);
        player.setExp(0);
    }

    private void toLobby(Player player) {
        player.teleport(arena.lobby().at(world));
        player.getInventory().setItem(8, plugin.tools().make(Tool.LEAVE));
    }

    private void spectate(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        Pos pos = arena.hunterSpawn() != null ? arena.hunterSpawn() : arena.lobby();
        player.teleport(pos.at(world));
    }

    private static void heal(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double limit = max == null ? 20.0 : max.getValue();
        player.setHealth(Math.min(limit, player.getHealth() + amount));
    }

    private static String hearts(double health) {
        double value = Math.ceil(health) / 2.0;
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private List<Player> viewers(Player self) {
        List<Player> out = new ArrayList<>(players.size());
        for (GamePlayer gp : players.values()) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player != null && !player.equals(self)) {
                out.add(player);
            }
        }
        return out;
    }

    private List<Player> online(Role role) {
        List<Player> out = new ArrayList<>();
        for (GamePlayer gp : players.values()) {
            Player player = Bukkit.getPlayer(gp.id);
            if (player != null && (role == null || gp.role == role)) {
                out.add(player);
            }
        }
        return out;
    }

    private TagResolver block(Material material) {
        return Messages.c("block", Component.translatable(material.translationKey()));
    }

    private String problemList() {
        List<String> out = new ArrayList<>();
        for (String problem : arena.problems()) {
            out.add(msg.raw("problems." + problem));
        }
        return String.join(", ", out);
    }

    private void broadcast(String key, TagResolver... resolvers) {
        for (Player player : online(null)) {
            msg.send(player, key, resolvers);
        }
    }

    private void sendAll(String key, TagResolver... resolvers) {
        for (Player player : online(null)) {
            msg.sendList(player, key, resolvers);
        }
    }

    private void title(Player player, String main, String sub, TagResolver... resolvers) {
        player.showTitle(Title.title(msg.get(main, resolvers), msg.get(sub, resolvers), TIMES));
    }

    private void play(Player player, String key, float pitch) {
        player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, pitch));
    }

    private void playAt(Location at, String key, float pitch) {
        at.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, pitch), at.getX(), at.getY(), at.getZ());
    }

    private void updateBar() {
        int props = 0;
        for (GamePlayer gp : players.values()) {
            if (gp.role == Role.PROP) {
                props++;
            }
        }
        switch (phase) {
            case WAITING -> {
                bar.name(msg.get("bar.waiting", Messages.p("arena", arena.id()), Messages.p("count", players.size()), Messages.p("min", settings.minPlayers())));
                bar.progress(Math.min(1f, players.size() / (float) Math.max(1, settings.minPlayers())));
                bar.color(BossBar.Color.YELLOW);
            }
            case STARTING -> {
                bar.name(msg.get("bar.starting", Messages.p("seconds", Math.max(0, countdown))));
                bar.progress(Math.max(0f, Math.min(1f, countdown / (float) Math.max(1, settings.lobbyCountdown()))));
                bar.color(BossBar.Color.GREEN);
            }
            case HIDING -> {
                bar.name(msg.get("bar.hiding", Messages.p("time", RoundClock.format(clock.remaining()))));
                bar.progress(clock.progress());
                bar.color(BossBar.Color.BLUE);
            }
            case SEEKING -> {
                bar.name(msg.get("bar.seeking", Messages.p("time", RoundClock.format(clock.remaining())), Messages.p("props", props)));
                bar.progress(clock.progress());
                bar.color(BossBar.Color.RED);
            }
            case ENDING -> {
                bar.name(msg.get(propsWon ? "bar.ending-props" : "bar.ending-hunters"));
                bar.progress(clock.progress());
                bar.color(BossBar.Color.WHITE);
            }
        }
    }

    public String phaseName() {
        return msg.raw("phase." + phase.name().toLowerCase(Locale.ROOT));
    }
}
