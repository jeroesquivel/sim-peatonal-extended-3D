package ar.edu.itba.simped.environment.neighbors;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.ports.NeighborsIndex;

import java.util.List;

/** STUB - implementado por G5 (CIM). Reemplazar. */
public final class StubNeighborsIndex implements NeighborsIndex {

    @Override
    public void update(AgentState agent) {
        // TODO G5
    }

    @Override
    public void remove(int agentId) {}

    @Override
    public List<Neighbor> neighborsOf(AgentState self, double rmax) {
        return List.of(); // TODO G5
    }
}
