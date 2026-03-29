package com.yart.render;

import java.util.ArrayList;
import java.util.List;

public final class ObjModel {
    private final List<Triangle> triangles;

    public ObjModel(List<Triangle> triangles) {
        this.triangles = List.copyOf(triangles);
    }

    public List<Triangle> triangles() {
        return triangles;
    }

    public ObjModel centeredAndUnitScaled() {
        if (triangles.isEmpty()) {
            return this;
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
}
