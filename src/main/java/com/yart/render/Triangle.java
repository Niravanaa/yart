package com.yart.render;

public record Triangle(Vec3 a, Vec3 b, Vec3 c, int faceColorIndex, Vec3 ambientColor, Vec3 diffuseColor) {
    public static final int FACE_RED = 10;
    public static final int FACE_GREEN = 11;
    public static final int FACE_YELLOW = 12;
    public static final int FACE_BLUE = 13;
    public static final int FACE_CYAN = 14;
    public static final int FACE_PURPLE = 15;

    private static final Vec3[] FACE_COLORS = {
            new Vec3(0.05, 0.05, 0.05),
            new Vec3(0.09, 0.09, 0.09),
            new Vec3(0.35, 0.31, 0.27),
            new Vec3(0.31, 0.33, 0.35),
            new Vec3(0.42, 0.42, 0.42),
            new Vec3(0.53, 0.47, 0.42),
            new Vec3(0.58, 0.58, 0.58),
            new Vec3(0.69, 0.69, 0.69),
            new Vec3(0.82, 0.77, 0.70),
            new Vec3(0.58, 0.58, 0.58),
            new Vec3(0.56, 0.13, 0.13),
            new Vec3(0.29, 0.42, 0.23),
            new Vec3(0.94, 0.70, 0.20),
            new Vec3(0.18, 0.31, 0.73),
            new Vec3(0.08, 0.50, 0.55),
            new Vec3(0.45, 0.26, 0.64)
    };

    public Triangle(Vec3 a, Vec3 b, Vec3 c) {
        this(a, b, c, deriveFaceColorIndex(a, b, c));
    }

    public Triangle(Vec3 a, Vec3 b, Vec3 c, int faceColorIndex) {
        this(a, b, c, faceColorIndex, new Vec3(0.10, 0.10, 0.10), faceColorToRgb(faceColorIndex));
    }

    public Vec3 normal() {
        return b.sub(a).cross(c.sub(a)).normalize();
    }

    public Triangle rotateY(double angle) {
        return new Triangle(a.rotateY(angle), b.rotateY(angle), c.rotateY(angle), faceColorIndex, ambientColor,
                diffuseColor);
    }

    public Triangle rotateX(double angle) {
        return new Triangle(a.rotateX(angle), b.rotateX(angle), c.rotateX(angle), faceColorIndex, ambientColor,
                diffuseColor);
    }

    private static Vec3 faceColorToRgb(int index) {
        int clamped = Math.max(0, Math.min(FACE_COLORS.length - 1, index));
        return FACE_COLORS[clamped];
    }

    private static int deriveFaceColorIndex(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 n = b.sub(a).cross(c.sub(a)).normalize();

        double ax = Math.abs(n.x());
        double ay = Math.abs(n.y());
        double az = Math.abs(n.z());

        if (ax >= ay && ax >= az) {
            return n.x() >= 0.0 ? FACE_RED : FACE_GREEN;
        }
        if (ay >= ax && ay >= az) {
            return n.y() >= 0.0 ? FACE_YELLOW : FACE_BLUE;
        }
        return n.z() >= 0.0 ? FACE_CYAN : FACE_PURPLE;
    }
}
