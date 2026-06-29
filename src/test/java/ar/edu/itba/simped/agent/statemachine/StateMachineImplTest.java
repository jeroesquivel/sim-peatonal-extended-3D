package ar.edu.itba.simped.agent.statemachine;

import ar.edu.itba.simped.agent.plan.PlanImpl;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Task;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineImplTest {

    @Test
    void locationWithDwellTransitionsThroughLeavingBeforeNextWalking() {
        AgentState state = new AgentState(1, "adult");
        state.setPosition(0.0, 0.0);
        Task location = Task.location(new Vec2(1.0, 0.0), 2.0);
        Task nextLocation = Task.location(new Vec2(5.0, 0.0), 0.0);
        StateMachineImpl sm = new StateMachineImpl(
                state,
                state.id(),
                new PlanImpl(List.of(location, nextLocation)),
                new InMemoryLocationOccupancy(),
                0.9,
                null,
                List.of()
        );

        sm.onArrival();
        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.OCCUPYING);

        sm.tick(2.0);

        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.LEAVING);
        assertThat(sm.currentFootTarget()).isEqualTo(nextLocation.target());

        sm.onArrival();
        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.LEAVING);

        state.setPosition(2.0, 0.0);
        sm.tick(0.1);
        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.WALKING);
    }

    @Test
    void exitStartsWalkingInsteadOfLeaving() {
        AgentState state = new AgentState(2, "adult");
        Task exit = Task.exit(new Vec2(10.0, 0.0));
        StateMachineImpl sm = new StateMachineImpl(
                state,
                state.id(),
                new PlanImpl(List.of(exit)),
                new InMemoryLocationOccupancy(),
                0.9,
                null,
                List.of()
        );

        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.WALKING);
    }

    private static final class InMemoryLocationOccupancy implements LocationOccupancy {
        private final Map<Vec2, Integer> occupiedByLocation = new HashMap<>();

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
