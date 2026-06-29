package ar.edu.itba.simped.core.ports;

/**
 * Módulo 2 del contract v4 (SimulationLoop).
 *
 * <p>Driver maestro. Avanza pasos discretos de tiempo, inicializa el
 * Environment una vez en t=0, y dispara todos los updates per-step.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I3: dispara cada {@link Agent} en cada {@code dt}.</li>
 *   <li>I4: inicializa {@link Environment} en t=0.</li>
 *   <li>I5: escribe a {@link OutputSink} cada {@code dtout}.</li>
 * </ul>
 * </p>
 *
 * <p>Propiedades (contract §2):
 * <ul>
 *   <li>{@code dt}: tamaño del timestep.</li>
 *   <li>{@code dtout}: intervalo de sampling de output.</li>
 *   <li>{@code t_total}: tiempo total de simulación.</li>
 * </ul>
 * Estos parámetros vienen del CSV {@code SIM_PARAMS.csv}.</p>
 */
public interface SimulationDriver {

    /** Corre la simulación de principio a fin. Bloquea hasta {@code t_total}. */
    void run();
}
