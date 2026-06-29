package ar.edu.itba.simped.agent;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.OperationalModel;
import ar.edu.itba.simped.core.ports.Plan;
import ar.edu.itba.simped.core.ports.PreOM;
import ar.edu.itba.simped.core.ports.Sensors;
import ar.edu.itba.simped.core.ports.StateMachine;

/**
 * STUB - implementado por G2 (en coordinación con T8 y G4) — el Agent es el
 * contenedor que ensambla Plan, Sensors, StateMachine, PreOM y OM y los
 * coordina en cada {@code step(dt)}. Reemplazar.
 */
public final class StubAgent implements Agent {

    private final AgentState state;
    private final Plan plan;
    private final Sensors sensors;
    private final StateMachine stateMachine;
    private final PreOM preOM;
    private final OperationalModel operationalModel;
    private final NeighborsIndex neighborsIndex;

    public StubAgent(int id, String agentType) {
        this.state = new AgentState(id, agentType);
        this.plan = null;
        this.sensors = null;
        this.stateMachine = null;
        this.preOM = null;
        this.operationalModel = null;
        this.neighborsIndex = null;
    }

    @Override
    public void step(double dt) {
        // Implementación real en agent/AgentImpl.java. Este stub queda
        // como referencia del shape del container.
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
