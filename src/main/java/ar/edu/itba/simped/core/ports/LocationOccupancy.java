package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Vec2;

/**
 * Coordinador de ocupación de Locations puntuales (capacidad 1).
 *
 * <p>Una {@code LOCATION} del plan representa un punto exclusivo: solo un
 * agente puede ocuparlo a la vez durante su dwell. Este puerto centraliza
 * ese estado compartido entre todos los agentes, fuera de la
 * {@link StateMachine} (la SM solo pregunta; no es dueña del mapa).</p>
 *
 * <p>Vive como sub-componente de {@link Environment}. La SM lo consulta en
 * {@code beginLocationUse} para decidir si pasa a {@code OCCUPYING} o se
 * queda intentando.</p>
 */
public interface LocationOccupancy {

    /**
     * Intenta marcar {@code location} como ocupada por {@code agentId}.
     *
     * @return {@code true} si la ocupación quedó asignada a este agente
     *         (la location estaba libre, o ya era de este mismo agente);
     *         {@code false} si está ocupada por otro.
     */
    boolean tryOccupy(Vec2 location, int agentId);

    /**
     * Libera {@code location} si actualmente la ocupa {@code agentId}.
     * No-op si está libre o la ocupa otro.
     */
    void release(Vec2 location, int agentId);
}
