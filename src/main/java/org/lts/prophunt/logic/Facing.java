package org.lts.prophunt.logic;

import org.bukkit.block.BlockFace;

public final class Facing {

    private static final BlockFace[] CARDINAL = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};
    private static final BlockFace[] SIXTEEN = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST};

    private Facing() {
    }

    public static BlockFace horizontal(float yaw) {
        return CARDINAL[Math.floorMod(Math.round(yaw / 90f), 4)];
    }

    public static BlockFace rotation(float yaw) {
        return SIXTEEN[Math.floorMod(Math.round(yaw / 22.5f), 16)];
    }
}
