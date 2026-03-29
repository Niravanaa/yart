package com.yart.render;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class BlockPalette {
    private final List<Entry> entries;
    private final int backgroundIndex;

    public BlockPalette(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Block palette cannot be empty.");
        }
        this.entries = List.copyOf(entries);
        this.backgroundIndex = findBackgroundIndex();
    }

    public int size() {
        return entries.size();
    }

    public Material materialAt(int index) {
        int clamped = Math.max(0, Math.min(entries.size() - 1, index));
        return entries.get(clamped).material();
    }

    public int backgroundIndex() {
        return backgroundIndex;
    }

    public int nearestIndex(Vec3 color) {
        Vec3 target = toOklab(clamp01(color));
        int bestIndex = 0;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < entries.size(); i++) {
            Vec3 sample = entries.get(i).oklab();
            double dl = target.x() - sample.x();
            double da = target.y() - sample.y();
            double db = target.z() - sample.z();
            double distance = dl * dl + da * da + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            lines.add(i + " -> " + entry.material().name() + " rgb=" + formatRgb(entry.rgb()));
        }
        return lines;
    }

    public static Vec3 clamp01(Vec3 color) {
        return new Vec3(
                clamp(color.x(), 0.0, 1.0),
                clamp(color.y(), 0.0, 1.0),
                clamp(color.z(), 0.0, 1.0));
    }

    public static Vec3 toOklab(Vec3 srgb) {
        Vec3 c = clamp01(srgb);

        double r = srgbToLinear(c.x());
        double g = srgbToLinear(c.y());
        double b = srgbToLinear(c.z());

        double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
        double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
        double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

        double l_ = Math.cbrt(l);
        double m_ = Math.cbrt(m);
        double s_ = Math.cbrt(s);

        return new Vec3(
                0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
                1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
                0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_);
    }

    private static double srgbToLinear(double channel) {
        if (channel <= 0.04045) {
            return channel / 12.92;
        }
        return Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static String formatRgb(Vec3 rgb) {
        return String.format("[%.3f, %.3f, %.3f]", rgb.x(), rgb.y(), rgb.z());
    }

    private int findBackgroundIndex() {
        for (int i = 0; i < entries.size(); i++) {
            String materialName = entries.get(i).material().name();
            if ("BLACK_CONCRETE".equals(materialName)
                    || "BLACK_WOOL".equals(materialName)
                    || "BLACK_TERRACOTTA".equals(materialName)) {
                return i;
            }
        }

        return nearestIndex(new Vec3(0.0, 0.0, 0.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Entry(Material material, Vec3 rgb, Vec3 oklab) {
        public Entry(Material material, Vec3 rgb) {
            this(material, clamp01(rgb), toOklab(clamp01(rgb)));
        }
    }
}
