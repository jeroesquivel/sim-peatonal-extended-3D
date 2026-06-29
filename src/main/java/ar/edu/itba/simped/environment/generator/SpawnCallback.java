package ar.edu.itba.simped.environment.generator;

import ar.edu.itba.simped.core.ports.Agent;

/**
 * Callback invocado al spawnear un agente (I7 + I21).
 * Permite registrar el agente en el SimulationLoop y asignarle un Plan.
 */
@FunctionalInterface
public interface SpawnCallback {
    void onSpawn(Agent agent);
}
