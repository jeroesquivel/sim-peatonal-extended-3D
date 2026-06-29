package ar.edu.itba.simped.environment.servers.model;

import ar.edu.itba.simped.core.Vec2;

/**
 * Axis-aligned rectangle, built from two opposite corners (the SERVERS.csv
 * block format). Coordinates are normalised so {@code min <= max} on both axes.
 */
public record Rectangle(double minX, double minY, double maxX, double maxY) {

    public static Rectangle ofCorners(Vec2 a, Vec2 b) {
        return new Rectangle(
                Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()));
    }

    public Vec2 centroid() {
        return new Vec2((minX + maxX) / 2.0, (minY + maxY) / 2.0);
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public boolean contains(Vec2 p) {
        return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY;
    }
}
