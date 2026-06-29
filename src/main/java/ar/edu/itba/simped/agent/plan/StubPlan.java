package ar.edu.itba.simped.agent.plan;

import ar.edu.itba.simped.core.ports.Plan;
import ar.edu.itba.simped.core.ports.TaskTarget;

import java.util.List;

/** STUB - implementado por G2. Reemplazar. */
public final class StubPlan implements Plan {

    @Override
    public List<TaskTarget> taskList() {
        return List.of(); // TODO G2
    }

    @Override
    public int currentTaskIndex() {
        return 0; // TODO G2
    }

    @Override
    public void advance() {
        // TODO G2
    }
}
