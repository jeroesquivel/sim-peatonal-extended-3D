package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.AgentState;

/**
 * Contenedor agente (módulo 4 del contract v4). Una instancia por peatón
 * simulado. Agrupa los sub-módulos AgentState, Plan, Sensors, StateMachine,
 * PreOM y OperationalModel.
 *
 * <p>Avanzado por SimulationLoop en cada dt vía I3.</p>
 */
public interface Agent {

    /** I3: avanza el agente un paso de tamaño {@code dt}. */
    void step(double dt);

    /** Estado kinemático/comportamental actual del agente. */
    AgentState state();

    /** Identificador único del agente. */
    int id();
}
