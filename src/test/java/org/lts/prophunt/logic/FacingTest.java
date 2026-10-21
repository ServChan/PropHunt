package org.lts.prophunt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class FacingTest {

    @Test
    void cardinalFollowsMinecraftYaw() {
        assertEquals(BlockFace.SOUTH, Facing.horizontal(0));
        assertEquals(BlockFace.WEST, Facing.horizontal(90));
        assertEquals(BlockFace.NORTH, Facing.horizontal(180));
        assertEquals(BlockFace.NORTH, Facing.horizontal(-180));
        assertEquals(BlockFace.EAST, Facing.horizontal(-90));
        assertEquals(BlockFace.EAST, Facing.horizontal(270));
        assertEquals(BlockFace.SOUTH, Facing.horizontal(44));
        assertEquals(BlockFace.WEST, Facing.horizontal(46));
        assertEquals(BlockFace.SOUTH, Facing.horizontal(-719));
    }

    @Test
    void sixteenWayRotation() {
        assertEquals(BlockFace.SOUTH, Facing.rotation(0));
        assertEquals(BlockFace.SOUTH_WEST, Facing.rotation(45));
        assertEquals(BlockFace.NORTH, Facing.rotation(180));
        assertEquals(BlockFace.SOUTH_SOUTH_EAST, Facing.rotation(-22.5f));
    }
}
