package ar.edu.itba.simped.core;

import java.util.random.RandomGenerator;

/**
 * Distribución de probabilidad usada por dwell times, service times,
 * radios de agente, etc. Sub-tipos: {@link Uniform}, {@link Gaussian},
 * {@link Deterministic}.
 */
public sealed interface Distribution permits Uniform, Gaussian, Deterministic {

    /** Devuelve una muestra de la distribución usando el {@code rng} dado. */
    double sample(RandomGenerator rng);
}
