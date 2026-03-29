package com.yart.render;

import org.joml.Matrix4d;
import org.joml.Vector4d;

import java.util.Arrays;

public final class RayTracer {
    private static final double NEAR_PLANE = 0.1;
    private static final double FAR_PLANE = 100.0;
    private static final double DEFAULT_FOV_RADIANS = Math.toRadians(65.0);
    private static final Vec3 LIGHT_DIR = new Vec3(0.55, 0.8, -0.35).normalize();

    private final int width;
    private final int height;
    private final BlockPalette palette;
    private final int backgroundIndex;

    public RayTracer(int width, int height, BlockPalette palette) {
        this.width = width;
        this.height = height;
        this.palette = palette;
        this.backgroundIndex = palette.backgroundIndex();
    }

        public int[] render(ObjModel model, double yaw, double pitch, double zoom, boolean lightingEnabled,
            boolean wireframeEnabled, double fovRadians) {
        int[] frame = new int[width * height];
        Arrays.fill(frame, backgroundIndex);

        double[] depthBuffer = new double[width * height];
        Arrays.fill(depthBuffer, Double.POSITIVE_INFINITY);

        double aspect = (double) width / (double) height;
        Matrix4d modelMatrix = new Matrix4d().rotateY(yaw).rotateX(pitch);
        Matrix4d viewMatrix = new Matrix4d().lookAt(0.0, 0.0, -zoom, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        double activeFov = Double.isFinite(fovRadians) ? fovRadians : DEFAULT_FOV_RADIANS;
        Matrix4d projectionMatrix = new Matrix4d().perspective(activeFov, aspect, NEAR_PLANE, FAR_PLANE);

        Matrix4d mvp = projectionMatrix.mul(viewMatrix, new Matrix4d()).mul(modelMatrix);

        for (Triangle triangle : model.triangles()) {
            Triangle rotated = triangle.rotateY(yaw).rotateX(pitch);
            rasterizeTriangle(mvp, triangle, rotated.normal(), frame, depthBuffer, lightingEnabled, wireframeEnabled);
        }

        return frame;
    }

        private void rasterizeTriangle(Matrix4d mvp, Triangle tri, Vec3 worldNormal, int[] frame, double[] depthBuffer,
            boolean lightingEnabled, boolean wireframeEnabled) {
        Vector4d clipA = mvp.transform(new Vector4d(tri.a().x(), tri.a().y(), tri.a().z(), 1.0));
        Vector4d clipB = mvp.transform(new Vector4d(tri.b().x(), tri.b().y(), tri.b().z(), 1.0));
        Vector4d clipC = mvp.transform(new Vector4d(tri.c().x(), tri.c().y(), tri.c().z(), 1.0));

        if (clipA.w <= 1e-9 || clipB.w <= 1e-9 || clipC.w <= 1e-9) {
            return;
        }

        ScreenVertex a = toScreenVertex(clipA);
        ScreenVertex b = toScreenVertex(clipB);
        ScreenVertex c = toScreenVertex(clipC);

        double area = edge(a.x, a.y, b.x, b.y, c.x, c.y);
        if (Math.abs(area) < 1e-9) {
            return;
        }

        int minX = clampToInt((int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))), 0, width - 1);
        int maxX = clampToInt((int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))), 0, width - 1);
        int minY = clampToInt((int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))), 0, height - 1);
        int maxY = clampToInt((int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))), 0, height - 1);

        boolean ccw = area > 0.0;
        int unlitIndex = nearestPaletteIndex(tri.diffuseColor());
        int litIndex = lightingEnabled ? applyLighting(tri, worldNormal) : unlitIndex;
        int paletteIndex = lightingEnabled ? litIndex : unlitIndex;

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double sx = px + 0.5;
                double sy = py + 0.5;

                double w0 = edge(b.x, b.y, c.x, c.y, sx, sy);
                double w1 = edge(c.x, c.y, a.x, a.y, sx, sy);
                double w2 = edge(a.x, a.y, b.x, b.y, sx, sy);

                boolean inside = ccw
                        ? (w0 >= 0.0 && w1 >= 0.0 && w2 >= 0.0)
                        : (w0 <= 0.0 && w1 <= 0.0 && w2 <= 0.0);
                if (!inside) {
                    continue;
                }

                double alpha = w0 / area;
                double beta = w1 / area;
                double gamma = w2 / area;

                double depth = alpha * a.depth + beta * b.depth + gamma * c.depth;
                if (depth < 0.0 || depth > 1.0) {
                    continue;
                }

                int index = py * width + px;
                if (depth >= depthBuffer[index]) {
                    continue;
                }

                depthBuffer[index] = depth;
                frame[index] = paletteIndex;
            }
        }

        if (wireframeEnabled) {
            drawLine(a, b, unlitIndex, frame, depthBuffer);
            drawLine(b, c, unlitIndex, frame, depthBuffer);
            drawLine(c, a, unlitIndex, frame, depthBuffer);
        }
    }

    private void drawLine(ScreenVertex start, ScreenVertex end, int paletteIndex, int[] frame, double[] depthBuffer) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));

        if (steps <= 0) {
            int px = clampToInt((int) Math.round(start.x), 0, width - 1);
            int py = clampToInt((int) Math.round(start.y), 0, height - 1);
            int index = py * width + px;
            if (start.depth < depthBuffer[index]) {
                depthBuffer[index] = start.depth;
                frame[index] = paletteIndex;
            }
            return;
        }

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            int px = clampToInt((int) Math.round(start.x + dx * t), 0, width - 1);
            int py = clampToInt((int) Math.round(start.y + dy * t), 0, height - 1);
            double depth = start.depth + (end.depth - start.depth) * t;
            if (depth < 0.0 || depth > 1.0) {
                continue;
            }

            int index = py * width + px;
            if (depth >= depthBuffer[index]) {
                continue;
            }

            depthBuffer[index] = depth;
            frame[index] = paletteIndex;
        }
    }

    private int applyLighting(Triangle triangle, Vec3 normal) {
        double diffuse = Math.max(0.0, normal.normalize().dot(LIGHT_DIR));
        Vec3 shaded = triangle.ambientColor().add(triangle.diffuseColor().mul(diffuse));
        return nearestPaletteIndex(shaded);
    }

    private int nearestPaletteIndex(Vec3 color) {
        return palette.nearestIndex(color);
    }

    private ScreenVertex toScreenVertex(Vector4d clip) {
        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;
        double ndcZ = clip.z / clip.w;

        double screenX = (ndcX * 0.5 + 0.5) * (width - 1);
        double screenY = (1.0 - (ndcY * 0.5 + 0.5)) * (height - 1);
        double depth = ndcZ * 0.5 + 0.5;

        return new ScreenVertex(screenX, screenY, depth);
    }

    private static double edge(double ax, double ay, double bx, double by, double px, double py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static int clampToInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScreenVertex(double x, double y, double depth) {
    }
}
