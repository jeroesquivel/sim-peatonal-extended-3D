package ar.edu.itba.simped.core;

/**
 * Segmento 2D inmutable entre dos puntos. Usado para paredes, salidas y
 * tramos de cola en {@link ar.edu.itba.simped.core.ports.Geometry} (G3).
 */
public record Segment(Vec2 a, Vec2 b) {

    public Vec2 midpoint() {
        return new Vec2((a.x() + b.x()) / 2.0, (a.y() + b.y()) / 2.0);
    }

    public double length() {
        return a.distanceTo(b);
    }

    public Vec2 closestPointTo(Vec2 p) {
        Vec2 ab = b.sub(a);
        double lengthSquared = ab.dot(ab);
        if (lengthSquared <= 1e-12) {
            return a;
        }
        double t = p.sub(a).dot(ab) / lengthSquared;
        double clampedT = Math.max(0.0, Math.min(1.0, t));
        return a.add(ab.scale(clampedT));
    }
}
