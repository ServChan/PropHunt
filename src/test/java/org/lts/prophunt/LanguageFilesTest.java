package org.lts.prophunt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LanguageFilesTest {

    private static Set<String> keys(String name) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(LanguageFilesTest.class.getResourceAsStream("/" + name)), StandardCharsets.UTF_8));
        Set<String> out = new TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                out.add(key);
            }
        }
        return out;
    }

    @Test
    void languagesHaveTheSameKeys() {
        assertEquals(keys("messages_ru.yml"), keys("messages_en.yml"));
    }
}
