package org.lts.prophunt.game;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.lts.prophunt.core.Messages;

final class BlockMenu implements InventoryHolder {

    private final Inventory inventory;
    private final List<Material> slots;

    BlockMenu(Messages msg, List<Material> blocks, Material current) {
        int count = Math.min(54, blocks.size());
        int size = Math.max(9, (count + 8) / 9 * 9);
        this.slots = new ArrayList<>(blocks.subList(0, count));
        this.inventory = Bukkit.createInventory(this, size, msg.get("menu.title"));
        for (int i = 0; i < count; i++) {
            Material material = slots.get(i);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (material == current) {
                meta.lore(List.of(msg.item("menu.current")));
                meta.setEnchantmentGlintOverride(true);
            } else {
                meta.lore(List.of(msg.item("menu.pick")));
            }
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
    }

    Material at(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
