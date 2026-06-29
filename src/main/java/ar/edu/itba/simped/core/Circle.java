package ar.edu.itba.simped.core;

public record Circle(Vec2 center, double radius) implements Shape {

    public Circle {
        if (radius < 0) {
            throw new IllegalArgumentException("Circle radius must be non-negative, got " + radius);
        }
    }

    @Override
    public Vec2 centroid() {
        return center;
    }

    @Override
    public boolean contains(Vec2 p) {
        return center.distanceTo(p) <= radius;
    }
}
