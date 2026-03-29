package com.yart.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlockPaletteLoader {
    private static final String DEFAULT_RESOURCE = "default-blockset.json";

    private BlockPaletteLoader() {
    }

    public static BlockPalette load(JavaPlugin plugin) {
        File blocksetFile = new File(plugin.getDataFolder(), "blockset.json");

        if (blocksetFile.exists() && blocksetFile.isFile()) {
            try {
                BlockPalette palette = loadFromFile(blocksetFile);
                plugin.getLogger().info("Loaded block palette from " + blocksetFile.getAbsolutePath()
                        + " (solid+occluding entries kept: " + palette.size() + ")");
                return palette;
            } catch (RuntimeException | IOException ex) {
                plugin.getLogger().warning("Failed to load blockset.json: " + ex.getMessage());
            }
        }

        BlockPalette fallback = loadFromResource(plugin);
        plugin.getLogger().info("Using bundled default block palette (" + fallback.size() + " entries)");
        return fallback;
    }

    private static BlockPalette loadFromFile(File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            return parsePalette(reader);
        }
    }

    private static BlockPalette loadFromResource(JavaPlugin plugin) {
        InputStream stream = plugin.getResource(DEFAULT_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing plugin resource: " + DEFAULT_RESOURCE);
        }

        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parsePalette(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read bundled default block palette.", ex);
        }
    }

    private static BlockPalette parsePalette(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Blockset root must be an object.");
        }

        JsonObject obj = root.getAsJsonObject();
        JsonArray blocks = getRequiredArray(obj, "blocks");
        List<BlockPalette.Entry> entries = new ArrayList<>();

        for (JsonElement element : blocks) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject block = element.getAsJsonObject();
            String materialName = getRequiredString(block, "material").toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isBlock() || !material.isSolid() || !material.isOccluding()) {
                continue;
            }

            Vec3 rgb = getRequiredVec3(block, "rgb");
            entries.add(new BlockPalette.Entry(material, rgb));
        }

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No valid blocks found in palette file.");
        }

        return new BlockPalette(entries);
    }

    private static JsonArray getRequiredArray(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Missing required array: " + key);
        }
        return obj.getAsJsonArray(key);
    }

    private static String getRequiredString(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return obj.get(key).getAsString();
    }

    private static Vec3 getRequiredVec3(JsonObject obj, String key) {
        JsonArray array = getRequiredArray(obj, key);
        if (array.size() < 3) {
            throw new IllegalArgumentException("Field " + key + " must have at least 3 numbers.");
        }
        return new Vec3(
                clamp01(array.get(0).getAsDouble()),
                clamp01(array.get(1).getAsDouble()),
                clamp01(array.get(2).getAsDouble()));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
