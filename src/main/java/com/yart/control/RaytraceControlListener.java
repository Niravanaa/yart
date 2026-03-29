package com.yart.control;

import com.yart.YetAnotherRayTracerPlugin;
import com.yart.render.RaytraceSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class RaytraceControlListener implements Listener {
    private static final double ROTATE_STEP_RADIANS = 0.14;
    private static final double ZOOM_STEP = 0.20;

    private final YetAnotherRayTracerPlugin plugin;

    public RaytraceControlListener(YetAnotherRayTracerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        String controlAction = RaytraceControls.readAction(plugin, event.getItem());
        if (controlAction == null) {
            return;
        }

        RaytraceSession session = plugin.getSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("Start a raytrace session before using controls.", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);

        switch (controlAction) {
            case "yaw_left" -> session.rotateYaw(-ROTATE_STEP_RADIANS);
            case "yaw_right" -> session.rotateYaw(ROTATE_STEP_RADIANS);
            case "pitch_up" -> session.rotatePitch(-ROTATE_STEP_RADIANS);
            case "pitch_down" -> session.rotatePitch(ROTATE_STEP_RADIANS);
            case "zoom_in" -> session.zoom(-ZOOM_STEP);
            case "zoom_out" -> session.zoom(ZOOM_STEP);
            case "camera_reset" -> session.resetCamera();
            case "auto_spin_toggle" -> session.toggleAutoSpin();
            default -> {
                return;
            }
        }

        player.sendActionBar(Component.text(session.debugState(), NamedTextColor.GRAY));
    }
}
