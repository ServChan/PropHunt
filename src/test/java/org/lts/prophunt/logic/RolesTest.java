package org.lts.prophunt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RolesTest {

    @Test
    void oneToThreeHuntersByDefault() {
        assertEquals(0, Roles.hunterCount(1, 1, 3, 4));
        assertEquals(1, Roles.hunterCount(2, 1, 3, 4));
        assertEquals(1, Roles.hunterCount(4, 1, 3, 4));
        assertEquals(2, Roles.hunterCount(5, 1, 3, 4));
        assertEquals(3, Roles.hunterCount(12, 1, 3, 4));
        assertEquals(3, Roles.hunterCount(40, 1, 3, 4));
    }

    @Test
    void alwaysLeavesAProp() {
        assertEquals(1, Roles.hunterCount(2, 3, 3, 1));
        assertEquals(2, Roles.hunterCount(3, 3, 3, 1));
    }

    @Test
    void brokenLimitsStillGiveAHunter() {
        assertEquals(1, Roles.hunterCount(6, 0, 0, 0));
        assertEquals(2, Roles.hunterCount(6, 2, 1, 100));
    }

    @Test
    void previousHuntersWaitTheirTurn() {
        List<String> players = List.of("a", "b", "c", "d");
        Map<String, Integer> history = new HashMap<>(Map.of("a", 2, "b", 1));
        for (int seed = 0; seed < 50; seed++) {
            List<String> picked = Roles.pickHunters(players, history, 2, new Random(seed));
            assertEquals(2, picked.size());
            assertTrue(picked.contains("c"));
            assertTrue(picked.contains("d"));
        }
    }

    @Test
    void tiesAreRandom() {
        List<String> players = List.of("a", "b", "c", "d");
        boolean sawA = false;
        boolean sawD = false;
        for (int seed = 0; seed < 50; seed++) {
            List<String> picked = Roles.pickHunters(players, Map.of(), 1, new Random(seed));
            sawA |= picked.contains("a");
            sawD |= picked.contains("d");
        }
        assertTrue(sawA && sawD);
        assertEquals(players.size(), Roles.pickHunters(players, Map.of(), 9, new Random(1)).size());
    }
}
