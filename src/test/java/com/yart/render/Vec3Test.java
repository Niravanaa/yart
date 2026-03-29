package com.yart.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Vec3Test {

    @Test
    void addAndSubShouldWork() {
        Vec3 a = new Vec3(1, 2, 3);
        Vec3 b = new Vec3(4, 5, 6);

        assertEquals(new Vec3(5, 7, 9), a.add(b));
        assertEquals(new Vec3(-3, -3, -3), a.sub(b));
    }

    @Test
    void dotAndCrossShouldWork() {
        Vec3 x = new Vec3(1, 0, 0);
        Vec3 y = new Vec3(0, 1, 0);

        assertEquals(0.0, x.dot(y), 1e-9);
        assertEquals(new Vec3(0, 0, 1), x.cross(y));
    }

    @Test
    void normalizeShouldHandleZeroAndUnitLength() {
        Vec3 zero = new Vec3(0, 0, 0);
        Vec3 n = new Vec3(3, 0, 4).normalize();

        assertEquals(zero, zero.normalize());
        assertEquals(1.0, n.length(), 1e-9);
    }

    @Test
    void rotationsShouldFollowRightHandedAxes() {
        Vec3 x = new Vec3(1, 0, 0);
        Vec3 y = new Vec3(0, 1, 0);

        Vec3 xAroundY = x.rotateY(Math.PI / 2.0);
        Vec3 yAroundX = y.rotateX(Math.PI / 2.0);

        assertEquals(0.0, xAroundY.x(), 1e-9);
        assertEquals(0.0, xAroundY.y(), 1e-9);
        assertEquals(-1.0, xAroundY.z(), 1e-9);

        assertEquals(0.0, yAroundX.x(), 1e-9);
        assertEquals(0.0, yAroundX.y(), 1e-9);
        assertEquals(1.0, yAroundX.z(), 1e-9);
    }
}
