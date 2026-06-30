package ar.edu.itba.simped.agent.om;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.OperationalModel;

import java.util.List;

/** STUB - implementado por G4. Reemplazar. */
public final class StubOperationalModel implements OperationalModel {

    @Override
    public void integrate(
            AgentState state,
            Vec3 footTarget,
            BehaviorState behavior,
            List<Neighbor> neighbors,
            double dt
    ) {
        // TODO G4: integrar fuerzas y escribir nuevas x, y, vx, vy en state.
    }
}
