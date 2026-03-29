package com.yart.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriangleTest {

    @Test
    void deriveFaceColorFromNormalDirection() {
        Triangle positiveX = new Triangle(
                new Vec3(0, 0, 0),
                new Vec3(0, 1, 0),
                new Vec3(0, 0, -1));
        Triangle negativeY = new Triangle(
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, 0, 1));
        Triangle positiveZ = new Triangle(
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, 1, 0));

        assertEquals(Triangle.FACE_GREEN, positiveX.faceColorIndex());
        assertEquals(Triangle.FACE_BLUE, negativeY.faceColorIndex());
        assertEquals(Triangle.FACE_CYAN, positiveZ.faceColorIndex());
    }

    @Test
    void constructorsClampColorIndexAndProvideDefaults() {
        Triangle low = new Triangle(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), -100);
        Triangle high = new Triangle(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 999);

        assertEquals(new Vec3(0.10, 0.10, 0.10), low.ambientColor());
        assertEquals(new Vec3(0.05, 0.05, 0.05), low.diffuseColor());
        assertEquals(new Vec3(0.45, 0.26, 0.64), high.diffuseColor());
    }

    @Test
    void normalAndRotationAreComputed() {
        Triangle tri = new Triangle(
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, 1, 0));

        Vec3 n = tri.normal();
        Triangle rotatedY = tri.rotateY(Math.PI / 2.0);
        Triangle rotatedX = tri.rotateX(Math.PI / 2.0);

        assertEquals(1.0, n.length(), 1e-9);
        assertEquals(Triangle.FACE_CYAN, tri.faceColorIndex());
        assertEquals(tri.faceColorIndex(), rotatedY.faceColorIndex());
        assertEquals(tri.faceColorIndex(), rotatedX.faceColorIndex());
        assertEquals(tri.ambientColor(), rotatedY.ambientColor());
        assertEquals(tri.diffuseColor(), rotatedX.diffuseColor());
    }
}
