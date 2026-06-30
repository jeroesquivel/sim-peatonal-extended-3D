package ar.edu.itba.simped.core;

/**
 * Vector 2D inmutable. Usado para posiciones, velocidades, foot-targets, etc.
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0.0, 0.0);

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 sub(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    public double norm() {
        return Math.sqrt(x * x + y * y);
    }

    public double distanceTo(Vec2 other) {
        return sub(other).norm();
    }

    public double dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    public Vec2 normalized() {
        double n = norm();
        if (n < 1e-12) return ZERO;
        return scale(1.0 / n);
    }

    public Vec2 clampNorm(double maxNorm) {
        double n = norm();
        if (n <= maxNorm || n < 1e-12) return this;
        return scale(maxNorm / n);
    }

    /** Eleva este punto planar a 3D agregándole la altura {@code z}. */
    public Vec3 withZ(double z) {
        return new Vec3(x, y, z);
    }
}
