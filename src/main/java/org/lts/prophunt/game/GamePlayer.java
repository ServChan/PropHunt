package org.lts.prophunt.game;

import java.util.UUID;
import org.bukkit.Location;
import org.lts.prophunt.logic.Stillness;

final class GamePlayer {

    enum Role {
        WAITING,
        HUNTER,
        PROP,
        SPECTATOR
    }

    final UUID id;
    final String name;
    Role role = Role.WAITING;
    Disguise disguise;
    final Stillness stillness = new Stillness(0.08);
    Location lockedAt;
    boolean lockRefused;
    Location lastInside;
    int tauntReady;
    int changeReady;
    int whistleReady;
    int hitReady;
    int dropTick = -1;
    int barHold;
    int found;
    int taunts;

    GamePlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    void resetRound() {
        disguise = null;
        lockedAt = null;
        lockRefused = false;
        lastInside = null;
        tauntReady = 0;
        changeReady = 0;
        whistleReady = 0;
        hitReady = 0;
        barHold = 0;
        found = 0;
        taunts = 0;
    }
}
