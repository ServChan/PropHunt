package org.lts.prophunt.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Roles {

    private Roles() {
    }

    public static int hunterCount(int players, int minHunters, int maxHunters, int playersPerHunter) {
        if (players < 2) {
            return 0;
        }
        int low = Math.max(1, minHunters);
        int high = Math.max(low, maxHunters);
        int wanted = (int) Math.ceil(players / (double) Math.max(1, playersPerHunter));
        wanted = Math.max(low, Math.min(high, wanted));
        return Math.min(wanted, players - 1);
    }

    public static <T> List<T> pickHunters(List<T> players, Map<T, Integer> timesHunter, int count, Random random) {
        List<T> pool = new ArrayList<>(players);
        Collections.shuffle(pool, random);
        pool.sort(Comparator.comparingInt(player -> timesHunter.getOrDefault(player, 0)));
        return new ArrayList<>(pool.subList(0, Math.max(0, Math.min(count, pool.size()))));
    }
}
