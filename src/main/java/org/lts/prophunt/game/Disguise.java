package org.lts.prophunt.game;

import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.lts.prophunt.logic.Facing;
import org.lts.prophunt.logic.HitPicker;

final class Disguise {

    private final Plugin plugin;
    private final Player owner;
    private static final double SNAP_DISTANCE_SQUARED = 4.0;

    private BlockData block;
    private boolean copied;
    private BlockDisplay display;
    private Block cell;
    private BlockData placed;
    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;

    Disguise(Plugin plugin, Player owner, BlockData block) {
        this.plugin = plugin;
        this.owner = owner;
        this.block = block;
    }

    BlockData block() {
        return block;
    }

    boolean solid() {
        return cell != null;
    }

    Block cell() {
        return cell;
    }

    void spawn() {
        Location at = displayLocation(owner.getLocation());
        display = owner.getWorld().spawn(at, BlockDisplay.class, entity -> {
            entity.setBlock(block);
            entity.setPersistent(false);
            entity.setTeleportDuration(2);
        });
        lastX = Double.NaN;
    }

    void follow() {
        if (solid()) {
            return;
        }
        if (display == null || !display.isValid()) {
            spawn();
            return;
        }
        Location at = owner.getLocation();
        if (at.getX() == lastX && at.getY() == lastY && at.getZ() == lastZ) {
            return;
        }
        double dx = at.getX() - lastX;
        double dy = at.getY() - lastY;
        double dz = at.getZ() - lastZ;
        boolean jump = Double.isNaN(lastX) || dx * dx + dy * dy + dz * dz > SNAP_DISTANCE_SQUARED;
        lastX = at.getX();
        lastY = at.getY();
        lastZ = at.getZ();
        display.setTeleportDuration(jump ? 0 : 2);
        display.teleport(displayLocation(at));
    }

    void change(BlockData data, boolean copied, Collection<Player> viewers) {
        this.block = data;
        this.copied = copied;
        if (display != null) {
            display.setBlock(data);
        }
        if (solid()) {
            placed = placement();
            if (display != null) {
                display.setBlock(placed);
            }
            send(viewers);
        }
    }

    void solidify(Block target, Collection<Player> viewers) {
        cell = target;
        placed = placement();
        if (display != null) {
            display.setBlock(placed);
            display.setTeleportDuration(0);
            display.teleport(target.getLocation());
            for (Player viewer : viewers) {
                viewer.hideEntity(plugin, display);
            }
        }
        send(viewers);
    }

    void melt(Collection<Player> viewers) {
        if (cell == null) {
            return;
        }
        Block old = cell;
        cell = null;
        placed = null;
        for (Player viewer : viewers) {
            viewer.sendBlockChange(old.getLocation(), old.getBlockData());
        }
        if (display != null) {
            display.setBlock(block);
            display.setTeleportDuration(2);
            display.teleport(displayLocation(owner.getLocation()));
            for (Player viewer : viewers) {
                viewer.showEntity(plugin, display);
            }
        }
        lastX = Double.NaN;
    }

    void send(Collection<Player> viewers) {
        if (cell == null) {
            return;
        }
        Location at = cell.getLocation();
        for (Player viewer : viewers) {
            if (viewer.getWorld().equals(at.getWorld())) {
                viewer.sendBlockChange(at, placed);
            }
        }
    }

    void remove(Collection<Player> viewers) {
        melt(viewers);
        if (display != null) {
            display.remove();
            display = null;
        }
    }

    BoundingBox box() {
        if (cell != null) {
            return HitPicker.cell(cell.getX(), cell.getY(), cell.getZ());
        }
        Location at = owner.getLocation();
        return HitPicker.around(at.getX(), at.getY(), at.getZ());
    }

    Location center() {
        if (cell != null) {
            return cell.getLocation().add(0.5, 0.5, 0.5);
        }
        return owner.getLocation().add(0, 0.5, 0);
    }

    private static Location displayLocation(Location at) {
        Location out = at.clone().add(-0.5, 0, -0.5);
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private BlockData placement() {
        return copied ? block.clone() : oriented(block, owner.getLocation().getYaw());
    }

    static BlockData oriented(BlockData data, float yaw) {
        BlockData out = data.clone();
        if (out instanceof Directional directional) {
            if (directional.getFaces().contains(BlockFace.UP) && !(out instanceof Stairs)) {
                return out;
            }
            BlockFace look = Facing.horizontal(yaw);
            BlockFace face = out instanceof Stairs ? look : look.getOppositeFace();
            if (directional.getFaces().contains(face)) {
                directional.setFacing(face);
            }
        } else if (out instanceof Rotatable rotatable) {
            rotatable.setRotation(Facing.rotation(yaw + 180f));
        }
        return out;
    }
}
