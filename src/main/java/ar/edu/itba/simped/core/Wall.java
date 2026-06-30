package ar.edu.itba.simped.core;

/**
 * Segmento de pared definido por dos extremos, perteneciente a una planta.
 *
 * <p>La pared es <b>planar</b>: sus dos extremos viven en la misma planta
 * {@code z} (ver D3 en {@code .claude/DECISIONES.md}). La forma se mantiene en
 * {@link Vec2}; {@code z} indica a qué planta pertenece (default 0 = planta baja
 * en escenarios de una sola planta).</p>
 */
public record Wall(Vec2 p1, Vec2 p2, double z) {

    /** Pared en la planta baja ({@code z = 0}). */
    public Wall(Vec2 p1, Vec2 p2) {
        this(p1, p2, 0.0);
    }
}
