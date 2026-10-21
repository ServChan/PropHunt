package org.lts.prophunt.core;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class Tools {

    public enum Tool {
        CHOOSE(Material.NETHER_STAR),
        TAUNT(Material.FIREWORK_STAR),
        SWORD(Material.IRON_SWORD),
        WHISTLE(Material.ECHO_SHARD),
        LEAVE(Material.RED_BED);

        private final Material material;

        Tool(Material material) {
            this.material = material;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Messages msg;
    private final NamespacedKey key;

    public Tools(JavaPlugin plugin, Messages msg) {
        this.msg = msg;
        this.key = new NamespacedKey(plugin, "tool");
    }

    public ItemStack make(Tool tool) {
        ItemStack item = new ItemStack(tool.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(msg.item("tools." + tool.key() + ".name"));
        meta.lore(List.of(msg.item("tools." + tool.key() + ".lore")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tool.name());
        item.setItemMeta(meta);
        return item;
    }

    public Tool of(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return Tool.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
