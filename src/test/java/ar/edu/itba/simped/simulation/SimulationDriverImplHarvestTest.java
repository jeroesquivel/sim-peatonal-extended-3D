package ar.edu.itba.simped.simulation;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.Environment;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.Graph;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.OutputSink;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;
import ar.edu.itba.simped.core.ports.Server;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el harvest de agentes con behavior == DEAD en el loop principal:
 * los DEAD se sacan de la iteración antes del próximo tick y se notifica al
 * cleanup callback (que App usa para limpiar el agentRegistry de Servers).
 */
class SimulationDriverImplHarvestTest {

    @Test
    void removesDeadAgentsAndNotifiesCallback() {
        TestAgent alive = new TestAgent(10, BehaviorState.WALKING);
        TestAgent dead = new TestAgent(11, BehaviorState.DEAD);

        TestEnvironment env = new TestEnvironment(List.of(alive, dead));
        CapturingOutputSink sink = new CapturingOutputSink();
        Set<Integer> removed = new HashSet<>();

        SimulationDriverImpl driver = new SimulationDriverImpl(
                env, sink, 0.1, 0.1, 0.2, null, removed::add);

        driver.run();

        assertThat(removed).containsExactly(11);
        // El harvest también saca al agente del CIM (vía NeighborsIndex.remove).
        assertThat(env.neighborsIndex().removedIds).containsExactly(11);
        assertThat(sink.snapshots).hasSizeGreaterThan(1);
        // El primer writeOutput es ANTES del harvest: arranca con los 2.
        assertThat(sink.snapshots.get(0)).hasSize(2);
        // Después del primer tick el DEAD ya fue cosechado.
        assertThat(sink.snapshots.get(sink.snapshots.size() - 1))
                .singleElement()
                .extracting(AgentState::id)
                .isEqualTo(10);
    }

    @Test
    void aliveAgentsAreNotRemoved() {
        TestAgent a = new TestAgent(1, BehaviorState.WALKING);
        TestAgent b = new TestAgent(2, BehaviorState.QUEUEING);
        TestAgent c = new TestAgent(3, BehaviorState.IDLE);

        TestEnvironment env = new TestEnvironment(List.of(a, b, c));
        CapturingOutputSink sink = new CapturingOutputSink();
        Set<Integer> removed = new HashSet<>();

        new SimulationDriverImpl(env, sink, 0.1, 0.1, 0.3, null, removed::add).run();

        assertThat(removed).isEmpty();
        // Ningún snapshot perdió agentes.
        assertThat(sink.snapshots).allSatisfy(snap -> assertThat(snap).hasSize(3));
    }

    @Test
    void worksWithoutCallback() {
        TestAgent dead = new TestAgent(7, BehaviorState.DEAD);
        TestEnvironment env = new TestEnvironment(List.of(dead));
        CapturingOutputSink sink = new CapturingOutputSink();

        // Sin el callback opcional: el harvest sigue limpiando la lista interna,
        // simplemente no notifica a nadie. No debe romper.
        new SimulationDriverImpl(env, sink, 0.1, 0.1, 0.2, null).run();

        assertThat(sink.snapshots.get(sink.snapshots.size() - 1)).isEmpty();
    }

    private static final class TestAgent implements Agent {
        private final AgentState state;

        TestAgent(int id, BehaviorState behavior) {
            this.state = new AgentState(id, "test");
            this.state.setState(behavior);
        }

        @Override
        public void step(double dt) {
            // no-op: el behavior no cambia, así que un DEAD sigue DEAD.
        }

        @Override
        public AgentState state() {
            return state;
        }

        @Override
        public int id() {
            return state.id();
        }
    }

    private static final class TestPedestrianGenerator implements PedestrianGenerator {
        private final List<Agent> initial;

        TestPedestrianGenerator(List<Agent> initial) {
            this.initial = initial;
        }

        @Override
        public List<Agent> spawnInitial() {
            return new ArrayList<>(initial);
        }

        @Override
        public List<Agent> spawnTick(double currentTime, double dt) {
            return Collections.emptyList();
        }
    }

    private static final class TestNeighborsIndex implements NeighborsIndex {
        final Set<Integer> removedIds = new HashSet<>();

        @Override
        public void update(AgentState agent) {
        }

        @Override
        public void remove(int agentId) {
            removedIds.add(agentId);
        }

        @Override
        public List<Neighbor> neighborsOf(AgentState self, double rmax) {
            return Collections.emptyList();
        }
    }

    private static final class TestEnvironment implements Environment {
        private final PedestrianGenerator pg;
        private final TestNeighborsIndex neighbors = new TestNeighborsIndex();

        TestEnvironment(List<Agent> initial) {
            this.pg = new TestPedestrianGenerator(initial);
        }

        TestNeighborsIndex neighborsIndex() { return neighbors; }

        @Override public void init() {}
        @Override public Geometry geometry() { return null; }
        @Override public Graph graph() { return null; }
        @Override public NeighborsIndex neighbors() { return neighbors; }
        @Override public PedestrianGenerator pedestrianGenerator() { return pg; }
        @Override public List<Server> servers() { return Collections.emptyList(); }
        @Override public LocationOccupancy locationOccupancy() { return null; }
    }

    private static final class CapturingOutputSink implements OutputSink {
        final List<List<AgentState>> snapshots = new ArrayList<>();

        @Override
        public void writeStep(double tout, Iterable<AgentState> agents) {
            List<AgentState> snap = new ArrayList<>();
            agents.forEach(snap::add);
            snapshots.add(snap);
        }

        @Override
        public void close() {}
    }
}
