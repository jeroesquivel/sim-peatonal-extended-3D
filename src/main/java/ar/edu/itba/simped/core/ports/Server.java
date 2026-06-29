package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Vec2;

/**
 * Sub-módulo 5.5 del contract v4.
 *
 * <p>Puntos de servicio que actúan como subprocesos de SM durante tasks de
 * Server. Tres tipos: {@code queue | semaphore | classroom}.</p>
 *
 * <p>Cuando SM delega (I13a), Server maneja autónomamente el ciclo de
 * queueing y service para ese agente.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I20: recibe posición desde {@link Geometry}.</li>
 *   <li>I13a: recibe delegation_request desde {@link StateMachine}.</li>
 *   <li>I13b: emite targets sucesivos {@code (xt, yt)} a {@link PreOM}.</li>
 *   <li>I13c: emite service_complete a {@link Sensors}.</li>
 * </ul>
 * </p>
 */
public interface Server {

    /** Identificador (block_name del CSV). */
    String name();

    /** Posición de servicio. */
    Vec2 position();

    /** I13a: SM delega el control de un agente a este server. */
    void delegate(Agent agent);
}
