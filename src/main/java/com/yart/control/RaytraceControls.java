package com.yart.control;

import com.yart.YetAnotherRayTracerPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class RaytraceControls {
    private static final String CONTROL_KEY = "raytrace_control_action";

    private RaytraceControls() {
    }

    public static void giveControls(YetAnotherRayTracerPlugin plugin, Player player) {
        NamespacedKey key = new NamespacedKey(plugin, CONTROL_KEY);

        List<ItemStack> items = List.of(
                controlItem(Material.STONE_BUTTON, "Rotate Left", "yaw_left", key),
                controlItem(Material.OAK_BUTTON, "Rotate Right", "yaw_right", key),
                controlItem(Material.SPRUCE_BUTTON, "Rotate Up", "pitch_up", key),
                controlItem(Material.BIRCH_BUTTON, "Rotate Down", "pitch_down", key),
                controlItem(Material.JUNGLE_BUTTON, "Zoom In", "zoom_in", key),
                controlItem(Material.ACACIA_BUTTON, "Zoom Out", "zoom_out", key),
                controlItem(Material.CRIMSON_BUTTON, "Reset Camera", "camera_reset", key),
                controlItem(Material.POLISHED_BLACKSTONE_BUTTON, "Toggle Auto Spin", "auto_spin_toggle", key));

        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
    }

    public static String readAction(YetAnotherRayTracerPlugin plugin, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || !itemStack.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, CONTROL_KEY);
        return pdc.get(key, PersistentDataType.STRING);
    }

    private static ItemStack controlItem(Material material, String label, String action, NamespacedKey key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Right/left click to apply", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
}
