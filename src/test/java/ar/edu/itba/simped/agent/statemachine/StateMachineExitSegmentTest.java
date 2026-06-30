package ar.edu.itba.simped.agent.statemachine;

import ar.edu.itba.simped.agent.plan.PlanImpl;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.Task;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineExitSegmentTest {

    @Test
    void exitUsesClosestPointOnSegmentAsFootTarget() {
        AgentState state = new AgentState(1, "adult");
        state.setPosition(2.0, 5.0);
        Segment exitSegment = new Segment(new Vec2(10.0, 0.0), new Vec2(10.0, 10.0));
        Task exit = Task.exit(exitSegment.midpoint(), exitSegment);

        StateMachineImpl sm = new StateMachineImpl(
                state,
                state.id(),
                new PlanImpl(List.of(exit)),
                new NoOpLocationOccupancy(),
                0.9,
                null,
                List.of()
        );

        assertThat(sm.currentBehavior()).isEqualTo(BehaviorState.WALKING);
        assertThat(sm.currentFootTarget().xy()).isEqualTo(new Vec2(10.0, 5.0));
    }

    private static final class NoOpLocationOccupancy implements LocationOccupancy {
        @Override
        public boolean tryOccupy(Vec2 location, int agentId) {
            return true;
        }

        @Override
        public void release(Vec2 location, int agentId) {
        }
    }
}
