package ar.edu.itba.simped.core;

/**
 * Forma 2D inmutable. Subtipos cubren los figure_type del CSV
 * TARGETS y los rectángulos de SERVERS/GENERATORS.
 */
public sealed interface Shape permits Circle, Rectangle {

    Vec2 centroid();

    boolean contains(Vec2 p);
}
