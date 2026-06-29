package ar.edu.itba.simped.core.ports;

import java.util.List;

/**
 * Contexto estático y dinámico compartido por todos los agentes
 * (módulo 5 del contract v4). Inicializado una sola vez por SimulationLoop
 * en t=0 vía I4.
 */
public interface Environment {

    /**
     * I4: ejecuta la cascada de inicialización descrita en SECTION C del
     * contract (Geometry → Graph/CIM/Servers → PG spawn).
     */
    void init();

    /** Sub-módulo 5.1: definición espacial del escenario. */
    Geometry geometry();

    /** Sub-módulo 5.2: navigation mesh. */
    Graph graph();

    /** Sub-módulo 5.3: índice espacial de vecinos (CIM). */
    NeighborsIndex neighbors();

    /** Sub-módulo 5.4: generador de peatones. */
    PedestrianGenerator pedestrianGenerator();

    /** Sub-módulo 5.5: puntos de servicio (queue/semaphore/classroom). */
    List<Server> servers();

    /**
     * Coordinador de ocupación de Locations puntuales (capacidad 1).
     * No es un sub-módulo del contract v4 — es estado compartido necesario
     * para que la SM no se contamine con lógica de ocupación.
     */
    LocationOccupancy locationOccupancy();
}
