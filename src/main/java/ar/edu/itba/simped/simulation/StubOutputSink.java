package ar.edu.itba.simped.simulation;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.ports.OutputSink;

/**
 * STUB - implementación del OutputWriter pendiente.
 *
 * <p>Spec del formato (ver README §Formato de Output):
 * {@code tout; x; y; vx; vy; state} — una fila por agente por output step.</p>
 */
public final class StubOutputSink implements OutputSink {

    @Override
    public void writeStep(double tout, Iterable<AgentState> agents) {
        // TODO: emitir filas al archivo de salida.
    }

    @Override
    public void close() {
        // TODO: cerrar el writer.
    }
}
