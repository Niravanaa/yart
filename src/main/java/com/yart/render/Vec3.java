package com.yart.render;

public record Vec3(double x, double y, double z) {
    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 sub(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 mul(double scalar) {
        return new Vec3(x * scalar, y * scalar, z * scalar);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vec3 normalize() {
        double len = length();
        if (len == 0.0) {
            return this;
        }
        return mul(1.0 / len);
    }

    public Vec3 rotateY(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(
                x * cos + z * sin,
                y,
                -x * sin + z * cos);
    }

    public Vec3 rotateX(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(
                x,
                y * cos - z * sin,
                y * sin + z * cos);
    }
}
