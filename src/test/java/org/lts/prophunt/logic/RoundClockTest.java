package org.lts.prophunt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoundClockTest {

    @Test
    void ticksDownToZero() {
        RoundClock clock = new RoundClock(2);
        assertFalse(clock.tick());
        assertTrue(clock.tick());
        assertTrue(clock.tick());
        assertEquals(0, clock.remaining());
    }

    @Test
    void tauntsShortenButNotBelowTheFloor() {
        RoundClock clock = new RoundClock(100);
        assertEquals(5, clock.shorten(5, 30));
        assertEquals(95, clock.remaining());
        RoundClock nearEnd = new RoundClock(33);
        assertEquals(3, nearEnd.shorten(5, 30));
        assertEquals(30, nearEnd.remaining());
        assertEquals(0, nearEnd.shorten(5, 30));
        RoundClock below = new RoundClock(20);
        assertEquals(0, below.shorten(5, 30));
        assertEquals(20, below.remaining());
    }

    @Test
    void formatsMinutes() {
        assertEquals("5:00", RoundClock.format(300));
        assertEquals("0:09", RoundClock.format(9));
        assertEquals("1:05", RoundClock.format(65));
        assertEquals("0:00", RoundClock.format(-4));
    }

    @Test
    void progressStaysInRange() {
        RoundClock clock = new RoundClock(10);
        assertEquals(1f, clock.progress());
        clock.shorten(5, 0);
        assertEquals(0.5f, clock.progress());
        assertEquals(0f, new RoundClock(0).progress());
    }
}
