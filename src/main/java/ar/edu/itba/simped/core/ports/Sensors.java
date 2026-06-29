package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Vec2;

/**
 * Sub-módulo 4.3 del contract v4.
 *
 * <p>Computa la distancia escalar entre la posición del agente y el foot-target
 * actual. Detecta eventos de task-completion y relaya señales originadas en
 * Servers hacia SM.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I6:  lee {@link ar.edu.itba.simped.core.AgentState} (posición propia).</li>
 *   <li>I9:  lee foot-target desde {@link StateMachine}.</li>
 *   <li>I13c: recibe señales de {@link Server}.</li>
 *   <li>I10a: produce eventos a {@link StateMachine}.</li>
 * </ul>
 * </p>
 */
public interface Sensors {

    /**
     * Tick principal del módulo. Llamado por el Agent container en cada paso
     * del SimulationLoop. Lee posición propia (I6) y foot-target del SM (I9),
     * actualiza la distancia y dispara onArrival al SM si corresponde (I10b).
     */
    void sense();

    /** Distancia escalar al foot-target actual: {@code | (x,y) - (xft, yft) |}. */
    double distanceToTarget();

    /** Asocia el foot-target actual provisto por SM (I9). */
    void setFootTarget(Vec2 footTarget);

    /**
     * I13c: notifica una señal originada en un Server. En el diseño actual,
     * el caso esperado es service completion para destrabar una task de tipo
     * SERVER y relanzar el avance normal de la StateMachine.
     */
    void onServerSignal(ServerSignal signal);
}
