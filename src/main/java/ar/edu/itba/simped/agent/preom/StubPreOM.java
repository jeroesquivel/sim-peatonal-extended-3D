package ar.edu.itba.simped.agent.preom;

import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.PreOM;

/**
 * PreOM trivial sin consulta a Graph: el foot-target resuelto es el último
 * que activó SM (o que pasó un Server durante delegación).
 *
 * <p>Pensado para el prototipo, donde Graph todavía es un stub y no se
 * necesita resolución de visibilidad. El CpmPreOM con A* + FVP sigue siendo
 * el que usaremos cuando Graph esté wireado.</p>
 */
public final class StubPreOM implements PreOM {

    private Vec3 activeTarget;

    @Override
    public Vec3 resolvedFootTarget() {
        return activeTarget;
    }

    @Override
    public void activate(Vec3 footTarget) {
        this.activeTarget = footTarget;
    }

    @Override
    public void onServerTarget(Vec3 target) {
        this.activeTarget = target;
    }
}
