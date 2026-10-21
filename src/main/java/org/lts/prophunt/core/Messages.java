package org.lts.prophunt.core;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private YamlConfiguration file = new YamlConfiguration();
    private TagResolver prefix = TagResolver.empty();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        String name = "messages_" + language + ".yml";
        if (plugin.getResource(name) == null) {
            plugin.getLogger().warning("Unknown language " + language + ", using ru");
            name = "messages_ru.yml";
        }
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + name, e);
        }
        file = loaded;
        prefix = Placeholder.parsed("pfx", raw("prefix"));
    }

    public String raw(String key) {
        String value = file.getString(key);
        return value == null ? key : value;
    }

    public List<String> rawList(String key) {
        List<String> list = file.getStringList(key);
        return list.isEmpty() && file.isString(key) ? List.of(raw(key)) : list;
    }

    public Component get(String key, TagResolver... resolvers) {
        return mini.deserialize(raw(key), TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    public Component item(String key, TagResolver... resolvers) {
        return get(key, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        if (!raw(key).isEmpty()) {
            audience.sendMessage(get(key, resolvers));
        }
    }

    public void sendList(Audience audience, String key, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(prefix, TagResolver.resolver(resolvers));
        for (String line : rawList(key)) {
            audience.sendMessage(mini.deserialize(line, all));
        }
    }

    public void actionBar(Audience audience, String key, TagResolver... resolvers) {
        audience.sendActionBar(get(key, resolvers));
    }

    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
