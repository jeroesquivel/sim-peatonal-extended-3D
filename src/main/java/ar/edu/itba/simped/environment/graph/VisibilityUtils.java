package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;

import java.util.List;

/**
 * Utilidades geométricas para tests de intersección de segmentos
 * y visibilidad entre puntos en presencia de paredes.
 */
public final class VisibilityUtils {

    private static final double EPS = 1e-9;

    private VisibilityUtils() {
    }

    /**
     * Producto cruzado 2D del vector (o→a) × (o→b).
     * Positivo si a está a la izquierda de o→b, negativo a la derecha, 0 si colineal.
     */
    static double cross(Vec2 o, Vec2 a, Vec2 b) {
        return (a.x() - o.x()) * (b.y() - o.y())
             - (a.y() - o.y()) * (b.x() - o.x());
    }

    private static boolean onSegment(Vec2 p, Vec2 a, Vec2 b) {
        return p.x() >= Math.min(a.x(), b.x()) - EPS
            && p.x() <= Math.max(a.x(), b.x()) + EPS
            && p.y() >= Math.min(a.y(), b.y()) - EPS
            && p.y() <= Math.max(a.y(), b.y()) + EPS;
    }

    /**
     * Determina si los segmentos (a1,a2) y (b1,b2) se intersectan.
     * Incluye casos de contacto en vértices y solapamiento colineal.
     * Cualquier contacto cuenta como intersección (política: tocar pared = no visible).
     */
    public static boolean segmentsIntersect(Vec2 a1, Vec2 a2, Vec2 b1, Vec2 b2) {
        double d1 = cross(b1, b2, a1);
        double d2 = cross(b1, b2, a2);
        double d3 = cross(a1, a2, b1);
        double d4 = cross(a1, a2, b2);

        if (((d1 > EPS && d2 < -EPS) || (d1 < -EPS && d2 > EPS))
         && ((d3 > EPS && d4 < -EPS) || (d3 < -EPS && d4 > EPS))) {
            return true;
        }

        if (Math.abs(d1) <= EPS && onSegment(a1, b1, b2)) return true;
        if (Math.abs(d2) <= EPS && onSegment(a2, b1, b2)) return true;
        if (Math.abs(d3) <= EPS && onSegment(b1, a1, a2)) return true;
        if (Math.abs(d4) <= EPS && onSegment(b2, a1, a2)) return true;

        return false;
    }

    /**
     * Dos puntos son visibles si el segmento entre ellos no intersecta ninguna pared.
     */
    public static boolean isVisible(Vec2 from, Vec2 to, List<Wall> walls) {
        for (Wall wall : walls) {
            if (segmentsIntersect(from, to, wall.p1(), wall.p2())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Distancia mínima desde un punto a un segmento (a,b).
     */
    public static double pointToSegmentDistance(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.sub(a);
        double lenSq = ab.dot(ab);
        if (lenSq < EPS * EPS) {
            return p.distanceTo(a);
        }
        double t = p.sub(a).dot(ab) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec2 proj = a.add(ab.scale(t));
        return p.distanceTo(proj);
    }

    /**
     * Distancia mínima de un punto a cualquier pared.
     */
    public static double minDistanceToWalls(Vec2 point, List<Wall> walls) {
        double min = Double.MAX_VALUE;
        for (Wall w : walls) {
            double d = pointToSegmentDistance(point, w.p1(), w.p2());
            if (d < min) min = d;
        }
        return min;
    }

    /**
     * Interpolación lineal entre dos puntos: result = a + t*(b-a).
     */
    public static Vec2 lerp(Vec2 a, Vec2 b, double t) {
        return new Vec2(
            a.x() + t * (b.x() - a.x()),
            a.y() + t * (b.y() - a.y())
        );
    }
}
