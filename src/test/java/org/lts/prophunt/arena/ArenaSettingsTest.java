package org.lts.prophunt.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ArenaSettingsTest {

    private static ConfigurationSection defaults() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(ArenaSettingsTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        return config.getConfigurationSection("defaults");
    }

    @Test
    void bundledDefaultsCoverEveryKey() {
        ConfigurationSection defaults = defaults();
        for (String key : ArenaSettings.KEYS) {
            assertTrue(defaults.contains(key), key);
        }
        assertEquals(ArenaSettings.KEYS.size(), defaults.getKeys(false).size());
        ArenaSettings settings = ArenaSettings.resolve(defaults, Map.of());
        assertEquals(1, settings.minHunters());
        assertEquals(3, settings.maxHunters());
        assertEquals(0.55, settings.propScale(), 1.0E-9);
        assertTrue(settings.foundBecomeHunters());
    }

    @Test
    void arenaOverridesWin() {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("max-hunters", 2);
        overrides.put("hunter-damage", 10.0);
        overrides.put("found-become-hunters", false);
        ArenaSettings settings = ArenaSettings.resolve(defaults(), overrides);
        assertEquals(2, settings.maxHunters());
        assertEquals(10.0, settings.hunterDamage(), 1.0E-9);
        assertFalse(settings.foundBecomeHunters());
    }

    @Test
    void valuesAreClamped() {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("min-players", 0);
        overrides.put("prop-scale", 5.0);
        overrides.put("hunter-reach", 100.0);
        ArenaSettings settings = ArenaSettings.resolve(defaults(), overrides);
        assertEquals(2, settings.minPlayers());
        assertEquals(1.0, settings.propScale(), 1.0E-9);
        assertEquals(8.0, settings.hunterReach(), 1.0E-9);
    }

    @Test
    void parsesByTheDefaultType() {
        ConfigurationSection defaults = defaults();
        assertEquals(3, ArenaSettings.parse(defaults, "max-hunters", "3"));
        assertNull(ArenaSettings.parse(defaults, "max-hunters", "three"));
        assertEquals(2.5, ArenaSettings.parse(defaults, "hunter-damage", "2,5"));
        assertEquals(4.0, ArenaSettings.parse(defaults, "hunter-damage", "4"));
        assertEquals(false, ArenaSettings.parse(defaults, "found-become-hunters", "FALSE"));
        assertNull(ArenaSettings.parse(defaults, "found-become-hunters", "nope"));
    }
}
