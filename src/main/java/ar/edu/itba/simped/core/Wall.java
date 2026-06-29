package ar.edu.itba.simped.core;

/**
 * Segmento de pared definido por dos extremos.
 *
 * <p>Producido por G3 (Geometry) y consumido por G8 (Graph, I17) y
 * G5 (CIM, I19). Compatible con el shape ya usado por G5/G8 en sus
 * Wall locales — la centralización en core permite que esos grupos
 * dropeen el suyo cuando estimen conveniente.</p>
 */
public record Wall(Vec2 p1, Vec2 p2) {
}
