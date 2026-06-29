package ar.edu.itba.simped.environment.neighbors;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.NeighborsIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación brute-force O(N) de NeighborsIndex para testing.
 * Implementación original del Grupo 7, adaptada al port.
 */
public final class BruteForceNeighborsIndex implements NeighborsIndex {

    private final List<AgentState> agents = new ArrayList<>();
    private final List<Wall> walls;

    public BruteForceNeighborsIndex(List<Wall> walls) {
        this.walls = walls;
    }

    @Override
    public void update(AgentState agent) {
        agents.removeIf(a -> a.id() == agent.id());
        agents.add(agent);
    }

    @Override
    public void remove(int agentId) {
        agents.removeIf(a -> a.id() == agentId);
    }

    @Override
    public List<Neighbor> neighborsOf(AgentState self, double rmax) {
        List<Neighbor> result = new ArrayList<>();
        Vec2 selfPos = new Vec2(self.x(), self.y());

        for (AgentState other : agents) {
            if (other.id() == self.id()) continue;
            Vec2 otherPos = new Vec2(other.x(), other.y());
            double dist = selfPos.distanceTo(otherPos);
            if (dist <= 2 * rmax) {
                result.add(new Neighbor(other.id(), NeighborType.AGENT, dist, other));
            }
        }

        for (int i = 0; i < walls.size(); i++) {
            double dist = walls.get(i).distanceTo(selfPos);
            if (dist <= rmax) {
                result.add(new Neighbor(i, NeighborType.WALL, dist, null));
            }
        }

        return result;
    }
}
