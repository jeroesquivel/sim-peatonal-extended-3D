package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.Vec2;

/**
 * Sub-módulo 4.5 del contract v4 (Pre-Operational Model).
 *
 * <p>Resuelve el foot-target final {@code (xvt, yvt)} que OM va a trackear.
 * Consulta Graph cuando el próximo waypoint no es directamente visible.
 * Durante delegación a Server recibe targets directos desde Server.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I11: activación desde {@link StateMachine} (task normal).</li>
 *   <li>I13b: targets sucesivos desde {@link Server} (durante delegación).</li>
 *   <li>I14: query a {@link Graph} (path / visibility).</li>
 *   <li>I15a: emite {@code (xvt, yvt)} a {@link OperationalModel}.</li>
 * </ul>
 * </p>
 */
public interface PreOM {

    /** I15a: foot-target resuelto que OM va a trackear. */
    Vec2 resolvedFootTarget();

    /** I11: activa la resolución para una task normal. */
    void activate(Vec2 footTarget);

    /** I13b: target intermedio provisto por Server durante delegación. */
    void onServerTarget(Vec2 target);
}
