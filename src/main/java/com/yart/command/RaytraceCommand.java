package com.yart.command;

import com.yart.YetAnotherRayTracerPlugin;
import com.yart.control.RaytraceControls;
import com.yart.render.LoadedScene;
import com.yart.render.ObjLoader;
import com.yart.render.ObjModel;
import com.yart.render.RaytraceSession;
import com.yart.render.SceneJsonLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RaytraceCommand implements CommandExecutor, TabCompleter {
    private static final String PLUGIN_VERSION = "0.1.0";
    private static final String[] ASCII_LOGO = {
            "__     __      _____ _______ ",
            " \\ \\   / //\\   |  __ \\__   __|",
            "  \\ \\_/ //  \\  | |__) | | |   ",
            "   \\   // /\\ \\ |  _  /  | |   ",
            "    | |/ ____ \\| | \\ \\  | |   ",
            "    |_/_/    \\_\\_|  \\_\\ |_|"
    };
    private static final int DEFAULT_CANVAS_WIDTH = 64;
    private static final int DEFAULT_CANVAS_HEIGHT = 36;
    private static final int MIN_CANVAS_SIZE = 8;
    private static final int MAX_CANVAS_WIDTH = 256;
    private static final int MAX_CANVAS_HEIGHT = 256;
    private static final double DEFAULT_SPIN_SPEED = 1.0;
    private static final double MIN_SPIN_SPEED = 0.25;
    private static final double MAX_SPIN_SPEED = 4.0;

    private final YetAnotherRayTracerPlugin plugin;
    private final Map<UUID, LoadedScene> loadedScenes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> canvasWidths = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> canvasHeights = new ConcurrentHashMap<>();
    private final Map<UUID, Double> spinSpeeds = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lightingModes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> wireframeModes = new ConcurrentHashMap<>();

    public RaytraceCommand(YetAnotherRayTracerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "load":
                return handleLoad(player, args);
            case "start":
                return handleStart(player, args);
            case "size":
                return handleSize(player, args);
            case "spin":
                return handleSpin(player, args);
            case "lighting":
                return handleLighting(player, args);
            case "wireframe":
                return handleWireframe(player, args);
            case "camera":
                return handleCamera(player, args);
            case "stop":
                return handleStop(player);
            case "reset":
                return handleReset(player);
            case "controls":
                return handleControls(player);
            case "palette":
                return handlePalette(player);
            default:
                sendUsage(player);
                return true;
        }
    }

    private boolean handleLoad(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /raytrace load <file.obj|file.json>", NamedTextColor.RED));
            return true;
        }

        File modelFile = new File(new File(plugin.getDataFolder(), "models"), args[1]);
        if (!modelFile.exists() || !modelFile.isFile()) {
            player.sendMessage(Component.text("Model file not found: " + modelFile.getName(), NamedTextColor.RED));
            return true;
        }

        try {
            LoadedScene scene = loadScene(modelFile);
            ObjModel model = scene.model();
            if (model.triangles().isEmpty()) {
                player.sendMessage(Component.text("Model has no triangles after parsing.", NamedTextColor.RED));
                return true;
            }

            loadedScenes.put(player.getUniqueId(), scene);
            player.sendMessage(Component.text(
                    "Loaded model " + modelFile.getName() + " with " + model.triangles().size() + " triangles.",
                    NamedTextColor.GREEN));
        } catch (IOException | RuntimeException ex) {
            player.sendMessage(Component.text("Failed to load model: " + ex.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Model load failed for " + modelFile.getName() + ": " + ex.getMessage());
        }

        return true;
    }

    private boolean handleStart(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        LoadedScene scene = loadedScenes.get(playerId);
        if (scene == null) {
            player.sendMessage(Component.text(
                    "No model loaded. Use /raytrace load <file.obj|file.json> first.",
                    NamedTextColor.RED));
            return true;
        }

        ObjModel model = scene.model();

        if (args.length > 3) {
            player.sendMessage(Component.text("Usage: /raytrace start [width] [height]", NamedTextColor.RED));
            return true;
        }

        int width = canvasWidths.getOrDefault(playerId, DEFAULT_CANVAS_WIDTH);
        int height = canvasHeights.getOrDefault(playerId, DEFAULT_CANVAS_HEIGHT);

        if (args.length == 2) {
            Integer requestedWidth = parseIntegerOrNull(args[1]);
            if (requestedWidth == null) {
                player.sendMessage(Component.text("Width must be a whole number.", NamedTextColor.RED));
                return true;
            }
            width = clamp(requestedWidth, MIN_CANVAS_SIZE, MAX_CANVAS_WIDTH);
            canvasWidths.put(playerId, width);
        } else if (args.length == 3) {
            Integer requestedWidth = parseIntegerOrNull(args[1]);
            Integer requestedHeight = parseIntegerOrNull(args[2]);
            if (requestedWidth == null || requestedHeight == null) {
                player.sendMessage(Component.text("Width and height must be whole numbers.", NamedTextColor.RED));
                return true;
            }
            width = clamp(requestedWidth, MIN_CANVAS_SIZE, MAX_CANVAS_WIDTH);
            height = clamp(requestedHeight, MIN_CANVAS_SIZE, MAX_CANVAS_HEIGHT);
            canvasWidths.put(playerId, width);
            canvasHeights.put(playerId, height);
        }

        double spinSpeed = spinSpeeds.getOrDefault(playerId, DEFAULT_SPIN_SPEED);
        boolean lightingEnabled = lightingModes.getOrDefault(playerId, false);
        boolean wireframeEnabled = wireframeModes.getOrDefault(playerId, false);
        startOrRestartSession(player, scene, width, height, spinSpeed, lightingEnabled, wireframeEnabled);

        player.sendMessage(Component.text(
                "Started raytracer at " + width + "x" + height + " (" + (width * height) + " block displays).",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text("Current resolution: " + width + "x" + height, NamedTextColor.GRAY));
        player.sendMessage(
                Component.text("Use /raytrace size width <w> height <h> to adjust canvas.", NamedTextColor.GRAY));
        player.sendMessage(
                Component.text("Use /raytrace spin set|up|down to adjust auto-spin speed.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Use /raytrace lighting on|off|toggle to control shading.",
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("Use /raytrace wireframe on|off|toggle to draw mesh edges.",
            NamedTextColor.GRAY));
        player.sendMessage(Component.text("Background uses the palette's black entry (or closest dark block).",
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("Use /raytrace controls to get rotation/zoom items.", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleSize(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        int currentWidth = canvasWidths.getOrDefault(playerId, DEFAULT_CANVAS_WIDTH);
        int currentHeight = canvasHeights.getOrDefault(playerId, DEFAULT_CANVAS_HEIGHT);

        if (args.length == 1 || "show".equalsIgnoreCase(args[1])) {
            player.sendMessage(Component.text(
                    "Current canvas size: " + currentWidth + "x" + currentHeight,
                    NamedTextColor.AQUA));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(
                    Component.text("Usage: /raytrace size <show|width|height> [mode] [value]", NamedTextColor.RED));
            return true;
        }

        String dimension = args[1].toLowerCase(Locale.ROOT);
        String mode = args[2].toLowerCase(Locale.ROOT);

        if ("width".equals(dimension)) {
            int newWidth;
            switch (mode) {
                case "set":
                    if (args.length < 4) {
                        player.sendMessage(
                                Component.text("Usage: /raytrace size width set <value>", NamedTextColor.RED));
                        return true;
                    }
                    Integer value = parseIntegerOrNull(args[3]);
                    if (value == null) {
                        player.sendMessage(Component.text("Width must be a whole number.", NamedTextColor.RED));
                        return true;
                    }
                    newWidth = clamp(value, MIN_CANVAS_SIZE, MAX_CANVAS_WIDTH);
                    break;
                case "up":
                case "increase":
                case "inc": {
                    int amount = args.length > 3 ? parseIntOrDefault(args[3], 8) : 8;
                    newWidth = clamp(currentWidth + Math.max(1, amount), MIN_CANVAS_SIZE, MAX_CANVAS_WIDTH);
                    break;
                }
                case "down":
                case "decrease":
                case "dec": {
                    int amount = args.length > 3 ? parseIntOrDefault(args[3], 8) : 8;
                    newWidth = clamp(currentWidth - Math.max(1, amount), MIN_CANVAS_SIZE, MAX_CANVAS_WIDTH);
                    break;
                }
                default:
                    player.sendMessage(
                            Component.text("Usage: /raytrace size width <set|up|down> [value]", NamedTextColor.RED));
                    return true;
            }
            canvasWidths.put(playerId, newWidth);
            player.sendMessage(Component.text(
                    "Canvas width set to " + newWidth + " (height: " + currentHeight + ").",
                    NamedTextColor.GREEN));

            RaytraceSession active = plugin.getSession(playerId);
            LoadedScene scene = loadedScenes.get(playerId);
            if (active != null && scene != null) {
                double spinSpeed = spinSpeeds.getOrDefault(playerId, DEFAULT_SPIN_SPEED);
                boolean lightingEnabled = lightingModes.getOrDefault(playerId, false);
                boolean wireframeEnabled = wireframeModes.getOrDefault(playerId, false);
                startOrRestartSession(player, scene, newWidth, currentHeight, spinSpeed, lightingEnabled,
                    wireframeEnabled);
                player.sendMessage(Component.text("Active raytracer session resized.", NamedTextColor.YELLOW));
            }
        } else if ("height".equals(dimension)) {
            int newHeight;
            switch (mode) {
                case "set":
                    if (args.length < 4) {
                        player.sendMessage(
                                Component.text("Usage: /raytrace size height set <value>", NamedTextColor.RED));
                        return true;
                    }
                    Integer value = parseIntegerOrNull(args[3]);
                    if (value == null) {
                        player.sendMessage(Component.text("Height must be a whole number.", NamedTextColor.RED));
                        return true;
                    }
                    newHeight = clamp(value, MIN_CANVAS_SIZE, MAX_CANVAS_HEIGHT);
                    break;
                case "up":
                case "increase":
                case "inc": {
                    int amount = args.length > 3 ? parseIntOrDefault(args[3], 8) : 8;
                    newHeight = clamp(currentHeight + Math.max(1, amount), MIN_CANVAS_SIZE, MAX_CANVAS_HEIGHT);
                    break;
                }
                case "down":
                case "decrease":
                case "dec": {
                    int amount = args.length > 3 ? parseIntOrDefault(args[3], 8) : 8;
                    newHeight = clamp(currentHeight - Math.max(1, amount), MIN_CANVAS_SIZE, MAX_CANVAS_HEIGHT);
                    break;
                }
                default:
                    player.sendMessage(
                            Component.text("Usage: /raytrace size height <set|up|down> [value]", NamedTextColor.RED));
                    return true;
            }
            canvasHeights.put(playerId, newHeight);
            player.sendMessage(Component.text(
                    "Canvas height set to " + newHeight + " (width: " + currentWidth + ").",
                    NamedTextColor.GREEN));

            RaytraceSession active = plugin.getSession(playerId);
            LoadedScene scene = loadedScenes.get(playerId);
            if (active != null && scene != null) {
                double spinSpeed = spinSpeeds.getOrDefault(playerId, DEFAULT_SPIN_SPEED);
                boolean lightingEnabled = lightingModes.getOrDefault(playerId, false);
                boolean wireframeEnabled = wireframeModes.getOrDefault(playerId, false);
                startOrRestartSession(player, scene, currentWidth, newHeight, spinSpeed, lightingEnabled,
                    wireframeEnabled);
                player.sendMessage(Component.text("Active raytracer session resized.", NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(
                    Component.text("Usage: /raytrace size <show|width|height> [mode] [value]", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleSpin(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        double currentSpeed = spinSpeeds.getOrDefault(playerId, DEFAULT_SPIN_SPEED);

        if (args.length == 1 || "show".equalsIgnoreCase(args[1])) {
            player.sendMessage(Component.text(
                    String.format("Current auto-spin speed: %.2f", currentSpeed),
                    NamedTextColor.AQUA));
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        double newSpeed;

        switch (mode) {
            case "set":
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /raytrace spin set <value>", NamedTextColor.RED));
                    return true;
                }
                Double value = parseDoubleOrNull(args[2]);
                if (value == null) {
                    player.sendMessage(Component.text("Spin speed must be a number.", NamedTextColor.RED));
                    return true;
                }
                newSpeed = clamp(value, MIN_SPIN_SPEED, MAX_SPIN_SPEED);
                break;
            case "up":
            case "increase":
            case "inc": {
                double amount = args.length > 2 ? parseDoubleOrDefault(args[2], 0.25) : 0.25;
                newSpeed = clamp(currentSpeed + Math.max(0.05, amount), MIN_SPIN_SPEED, MAX_SPIN_SPEED);
                break;
            }
            case "down":
            case "decrease":
            case "dec": {
                double amount = args.length > 2 ? parseDoubleOrDefault(args[2], 0.25) : 0.25;
                newSpeed = clamp(currentSpeed - Math.max(0.05, amount), MIN_SPIN_SPEED, MAX_SPIN_SPEED);
                break;
            }
            default:
                player.sendMessage(
                        Component.text("Usage: /raytrace spin <show|set|up|down> [value]", NamedTextColor.RED));
                return true;
        }

        spinSpeeds.put(playerId, newSpeed);
        player.sendMessage(Component.text(
                String.format("Auto-spin speed set to %.2f (range %.2f-%.2f)", newSpeed, MIN_SPIN_SPEED,
                        MAX_SPIN_SPEED),
                NamedTextColor.GREEN));

        RaytraceSession active = plugin.getSession(playerId);
        if (active != null) {
            active.setAutoSpinSpeed(newSpeed);
            player.sendMessage(Component.text("Applied to active raytracer session.", NamedTextColor.YELLOW));
        }

        return true;
    }

    private boolean handleLighting(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        boolean current = lightingModes.getOrDefault(playerId, false);

        if (args.length > 1) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (!List.of("show", "on", "off", "toggle", "enable", "disable", "enabled", "disabled")
                    .contains(mode)) {
                player.sendMessage(
                        Component.text("Usage: /raytrace lighting <show|on|off|toggle>", NamedTextColor.RED));
                return true;
            }

            switch (mode) {
                case "on":
                case "enable":
                case "enabled":
                    current = true;
                    break;
                case "off":
                case "disable":
                case "disabled":
                    current = false;
                    break;
                case "toggle":
                    current = !current;
                    break;
                case "show":
                    // Keep current state.
                    break;
                default:
                    // No-op.
                    break;
            }
        }

        lightingModes.put(playerId, current);

        player.sendMessage(Component.text(
                "Lighting is now " + (current ? "enabled" : "disabled") + ".",
                current ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        RaytraceSession active = plugin.getSession(playerId);
        if (active != null) {
            active.setLightingEnabled(current);
            player.sendMessage(Component.text(
                    "Applied to active raytracer session.",
                    NamedTextColor.YELLOW));
        }

        return true;
    }

    private boolean handleCamera(Player player, String[] args) {
        RaytraceSession active = plugin.getSession(player.getUniqueId());
        if (active == null) {
            player.sendMessage(Component.text("No active raytracer session.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 || "show".equalsIgnoreCase(args[1])) {
            player.sendMessage(Component.text("Camera: " + active.debugState(), NamedTextColor.AQUA));
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("reset").contains(mode)) {
            player.sendMessage(Component.text("Usage: /raytrace camera <show|reset>", NamedTextColor.RED));
            return true;
        }

        active.resetCamera();
        player.sendMessage(Component.text("Camera reset to loaded/default view.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Camera: " + active.debugState(), NamedTextColor.GRAY));
        return true;
    }

    private boolean handleWireframe(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        boolean current = wireframeModes.getOrDefault(playerId, false);

        if (args.length > 1) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (!List.of("show", "on", "off", "toggle", "enable", "disable", "enabled", "disabled")
                    .contains(mode)) {
                player.sendMessage(
                        Component.text("Usage: /raytrace wireframe <show|on|off|toggle>", NamedTextColor.RED));
                return true;
            }

            switch (mode) {
                case "on":
                case "enable":
                case "enabled":
                    current = true;
                    break;
                case "off":
                case "disable":
                case "disabled":
                    current = false;
                    break;
                case "toggle":
                    current = !current;
                    break;
                case "show":
                    break;
                default:
                    break;
            }
        }

        wireframeModes.put(playerId, current);
        player.sendMessage(Component.text(
                "Wireframe is now " + (current ? "enabled" : "disabled") + ".",
                current ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        RaytraceSession active = plugin.getSession(playerId);
        if (active != null) {
            active.setWireframeEnabled(current);
            player.sendMessage(Component.text(
                    "Applied to active raytracer session.",
                    NamedTextColor.YELLOW));
        }

        return true;
    }

    private void startOrRestartSession(Player player, LoadedScene scene, int width, int height, double spinSpeed,
            boolean lightingEnabled, boolean wireframeEnabled) {
        handleStop(player);
        RaytraceSession session = new RaytraceSession(plugin, player, scene.model(), width, height, 0.35f, 6.0,
                plugin.getBlockPalette());
        if (scene.cameraPreset() != null) {
            session.applyCameraPreset(scene.cameraPreset());
        }
        session.setAutoSpinSpeed(spinSpeed);
        session.setLightingEnabled(lightingEnabled);
        session.setWireframeEnabled(wireframeEnabled);
        session.start();
        plugin.getSessions().put(player.getUniqueId(), session);
    }

    private boolean handleStop(Player player) {
        RaytraceSession session = plugin.removeSession(player.getUniqueId());
        if (session != null) {
            session.stop();
            player.sendMessage(Component.text("Stopped raytracer screen.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("No active raytracer session.", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean handleReset(Player player) {
        handleStop(player);
        loadedScenes.remove(player.getUniqueId());
        canvasWidths.remove(player.getUniqueId());
        canvasHeights.remove(player.getUniqueId());
        spinSpeeds.remove(player.getUniqueId());
        lightingModes.remove(player.getUniqueId());
        wireframeModes.remove(player.getUniqueId());
        player.sendMessage(Component.text("Cleared loaded model.", NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleControls(Player player) {
        RaytraceControls.giveControls(plugin, player);
        player.sendMessage(
                Component.text("Gave control items: left/right/up/down/zoom in/zoom out/camera reset/auto spin toggle.",
                        NamedTextColor.GREEN));
        return true;
    }

    private boolean handlePalette(Player player) {
        player.sendMessage(Component.text("Palette mapping (index -> block):", NamedTextColor.AQUA));
        for (String line : plugin.getBlockPalette().describe()) {
            player.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
        return true;
    }

    private int parseIntOrDefault(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Integer parseIntegerOrNull(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double parseDoubleOrDefault(String input, double fallback) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Double parseDoubleOrNull(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private LoadedScene loadScene(File modelFile) throws IOException {
        String lowerName = modelFile.getName().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".obj")) {
            return new LoadedScene(ObjLoader.load(modelFile), null);
        }
        if (lowerName.endsWith(".json")) {
            return SceneJsonLoader.loadScene(modelFile);
        }

        throw new IllegalArgumentException("Unsupported file type. Use .obj or .json");
    }

    private void sendUsage(Player player) {
        for (String logoLine : ASCII_LOGO) {
            player.sendMessage(Component.text(logoLine, NamedTextColor.GOLD));
        }
        player.sendMessage(Component.text("YetAnotherRayTracer (YART) - Version " + PLUGIN_VERSION,
                NamedTextColor.AQUA));
        player.sendMessage(Component.text("", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Raytrace commands:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/raytrace load <file.obj|file.json>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace start [width] [height]", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace size show", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace size width <set|up|down> [value]", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace size height <set|up|down> [value]", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace spin <show|set|up|down> [value]", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace lighting <show|on|off|toggle>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace wireframe <show|on|off|toggle>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace camera <show|reset>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace stop", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace reset", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace controls", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/raytrace palette", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("load", "start", "size", "spin", "lighting", "wireframe", "camera", "stop", "reset", "controls",
                    "palette");
        }

        if (args.length == 2 && "size".equalsIgnoreCase(args[0])) {
            return List.of("show", "set", "up", "down");
        }

        if (args.length == 2 && "spin".equalsIgnoreCase(args[0])) {
            return List.of("show", "set", "up", "down");
        }

        if (args.length == 2 && "lighting".equalsIgnoreCase(args[0])) {
            return List.of("show", "on", "off", "toggle");
        }

        if (args.length == 2 && "wireframe".equalsIgnoreCase(args[0])) {
            return List.of("show", "on", "off", "toggle");
        }

        if (args.length == 2 && "camera".equalsIgnoreCase(args[0])) {
            return List.of("show", "reset");
        }

        if (args.length == 2 && "load".equalsIgnoreCase(args[0])) {
            File modelsDir = new File(plugin.getDataFolder(), "models");
            File[] files = modelsDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".obj") || lower.endsWith(".json");
            });
            if (files == null) {
                return List.of();
            }

            List<String> completions = new ArrayList<>();
            for (File file : files) {
                completions.add(file.getName());
            }
            return completions;
        }

        return List.of();
    }
}
