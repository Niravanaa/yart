package com.yart.render;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ObjLoader {
    private ObjLoader() {
    }

    public static ObjModel load(File file) throws IOException {
        List<Vec3> vertices = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        Map<String, MaterialColors> materials = new HashMap<>();
        String currentMaterialName = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) {
                        continue;
                    }
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    vertices.add(new Vec3(x, y, z));
                    continue;
                }

                if (line.startsWith("mtllib ")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length == 2 && !parts[1].isBlank()) {
                        File mtlFile = new File(file.getParentFile(), parts[1].trim());
                        if (mtlFile.exists() && mtlFile.isFile()) {
                            materials.putAll(loadMaterials(mtlFile));
                        }
                    }
                    continue;
                }

                if (line.startsWith("usemtl ")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length == 2 && !parts[1].isBlank()) {
                        currentMaterialName = parts[1].trim();
                    }
                    continue;
                }

                if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) {
                        continue;
                    }

                    int[] indices = new int[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        indices[i - 1] = parseVertexIndex(parts[i], vertices.size());
                    }

                    // Triangulate n-gons using a fan from the first vertex.
                    for (int i = 1; i < indices.length - 1; i++) {
                        Vec3 a = vertices.get(indices[0]);
                        Vec3 b = vertices.get(indices[i]);
                        Vec3 c = vertices.get(indices[i + 1]);
                        Triangle base = new Triangle(a, b, c);
                        MaterialColors colors = currentMaterialName == null ? null : materials.get(currentMaterialName);
                        if (colors == null) {
                            triangles.add(base);
                            continue;
                        }

                        triangles.add(new Triangle(
                                base.a(),
                                base.b(),
                                base.c(),
                                base.faceColorIndex(),
                                colors.ambient(),
                                colors.diffuse()));
                    }
                }
            }
        }

        return new ObjModel(triangles).centeredAndUnitScaled();
    }

    private static int parseVertexIndex(String token, int vertexCount) {
        String[] split = token.split("/");
        int raw = Integer.parseInt(split[0]);

        if (raw > 0) {
            return raw - 1;
        }

        // OBJ supports negative indices as relative to the end of the vertex list.
        return vertexCount + raw;
    }

    private static Map<String, MaterialColors> loadMaterials(File mtlFile) throws IOException {
        Map<String, MaterialColors> materials = new HashMap<>();

        String currentName = null;
        Vec3 ambient = new Vec3(0.10, 0.10, 0.10);
        Vec3 diffuse = new Vec3(1.0, 1.0, 1.0);

        try (BufferedReader reader = new BufferedReader(new FileReader(mtlFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("newmtl ")) {
                    if (currentName != null) {
                        materials.put(currentName, new MaterialColors(clamp01(ambient), clamp01(diffuse)));
                    }

                    String[] parts = line.split("\\s+", 2);
                    if (parts.length < 2 || parts[1].isBlank()) {
                        currentName = null;
                    } else {
                        currentName = parts[1].trim();
                    }
                    ambient = new Vec3(0.10, 0.10, 0.10);
                    diffuse = new Vec3(1.0, 1.0, 1.0);
                    continue;
                }

                if (currentName == null) {
                    continue;
                }

                if (lower.startsWith("ka ")) {
                    Vec3 parsed = parseMtlRgb(line);
                    if (parsed != null) {
                        ambient = parsed;
                    }
                    continue;
                }

                if (lower.startsWith("kd ")) {
                    Vec3 parsed = parseMtlRgb(line);
                    if (parsed != null) {
                        diffuse = parsed;
                    }
                }
            }
        }

        if (currentName != null) {
            materials.put(currentName, new MaterialColors(clamp01(ambient), clamp01(diffuse)));
        }

        return materials;
    }

    private static Vec3 parseMtlRgb(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 4) {
            return null;
        }

        try {
            return new Vec3(
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]));
        } catch (NumberFormatException ignored) {
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

    private record MaterialColors(Vec3 ambient, Vec3 diffuse) {
    }
}
