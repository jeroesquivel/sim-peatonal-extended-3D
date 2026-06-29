package ar.edu.itba.simped.core;

import java.util.random.RandomGenerator;

/**
 * Distribución degenerada: {@code sample(rng)} siempre devuelve
 * {@code value}. Útil para modelar tiempos fijos (e.g. servidores con
 * {@code service_time_std=0} del Formato A: media + std=0 → constante).
 */
public record Deterministic(double value) implements Distribution {

    @Override
    public double sample(RandomGenerator rng) {
        return value;
    }
}
