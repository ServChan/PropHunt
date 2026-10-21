package org.lts.prophunt.logic;

public final class RoundClock {

    private final int total;
    private int remaining;

    public RoundClock(int seconds) {
        this.total = Math.max(0, seconds);
        this.remaining = this.total;
    }

    public int remaining() {
        return remaining;
    }

    public int total() {
        return total;
    }

    public float progress() {
        return total == 0 ? 0f : Math.max(0f, Math.min(1f, remaining / (float) total));
    }

    public boolean tick() {
        if (remaining > 0) {
            remaining--;
        }
        return remaining == 0;
    }

    public int shorten(int seconds, int floor) {
        if (seconds <= 0 || remaining <= floor) {
            return 0;
        }
        int before = remaining;
        remaining = Math.max(floor, remaining - seconds);
        return before - remaining;
    }

    public static String format(int seconds) {
        int safe = Math.max(0, seconds);
        return safe / 60 + ":" + (safe % 60 < 10 ? "0" : "") + safe % 60;
    }
}
