package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Vec3;

/**
 * Sub-módulo 4.4 del contract v4.
 *
 * <p>FSM que traduce la task activa del Plan en behavioral mode, parametriza
 * el sub-sistema de movimiento, y delega interacciones con Servers como
 * subproceso controlado.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I8:  recibe la full task list desde {@link Plan}.</li>
 *   <li>I9:  provee {@code (xft, yft)} a {@link Sensors}.</li>
 *   <li>I10a: recibe task_complete_event desde Sensors.</li>
 *   <li>I11: activa {@link PreOM} para resolución de target.</li>
 *   <li>I12: pasa {@link BehaviorState} a {@link OperationalModel}.</li>
 *   <li>I13a: emite delegation_request a {@link Server}.</li>
 * </ul>
 * </p>
 */
public interface StateMachine {

    /** I9: foot-target 3D derivado de la task activa del Plan ({@code z} = planta del target). */
    Vec3 currentFootTarget();

    /** Estado comportamental actual del agente. */
    BehaviorState currentBehavior();

    /** Notifica que Sensors detectó cercanía al target actual. */
    void onApproach();

    /** Notifica que Sensors detectó salida de la zona de cercanía al target. */
    void onApproachExit();

    /** Notifica que Sensors detectó arrival sobre el foot-target actual. */
    void onArrival();

    /** I10a: notifica que la task activa se completó. */
    void onTaskComplete();

    /**
     * Notifica que el módulo de Servers detectó que el agente llegó a su
     * puesto (slot de fila / región de semáforo o classroom). Relay del
     * {@link StandardServerSignal#ARRIVED_AT_POST} vía Sensors. La SM pasa el
     * agente a {@code QUEUEING}; mientras camina hacia el puesto queda en
     * {@code WALKING}/{@code APPROACHING}.
     */
    default void onServerPostArrival() {
    }

    /** Avanza timers internos de la máquina de estados para el paso {@code dt}. */
    void tick(double dt);

}
