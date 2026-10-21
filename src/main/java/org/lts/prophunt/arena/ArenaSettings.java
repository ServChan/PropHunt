package org.lts.prophunt.arena;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record ArenaSettings(
        int minPlayers,
        int maxPlayers,
        int minHunters,
        int maxHunters,
        int playersPerHunter,
        int lobbyCountdown,
        int hideSeconds,
        int seekSeconds,
        int endSeconds,
        double lockSeconds,
        double propScale,
        double hunterDamage,
        double missDamage,
        double hunterHealOnHit,
        double hunterReach,
        int hitCooldownTicks,
        boolean foundBecomeHunters,
        int tauntCooldown,
        int tauntTimeBonus,
        int tauntFloor,
        int autoTaunt,
        int whistleCooldown,
        int changeCooldown) {

    public static final List<String> KEYS = List.of(
            "min-players", "max-players", "min-hunters", "max-hunters", "players-per-hunter",
            "lobby-countdown", "hide-seconds", "seek-seconds", "end-seconds", "lock-seconds", "prop-scale",
            "hunter-damage", "miss-damage", "hunter-heal-on-hit", "hunter-reach", "hit-cooldown-ticks",
            "found-become-hunters", "taunt-cooldown", "taunt-time-bonus", "taunt-floor", "auto-taunt",
            "whistle-cooldown", "change-cooldown");

    public static ArenaSettings resolve(ConfigurationSection defaults, Map<String, Object> overrides) {
        Reader r = new Reader(defaults, overrides);
        int minHunters = clamp(r.i("min-hunters"), 1, 16);
        int minPlayers = Math.max(2, r.i("min-players"));
        return new ArenaSettings(
                minPlayers,
                Math.max(minPlayers, r.i("max-players")),
                minHunters,
                Math.max(minHunters, r.i("max-hunters")),
                Math.max(1, r.i("players-per-hunter")),
                Math.max(1, r.i("lobby-countdown")),
                Math.max(0, r.i("hide-seconds")),
                Math.max(10, r.i("seek-seconds")),
                Math.max(1, r.i("end-seconds")),
                Math.max(0.25, r.d("lock-seconds")),
                Math.max(0.1, Math.min(1.0, r.d("prop-scale"))),
                Math.max(0.5, r.d("hunter-damage")),
                Math.max(0, r.d("miss-damage")),
                Math.max(0, r.d("hunter-heal-on-hit")),
                Math.max(1.0, Math.min(8.0, r.d("hunter-reach"))),
                Math.max(1, r.i("hit-cooldown-ticks")),
                r.b("found-become-hunters"),
                Math.max(0, r.i("taunt-cooldown")),
                Math.max(0, r.i("taunt-time-bonus")),
                Math.max(0, r.i("taunt-floor")),
                Math.max(0, r.i("auto-taunt")),
                Math.max(0, r.i("whistle-cooldown")),
                Math.max(0, r.i("change-cooldown")));
    }

    public static Object parse(ConfigurationSection defaults, String key, String value) {
        Object sample = defaults.get(key);
        try {
            if (sample instanceof Boolean) {
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    return Boolean.parseBoolean(value.toLowerCase(Locale.ROOT));
                }
                return null;
            }
            if (sample instanceof Integer || sample instanceof Long) {
                return Integer.parseInt(value);
            }
            if (sample instanceof Number) {
                return Double.parseDouble(value.replace(',', '.'));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Reader(ConfigurationSection defaults, Map<String, Object> overrides) {

        private Object raw(String key) {
            Object value = overrides.get(key);
            return value != null ? value : defaults.get(key);
        }

        int i(String key) {
            return raw(key) instanceof Number number ? number.intValue() : 0;
        }

        double d(String key) {
            return raw(key) instanceof Number number ? number.doubleValue() : 0;
        }

        boolean b(String key) {
            return raw(key) instanceof Boolean bool && bool;
        }
    }
}
