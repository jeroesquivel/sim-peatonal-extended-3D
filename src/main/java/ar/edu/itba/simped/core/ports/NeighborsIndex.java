package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Neighbor;

import java.util.List;

/**
 * Sub-módulo 5.3 del contract v4 (CIM, Cell Index Method).
 *
 * <p>Estructura espacial para lookup rápido de vecinos.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I19: recibe geometría de Walls para zonas de exclusión.</li>
 *   <li>I16: recibe actualización de posición desde {@link AgentState}
 *       cada dt.</li>
 *   <li>I16q: provee lista de vecinos dentro de {@code rmax} a
 *       {@link OperationalModel}.</li>
 * </ul>
 * </p>
 */
public interface NeighborsIndex {

    /** I16: registra/actualiza la posición de un agente en la grilla. */
    void update(AgentState agent);

    /**
     * I16 (remoción): saca a un agente del índice cuando egresa del sistema
     * (EXIT/despawn) para que deje de contar como vecino. No-op si el id no
     * está registrado.
     */
    void remove(int agentId);

    /**
     * I16q: obstáculos (agentes y paredes) dentro de un radio {@code rmax}
     * de la posición del agente {@code self} (excluyéndolo).
     */
    List<Neighbor> neighborsOf(AgentState self, double rmax);
}
