package org.lts.prophunt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StillnessTest {

    @Test
    void countsTicksWhileStandingStill() {
        Stillness still = new Stillness(0.1);
        assertEquals(0, still.update(0, 64, 0));
        assertEquals(1, still.update(0.02, 64, 0));
        assertEquals(2, still.update(0.05, 64, 0.03));
        assertEquals(0, still.update(0.5, 64, 0));
        assertEquals(1, still.update(0.5, 64, 0));
    }

    @Test
    void slowDriftDoesNotCount() {
        Stillness still = new Stillness(0.1);
        still.update(0, 64, 0);
        int last = 0;
        for (int i = 1; i <= 20; i++) {
            last = still.update(i * 0.03, 64, 0);
        }
        assertTrue(last < 5);
    }

    @Test
    void jumpingInPlaceResets() {
        Stillness still = new Stillness(0.1);
        still.update(0, 64, 0);
        still.update(0, 64, 0);
        assertEquals(0, still.update(0, 64.4, 0));
    }
}
