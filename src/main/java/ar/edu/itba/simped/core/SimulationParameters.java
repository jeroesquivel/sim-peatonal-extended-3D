package ar.edu.itba.simped.core;

/**
 * Parámetros globales de la simulación (2.a-c del contract v4).
 * Origen: {@code SIM_PARAMS.csv} (keys {@code dt}, {@code dt_out},
 * {@code t_total}).
 *
 * @param dt       timestep size [s].
 * @param dtOut    intervalo de muestreo de output [s].
 * @param tTotal   tiempo total de simulación [s].
 */
public record SimulationParameters(double dt, double dtOut, double tTotal) {

    public SimulationParameters {
        if (dt <= 0) {
            throw new IllegalArgumentException("dt must be > 0, got " + dt);
        }
        if (dtOut <= 0) {
            throw new IllegalArgumentException("dtOut must be > 0, got " + dtOut);
        }
        if (tTotal <= 0) {
            throw new IllegalArgumentException("tTotal must be > 0, got " + tTotal);
        }
    }
}
