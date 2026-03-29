package com.yart.render;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class BlockScreen {
    private final World world;
    private final int width;
    private final int height;
    private final float pixelScale;
    private final BlockPalette palette;
    private final BlockDisplay[] pixels;
    private final int[] previousFrame;

    private BlockScreen(World world, int width, int height, float pixelScale, BlockPalette palette,
            BlockDisplay[] pixels) {
        this.world = world;
        this.width = width;
        this.height = height;
        this.pixelScale = pixelScale;
        this.palette = palette;
        this.pixels = pixels;
        this.previousFrame = new int[width * height];
        for (int i = 0; i < previousFrame.length; i++) {
            previousFrame[i] = -1;
        }
    }

    public static BlockScreen spawnInFrontOf(Player player, int width, int height, float pixelScale, double distance,
            BlockPalette palette) {
        World world = player.getWorld();
        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector worldUp = new Vector(0.0, 1.0, 0.0);
        Vector right = forward.clone().crossProduct(worldUp).normalize();

        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1.0, 0.0, 0.0);
        }

        Vector up = right.clone().crossProduct(forward).normalize();
        Location center = player.getEyeLocation().add(forward.multiply(distance));
        int backgroundIndex = palette.backgroundIndex();

        BlockDisplay[] displays = new BlockDisplay[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double ox = (x - (width - 1) * 0.5) * pixelScale;
                double oy = ((height - 1) * 0.5 - y) * pixelScale;

                Location pixelLocation = center.clone()
                        .add(right.clone().multiply(ox))
                        .add(up.clone().multiply(oy));

                BlockDisplay display = world.spawn(pixelLocation, BlockDisplay.class, spawned -> {
                    spawned.setInterpolationDuration(0);
                    spawned.setInterpolationDelay(0);
                    spawned.setViewRange(32.0f);
                    spawned.setBillboard(Display.Billboard.FIXED);
                    spawned.setTransformation(new Transformation(
                            new Vector3f(0.0f, 0.0f, 0.0f),
                            new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                            new Vector3f(pixelScale, pixelScale, 0.08f),
                            new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)));
                    spawned.setBlock(Bukkit.createBlockData(palette.materialAt(backgroundIndex)));
                });

                displays[y * width + x] = display;
            }
        }

        return new BlockScreen(world, width, height, pixelScale, palette, displays);
    }

    public void applyFrame(int[] frame) {
        if (frame.length != width * height) {
            throw new IllegalArgumentException("Frame size does not match screen dimensions.");
        }

        for (int i = 0; i < frame.length; i++) {
            int index = frame[i];
            if (previousFrame[i] == index) {
                continue;
            }

            previousFrame[i] = index;
            BlockDisplay display = pixels[i];
            if (display == null || !display.isValid()) {
                continue;
            }

            display.setBlock(Bukkit.createBlockData(palette.materialAt(index)));
        }
    }

    public void destroy() {
        for (BlockDisplay display : pixels) {
            if (display != null && display.isValid() && display.getWorld().equals(world)) {
                display.remove();
            }
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float pixelScale() {
        return pixelScale;
    }
}
