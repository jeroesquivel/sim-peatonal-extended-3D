package ar.edu.itba.simped.agent.statemachine;

import ar.edu.itba.simped.agent.plan.PlanImpl;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.Task;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineLocationGroupTest {

    @Test
    void groupedLocationChoosesClosestCandidateOnEnter() {
        AgentState state = new AgentState(1, "adult");
        state.setPosition(0.0, 0.0);
        Task task = Task.locationGroup(
                "PISTA",
                List.of(new Vec2(10.0, 0.0), new Vec2(3.0, 0.0), new Vec2(7.0, 0.0)),
                ObjectiveSelection.CLOSEST,
                5.0,
                7L
        );

        StateMachineImpl sm = new StateMachineImpl(
                state,
                state.id(),
                new PlanImpl(List.of(task)),
                new InMemoryLocationOccupancy(),
                0.9,
                null,
                List.of()
        );

        assertThat(sm.currentFootTarget().xy()).isEqualTo(new Vec2(3.0, 0.0));
        assertThat(task.target()).isEqualTo(new Vec2(3.0, 0.0));
    }

    @Test
    void groupedLocationRetriesAnotherCandidateWhenChosenOneIsOccupied() {
        AgentState state = new AgentState(2, "adult");
        state.setPosition(0.0, 0.0);
        InMemoryLocationOccupancy occupancy = new InMemoryLocationOccupancy();
        occupancy.occupy(new Vec2(3.0, 0.0), 99);
        Task task = Task.locationGroup(
                "PISTA",
                List.of(new Vec2(10.0, 0.0), new Vec2(3.0, 0.0), new Vec2(7.0, 0.0)),
                ObjectiveSelection.CLOSEST,
                5.0,
                7L
        );

        StateMachineImpl sm = new StateMachineImpl(
                state,
                state.id(),
                new PlanImpl(List.of(task)),
                occupancy,
                0.9,
                null,
                List.of()
        );

        state.setPosition(3.0, 0.0);
        sm.onArrival();

        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.WALKING);
        assertThat(sm.currentFootTarget().xy()).isEqualTo(new Vec2(7.0, 0.0));
        assertThat(task.target()).isEqualTo(new Vec2(7.0, 0.0));
    }

    private static final class InMemoryLocationOccupancy implements LocationOccupancy {
        private final Map<Vec2, Integer> occupiedByLocation = new HashMap<>();

        void occupy(Vec2 location, int agentId) {
            occupiedByLocation.put(location, agentId);
        }

        @Override
        public boolean tryOccupy(Vec2 location, int agentId) {
            Integer owner = occupiedByLocation.get(location);
            if (owner == null || owner == agentId) {
                occupiedByLocation.put(location, agentId);
                return true;
            }
            return false;
        }

        @Override
        public void release(Vec2 location, int agentId) {
            Integer owner = occupiedByLocation.get(location);
            if (owner != null && owner == agentId) {
                occupiedByLocation.remove(location);
            }
        }
    }
}
