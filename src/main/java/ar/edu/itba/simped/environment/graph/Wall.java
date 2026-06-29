package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;

/**
 * Segmento de pared definido por dos extremos.
 * Coordenada z del CSV se ignora (simulación 2D).
 */
public record Wall(Vec2 p1, Vec2 p2) {
}
