package org.lts.prophunt.game;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.lts.prophunt.PropHunt;
import org.lts.prophunt.core.Tools.Tool;

public final class GameListener implements Listener {

    private final PropHunt plugin;
    private final GameManager games;

    public GameListener(PropHunt plugin, GameManager games) {
        this.plugin = plugin;
        this.games = games;
    }

    private Game game(Entity entity) {
        return entity instanceof Player player ? games.of(player) : null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.snapshots().recover(event.getPlayer());
        games.welcome(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        games.leave(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Game game = games.of(event.getPlayer());
        if (game != null && !event.getPlayer().getWorld().equals(game.arena().world())) {
            games.leave(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Game game = games.of(event.getPlayer());
        if (game != null && game.frozen(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                event.setTo(back);
            }
        }
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Game game = games.of(event.getPlayer());
        if (game != null) {
            game.swing(event.getPlayer(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        Game game = games.of(event.getPlayer());
        if (game != null) {
            game.dropped(event.getPlayer(), Bukkit.getCurrentTick());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Game game = games.of(player);
        if (game == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Tool tool = plugin.tools().of(event.getItem());
        if (tool == null) {
            return;
        }
        if (tool == Tool.LEAVE) {
            games.leave(player);
            return;
        }
        game.useTool(player, tool, event.getAction() == Action.RIGHT_CLICK_BLOCK ? event.getClickedBlock() : null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Game game = games.of(event.getPlayer());
        if (game == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            Tool tool = plugin.tools().of(event.getPlayer().getInventory().getItemInMainHand());
            if (tool == Tool.LEAVE) {
                games.leave(event.getPlayer());
            } else if (tool != null) {
                game.useTool(event.getPlayer(), tool, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BlockMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
                Game game = games.of(player);
                if (game != null) {
                    game.menuPick(player, menu.at(event.getSlot()));
                }
            }
            return;
        }
        if (game(event.getWhoClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BlockMenu || game(event.getWhoClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (games.of(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (game(event.getEntity()) != null) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                event.getEntity().setFireTicks(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFood(FoodLevelChangeEvent event) {
        if (game(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRegain(EntityRegainHealthEvent event) {
        if (game(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent event) {
        if (game(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (games.of(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (games.of(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (event.isFlying() && games.of(player) != null && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Game game = games.of(event.getPlayer());
        if (game == null) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        game.died(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Game game = games.of(event.getPlayer());
        if (game != null) {
            Location at = game.respawn(event.getPlayer());
            if (at != null) {
                event.setRespawnLocation(at);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (games.of(player) == null || player.hasPermission("prophunt.bypass.commands")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        List<String> allowed = plugin.allowedCommands();
        if (!allowed.contains(label)) {
            event.setCancelled(true);
            plugin.msg().send(player, "errors.command-blocked");
        }
    }
}
