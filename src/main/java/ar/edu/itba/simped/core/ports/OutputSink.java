package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.AgentState;

/**
 * Módulo 3 del contract v4 (Output).
 *
 * <p>Archivo de texto plano escrito por {@link SimulationDriver} cada
 * {@code dtout} (I5).</p>
 *
 * <p>Formato de fila (ver README §Formato de Output):
 * {@code tout; x; y; vx; vy; state}, una fila por agente por output step.</p>
 */
public interface OutputSink extends AutoCloseable {

    /**
     * I5: emite una fila por cada agente en {@code agents} con tiempo
     * {@code tout}.
     */
    void writeStep(double tout, Iterable<AgentState> agents);

    @Override
    void close();
}
