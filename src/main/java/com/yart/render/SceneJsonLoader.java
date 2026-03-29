package com.yart.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SceneJsonLoader {
    private static final int SPHERE_STACKS = 12;
    private static final int SPHERE_SLICES = 24;

    private SceneJsonLoader() {
    }

    public static ObjModel load(File file) throws IOException {
        return loadScene(file).model();
    }

    public static LoadedScene loadScene(File file) throws IOException {
        JsonObject root;
        try (Reader reader = new FileReader(file)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Scene file root must be a JSON object.");
            }
            root = parsed.getAsJsonObject();
        }

        JsonArray geometry = getRequiredArray(root, "geometry");
        List<Triangle> triangles = new ArrayList<>();

        for (JsonElement element : geometry) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject shape = element.getAsJsonObject();
            if (shape.has("visible") && !shape.get("visible").getAsBoolean()) {
                continue;
            }

            String type = getRequiredString(shape, "type").toLowerCase(Locale.ROOT);
            switch (type) {
                case "rectangle" -> addRectangle(shape, triangles);
                case "sphere" -> addSphere(shape, triangles);
                case "triangle" -> addTriangle(shape, triangles);
                default -> {
                    // Ignore non-geometry entries from the COMP371 schema (e.g. lights) in this
                    // first pass.
                }
            }
        }

        Normalization normalization = computeNormalization(triangles);
        ObjModel normalizedModel = normalizeModel(triangles, normalization.center(), normalization.scale());
        SceneCameraPreset cameraPreset = parseCameraPreset(root, normalization.center(), normalization.scale());
        return new LoadedScene(normalizedModel, cameraPreset);
    }

    private static void addRectangle(JsonObject shape, List<Triangle> triangles) {
        Vec3 p1 = getRequiredVec3(shape, "p1");
        Vec3 p2 = getRequiredVec3(shape, "p2");
        Vec3 p3 = getRequiredVec3(shape, "p3");
        Vec3 p4 = getRequiredVec3(shape, "p4");
        Vec3 ac = getOptionalVec3(shape, "ac", new Vec3(0.10, 0.10, 0.10));
        Vec3 dc = getOptionalVec3(shape, "dc", new Vec3(1.0, 1.0, 1.0));

        triangles.add(createMaterialTriangle(p1, p2, p3, ac, dc));
        triangles.add(createMaterialTriangle(p1, p3, p4, ac, dc));
    }

    private static void addTriangle(JsonObject shape, List<Triangle> triangles) {
        Vec3 p1 = getRequiredVec3(shape, "p1");
        Vec3 p2 = getRequiredVec3(shape, "p2");
        Vec3 p3 = getRequiredVec3(shape, "p3");
        Vec3 ac = getOptionalVec3(shape, "ac", new Vec3(0.10, 0.10, 0.10));
        Vec3 dc = getOptionalVec3(shape, "dc", new Vec3(1.0, 1.0, 1.0));
        triangles.add(createMaterialTriangle(p1, p2, p3, ac, dc));
    }

    private static void addSphere(JsonObject shape, List<Triangle> triangles) {
        Vec3 center = getRequiredVec3(shape, "centre");
        double radius = getRequiredDouble(shape, "radius");
        Vec3 ac = getOptionalVec3(shape, "ac", new Vec3(0.10, 0.10, 0.10));
        Vec3 dc = getOptionalVec3(shape, "dc", new Vec3(1.0, 1.0, 1.0));
        if (radius <= 0.0) {
            return;
        }

        for (int stack = 0; stack < SPHERE_STACKS; stack++) {
            double phi0 = Math.PI * stack / SPHERE_STACKS;
            double phi1 = Math.PI * (stack + 1) / SPHERE_STACKS;

            for (int slice = 0; slice < SPHERE_SLICES; slice++) {
                double theta0 = 2.0 * Math.PI * slice / SPHERE_SLICES;
                double theta1 = 2.0 * Math.PI * (slice + 1) / SPHERE_SLICES;

                Vec3 v00 = spherePoint(center, radius, phi0, theta0);
                Vec3 v01 = spherePoint(center, radius, phi0, theta1);
                Vec3 v10 = spherePoint(center, radius, phi1, theta0);
                Vec3 v11 = spherePoint(center, radius, phi1, theta1);

                if (stack == 0) {
                    triangles.add(createMaterialTriangle(v00, v10, v11, ac, dc));
                    continue;
                }
                if (stack == SPHERE_STACKS - 1) {
                    triangles.add(createMaterialTriangle(v00, v10, v01, ac, dc));
                    continue;
                }

                triangles.add(createMaterialTriangle(v00, v10, v11, ac, dc));
                triangles.add(createMaterialTriangle(v00, v11, v01, ac, dc));
            }
        }
    }

    private static Triangle createMaterialTriangle(Vec3 a, Vec3 b, Vec3 c, Vec3 ac, Vec3 dc) {
        Triangle base = new Triangle(a, b, c);
        return new Triangle(base.a(), base.b(), base.c(), base.faceColorIndex(), clamp01(ac), clamp01(dc));
    }

    private static ObjModel normalizeModel(List<Triangle> triangles, Vec3 center, double scale) {
        List<Triangle> transformed = new ArrayList<>(triangles.size());
        for (Triangle tri : triangles) {
            transformed.add(new Triangle(
                    tri.a().sub(center).mul(scale),
                    tri.b().sub(center).mul(scale),
                    tri.c().sub(center).mul(scale),
                    tri.faceColorIndex(),
                    tri.ambientColor(),
                    tri.diffuseColor()));
        }
        return new ObjModel(transformed);
    }

    private static Normalization computeNormalization(List<Triangle> triangles) {
        if (triangles.isEmpty()) {
            return new Normalization(new Vec3(0.0, 0.0, 0.0), 1.0);
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Triangle tri : triangles) {
            Vec3[] points = { tri.a(), tri.b(), tri.c() };
            for (Vec3 point : points) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
                maxZ = Math.max(maxZ, point.z());
            }
        }

        Vec3 center = new Vec3(
                (minX + maxX) * 0.5,
                (minY + maxY) * 0.5,
                (minZ + maxZ) * 0.5);

        double dx = maxX - minX;
        double dy = maxY - minY;
        double dz = maxZ - minZ;
        double maxDimension = Math.max(dx, Math.max(dy, dz));
        double scale = maxDimension == 0.0 ? 1.0 : 1.0 / maxDimension;

        return new Normalization(center, scale);
    }

    private static SceneCameraPreset parseCameraPreset(JsonObject root, Vec3 normalizeCenter, double normalizeScale) {
        JsonObject output = getFirstOutputObject(root);
        if (output == null) {
            return null;
        }

        Vec3 cameraCenter = getOptionalVec3(output, "centre", getOptionalVec3(output, "center", null));
        if (cameraCenter == null) {
            return null;
        }

        Vec3 normalizedCamera = cameraCenter.sub(normalizeCenter).mul(normalizeScale);
        double zoom = normalizedCamera.length();
        if (zoom <= 1e-6) {
            return null;
        }

        Vec3 lookAt = getOptionalVec3(output, "lookat", null);
        Vec3 viewDirection;
        if (lookAt != null) {
            Vec3 normalizedLookAt = lookAt.sub(normalizeCenter).mul(normalizeScale);
            viewDirection = normalizedLookAt.sub(normalizedCamera).normalize();
            if (viewDirection.length() <= 1e-6) {
                viewDirection = normalizedCamera.mul(-1.0).normalize();
            }
        } else {
            viewDirection = normalizedCamera.mul(-1.0).normalize();
        }

        double yaw = Math.atan2(-viewDirection.x(), viewDirection.z());
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double vzPrime = -viewDirection.x() * sinYaw + viewDirection.z() * cosYaw;
        double pitch = Math.atan2(viewDirection.y(), vzPrime);

        Double fovDegrees = getOptionalDouble(output, "fov");
        double fovRadians = fovDegrees == null ? Math.toRadians(65.0) : Math.toRadians(clamp(fovDegrees, 20.0, 120.0));

        return new SceneCameraPreset(yaw, pitch, zoom, fovRadians);
    }

    private static JsonObject getFirstOutputObject(JsonObject root) {
        if (!root.has("output")) {
            return null;
        }

        JsonElement output = root.get("output");
        if (output.isJsonObject()) {
            return output.getAsJsonObject();
        }
        if (!output.isJsonArray()) {
            return null;
        }

        JsonArray outputArray = output.getAsJsonArray();
        if (outputArray.isEmpty() || !outputArray.get(0).isJsonObject()) {
            return null;
        }

        return outputArray.get(0).getAsJsonObject();
    }

    private static Vec3 spherePoint(Vec3 center, double radius, double phi, double theta) {
        double sinPhi = Math.sin(phi);
        double x = center.x() + radius * sinPhi * Math.cos(theta);
        double y = center.y() + radius * Math.cos(phi);
        double z = center.z() + radius * sinPhi * Math.sin(theta);
        return new Vec3(x, y, z);
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

    private static double getRequiredDouble(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return obj.get(key).getAsDouble();
    }

    private static Vec3 getRequiredVec3(JsonObject obj, String key) {
        JsonArray array = getRequiredArray(obj, key);
        if (array.size() < 3) {
            throw new IllegalArgumentException("Field " + key + " must have at least 3 numbers.");
        }
        return new Vec3(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble());
    }

    private static Vec3 getOptionalVec3(JsonObject obj, String key, Vec3 fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return fallback;
        }
        JsonArray array = obj.getAsJsonArray(key);
        if (array.size() < 3) {
            return fallback;
        }
        return new Vec3(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble());
    }

    private static Double getOptionalDouble(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return null;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Vec3 clamp01(Vec3 color) {
        return new Vec3(
                clamp(color.x(), 0.0, 1.0),
                clamp(color.y(), 0.0, 1.0),
                clamp(color.z(), 0.0, 1.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Normalization(Vec3 center, double scale) {
    }
}
