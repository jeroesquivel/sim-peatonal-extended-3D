package ar.edu.itba.simped.agent.statemachine;

import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.StateMachine;

/** STUB - implementado por G2. Reemplazar. */
public final class StubStateMachine implements StateMachine {

    @Override
    public Vec3 currentFootTarget() {
        return Vec3.ZERO; // TODO G2
    }

    @Override
    public BehaviorState currentBehavior() {
        return BehaviorState.IDLE; // TODO G2
    }

    @Override
    public void onApproach() {
        // TODO G2
    }

    @Override
    public void onApproachExit() {
        // TODO G2
    }

    @Override
    public void onArrival() {
        // TODO G2
    }

    @Override
    public void onTaskComplete() {
        // TODO G2
    }

    @Override
    public void tick(double dt) {
        // TODO G2
    }
}
