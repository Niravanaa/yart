package com.yart.render;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public final class RaytraceSession {
    private static final double MIN_PITCH = -1.35;
    private static final double MAX_PITCH = 1.35;
    private static final double SMOOTHING = 0.035;
    private static final double MIN_ZOOM = 0.05;
    private static final double MIN_SPIN_SPEED = 0.25;
    private static final double MAX_SPIN_SPEED = 4.00;
    private static final double DEFAULT_FOV_RADIANS = Math.toRadians(65.0);

    private final JavaPlugin plugin;
    private final UUID playerId;
    private final ObjModel model;
    private final RayTracer rayTracer;
    private final BlockScreen blockScreen;

    private BukkitTask task;
    private double yaw;
    private double pitch;
    private double zoom;
    private boolean autoSpinEnabled;
    private double yawVelocity;
    private double pitchVelocity;
    private double targetYawVelocity;
    private double targetPitchVelocity;
    private int ticksUntilDirectionShift;
    private double autoSpinSpeed;
    private boolean lightingEnabled;
    private boolean wireframeEnabled;
    private double fovRadians;
    private double resetYaw;
    private double resetPitch;
    private double resetZoom;
    private double resetFovRadians;

    public RaytraceSession(JavaPlugin plugin, Player player, ObjModel model, int width, int height, float pixelScale,
            double distance, BlockPalette palette) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.model = model;
        this.rayTracer = new RayTracer(width, height, palette);
        this.blockScreen = BlockScreen.spawnInFrontOf(player, width, height, pixelScale, distance, palette);
        this.yaw = 0.0;
        this.pitch = 0.0;
        this.zoom = 2.75;
        this.autoSpinEnabled = false;
        this.yawVelocity = 0.0;
        this.pitchVelocity = 0.0;
        this.targetYawVelocity = 0.0;
        this.targetPitchVelocity = 0.0;
        this.ticksUntilDirectionShift = 0;
        this.autoSpinSpeed = 1.0;
        this.lightingEnabled = false;
        this.wireframeEnabled = false;
        this.fovRadians = DEFAULT_FOV_RADIANS;
        this.resetYaw = yaw;
        this.resetPitch = pitch;
        this.resetZoom = zoom;
        this.resetFovRadians = fovRadians;
    }

    public void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                stop();
                return;
            }

            if (autoSpinEnabled) {
                updateAutoSpin();
            }

            int[] frame = rayTracer.render(model, yaw, pitch, zoom, lightingEnabled, wireframeEnabled, fovRadians);
            blockScreen.applyFrame(frame);
        }, 1L, 1L);
    }

    public UUID playerId() {
        return playerId;
    }

    public void rotateYaw(double delta) {
        yaw += delta;
    }

    public void rotatePitch(double delta) {
        pitch = clamp(pitch + delta, MIN_PITCH, MAX_PITCH);
    }

    public void zoom(double delta) {
        zoom = Math.max(MIN_ZOOM, zoom + delta);
    }

    public boolean toggleAutoSpin() {
        autoSpinEnabled = !autoSpinEnabled;
        if (autoSpinEnabled) {
            chooseNewSpinTargets();
        } else {
            targetYawVelocity = 0.0;
            targetPitchVelocity = 0.0;
        }
        return autoSpinEnabled;
    }

    public void setAutoSpinSpeed(double speed) {
        autoSpinSpeed = clamp(speed, MIN_SPIN_SPEED, MAX_SPIN_SPEED);
    }

    public double getAutoSpinSpeed() {
        return autoSpinSpeed;
    }

    public void setLightingEnabled(boolean enabled) {
        lightingEnabled = enabled;
    }

    public boolean isLightingEnabled() {
        return lightingEnabled;
    }

    public void setWireframeEnabled(boolean enabled) {
        wireframeEnabled = enabled;
    }

    public boolean isWireframeEnabled() {
        return wireframeEnabled;
    }

    public void applyCameraPreset(SceneCameraPreset preset) {
        yaw = preset.yaw();
        pitch = clamp(preset.pitch(), MIN_PITCH, MAX_PITCH);
        zoom = Math.max(MIN_ZOOM, preset.zoom());
        fovRadians = clamp(preset.fovRadians(), Math.toRadians(20.0), Math.toRadians(120.0));
        resetYaw = yaw;
        resetPitch = pitch;
        resetZoom = zoom;
        resetFovRadians = fovRadians;
    }

    public void resetCamera() {
        yaw = resetYaw;
        pitch = clamp(resetPitch, MIN_PITCH, MAX_PITCH);
        zoom = Math.max(MIN_ZOOM, resetZoom);
        fovRadians = clamp(resetFovRadians, Math.toRadians(20.0), Math.toRadians(120.0));
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        targetYawVelocity = 0.0;
        targetPitchVelocity = 0.0;
    }

    public String debugState() {
        return String.format(
            "yaw=%.2f pitch=%.2f zoom=%.2f autoSpin=%s spinSpeed=%.2f lighting=%s wireframe=%s",
                yaw,
                pitch,
                zoom,
                autoSpinEnabled ? "on" : "off",
                autoSpinSpeed,
            lightingEnabled ? "on" : "off",
            wireframeEnabled ? "on" : "off");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        blockScreen.destroy();
    }

    private void updateAutoSpin() {
        if (ticksUntilDirectionShift <= 0) {
            chooseNewSpinTargets();
        }

        ticksUntilDirectionShift--;

        yawVelocity = approach(yawVelocity, targetYawVelocity, SMOOTHING);
        pitchVelocity = approach(pitchVelocity, targetPitchVelocity, SMOOTHING * 0.75);

        rotateYaw(yawVelocity);
        rotatePitch(pitchVelocity);

        // Nudge pitch direction near limits so vertical motion keeps flowing.
        if (pitch > MAX_PITCH - 0.08 && targetPitchVelocity > 0.0) {
            targetPitchVelocity = -Math.abs(targetPitchVelocity);
        } else if (pitch < MIN_PITCH + 0.08 && targetPitchVelocity < 0.0) {
            targetPitchVelocity = Math.abs(targetPitchVelocity);
        }
    }

    private void chooseNewSpinTargets() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        double yawMagnitude = random.nextDouble(0.012, 0.05) * autoSpinSpeed;
        targetYawVelocity = random.nextBoolean() ? yawMagnitude : -yawMagnitude;

        double pitchMagnitude = random.nextDouble(0.004, 0.02) * autoSpinSpeed;
        targetPitchVelocity = random.nextBoolean() ? pitchMagnitude : -pitchMagnitude;

        ticksUntilDirectionShift = random.nextInt(35, 120);
    }

    private static double approach(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        return Math.max(current - maxDelta, target);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
