package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;

/**
 * Segmento de pared del grafo, perteneciente a una planta {@code z} (paso 4).
 * La forma es planar ({@link Vec2}); {@code z} indica a qué planta pertenece,
 * para que la visibilidad y la generación del grafo operen por planta.
 */
public record Wall(Vec2 p1, Vec2 p2, double z) {

    /** Pared en la planta baja ({@code z = 0}). */
    public Wall(Vec2 p1, Vec2 p2) {
        this(p1, p2, 0.0);
    }
}
