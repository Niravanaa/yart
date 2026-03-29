package com.yart.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjModelTest {

    @Test
    void centeredAndUnitScaledReturnsSameInstanceForEmptyModel() {
        ObjModel model = new ObjModel(List.of());

        assertSame(model, model.centeredAndUnitScaled());
    }

    @Test
    void centeredAndUnitScaledRecentersAndScalesGeometry() {
        Triangle tri = new Triangle(
                new Vec3(0, 0, 0),
                new Vec3(2, 0, 0),
                new Vec3(0, 2, 0),
                Triangle.FACE_RED,
                new Vec3(0.2, 0.2, 0.2),
                new Vec3(0.8, 0.1, 0.1));
        ObjModel model = new ObjModel(List.of(tri));

        ObjModel normalized = model.centeredAndUnitScaled();
        Triangle transformed = normalized.triangles().getFirst();

        assertNotSame(model, normalized);
        assertEquals(new Vec3(-0.5, -0.5, 0.0), transformed.a());
        assertEquals(new Vec3(0.5, -0.5, 0.0), transformed.b());
        assertEquals(new Vec3(-0.5, 0.5, 0.0), transformed.c());
        assertEquals(tri.faceColorIndex(), transformed.faceColorIndex());
        assertEquals(tri.ambientColor(), transformed.ambientColor());
        assertEquals(tri.diffuseColor(), transformed.diffuseColor());
    }

    @Test
    void constructorDefensivelyCopiesTriangles() {
        List<Triangle> source = new ArrayList<>();
        source.add(new Triangle(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0)));

        ObjModel model = new ObjModel(source);
        source.clear();
        Triangle extra = new Triangle(new Vec3(2, 0, 0), new Vec3(2, 1, 0), new Vec3(2, 0, 1));

        assertEquals(1, model.triangles().size());
        assertThrows(UnsupportedOperationException.class, () -> model.triangles().add(extra));
    }
}
