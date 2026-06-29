package ar.edu.itba.simped.environment.neighbors;

import ar.edu.itba.simped.core.Vec2;

/**
 * Segmento de pared definido por dos endpoints.
 * Implementación original del Grupo 7.
 */
public final class Wall {

    private final Vec2 p1;
    private final Vec2 p2;

    public Wall(Vec2 p1, Vec2 p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public Vec2 p1() { return p1; }
    public Vec2 p2() { return p2; }

    public Vec2 closestPointTo(Vec2 p) {
        double dx = p2.x() - p1.x();
        double dy = p2.y() - p1.y();
        double len2 = dx * dx + dy * dy;
        if (len2 == 0.0) return p1;
        double t = ((p.x() - p1.x()) * dx + (p.y() - p1.y()) * dy) / len2;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;
        return new Vec2(p1.x() + t * dx, p1.y() + t * dy);
    }

    public double distanceTo(Vec2 p) {
        return closestPointTo(p).distanceTo(p);
    }
}
