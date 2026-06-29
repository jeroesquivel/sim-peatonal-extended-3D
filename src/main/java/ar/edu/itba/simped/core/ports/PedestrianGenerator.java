package ar.edu.itba.simped.core.ports;

import java.util.List;

/**
 * Sub-módulo 5.4 del contract v4.
 *
 * <p>Crea agentes nuevos según una flowrate o una instant occupation schedule.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I7:  escribe estado inicial (posición, velocidad, agent_type) en
 *       {@link ar.edu.itba.simped.core.AgentState} (init-only).</li>
 *   <li>I21: asigna instancia de Plan al agente nuevo.</li>
 *   <li>I22: extrae template de Plan ("provide a plan for init agents").</li>
 * </ul>
 * </p>
 */
public interface PedestrianGenerator {

    /**
     * Agentes a spawnear al inicio de la simulación (modo instant_occupation).
     * Llamado una vez por SimulationLoop durante init.
     */
    List<Agent> spawnInitial();

    /**
     * Agentes a spawnear en el tick actual (modo flowrate). Llamado por
     * SimulationLoop en cada dt; puede devolver lista vacía.
     */
    List<Agent> spawnTick(double currentTime, double dt);
}
