package ar.edu.itba.simped.core;

/**
 * Rectángulo eje-alineado definido por dos esquinas opuestas {@code a} y
 * {@code c}. El orden de las esquinas no importa para {@link #centroid()}
 * ni {@link #contains(Vec2)}.
 */
public record Rectangle(Vec2 a, Vec2 c) implements Shape {

    @Override
    public Vec2 centroid() {
        return new Vec2((a.x() + c.x()) / 2.0, (a.y() + c.y()) / 2.0);
    }

    @Override
    public boolean contains(Vec2 p) {
        double xmin = Math.min(a.x(), c.x());
        double xmax = Math.max(a.x(), c.x());
        double ymin = Math.min(a.y(), c.y());
        double ymax = Math.max(a.y(), c.y());
        return p.x() >= xmin && p.x() <= xmax && p.y() >= ymin && p.y() <= ymax;
    }

    public double width() {
        return Math.abs(c.x() - a.x());
    }

    public double height() {
        return Math.abs(c.y() - a.y());
    }
}
