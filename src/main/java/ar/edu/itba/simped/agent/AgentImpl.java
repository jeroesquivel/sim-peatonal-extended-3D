package ar.edu.itba.simped.agent;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.OperationalModel;
import ar.edu.itba.simped.core.ports.PreOM;
import ar.edu.itba.simped.core.ports.Sensors;
import ar.edu.itba.simped.core.ports.StateMachine;

import java.util.List;

/**
 * Container del módulo 4 del contract v4 (Agent). Una instancia por peatón.
 *
 * <p>Su {@link #step(double)} ejecuta el pipeline:
 * <ol>
 *   <li>Sensors lee el foot-target del SM y dispara onArrival si corresponde (I6/I9/I10b).</li>
 *   <li>PreOM se activa con el target actual del SM (I11) y resuelve (I15a).</li>
 *   <li>NeighborsIndex provee la lista de obstáculos (I16q).</li>
 *   <li>OperationalModel integra y muta {@link AgentState} (I15).</li>
 * </ol>
 * </p>
 */
public final class AgentImpl implements Agent {

    private final AgentState state;
    private final Sensors sensors;
    private final StateMachine sm;
    private final PreOM preom;
    private final OperationalModel om;
    private final NeighborsIndex neighbors;

    /** Último target fino empujado por un Server vía I13b (null si no hay). */
    private Vec3 serverTarget;

    public AgentImpl(
            AgentState state,
            Sensors sensors,
            StateMachine sm,
            PreOM preom,
            OperationalModel om,
            NeighborsIndex neighbors
    ) {
        this.state = state;
        this.sensors = sensors;
        this.sm = sm;
        this.preom = preom;
        this.om = om;
        this.neighbors = neighbors;
    }

    @Override
    public void step(double dt) {
        sensors.sense();
        sm.tick(dt);

        Vec3 target = sm.currentFootTarget();
        // Mientras el agente está delegado a un Server, este empuja los targets
        // finos (slot de cola / posición de servicio / región de espera) vía
        // I13b y mandan sobre el foot-target grueso de la SM, sea cual sea el
        // BehaviorState (camina al puesto en WALKING/APPROACHING y queda
        // QUEUEING recién al llegar). El wiring lo limpia con SERVICE_COMPLETE.
        if (serverTarget != null) {
            target = serverTarget;
        }
        if (target == null) {
            // Plan completo o sin tasks. El agente queda parado en su estado actual.
            return;
        }
        preom.activate(target);

        Vec3 footTarget = preom.resolvedFootTarget();
        if (footTarget == null) {
            return;
        }

        BehaviorState behavior = sm.currentBehavior();
        List<Neighbor> nb = neighbors.neighborsOf(state, om.neighborQueryRadius(state, behavior));
        om.integrate(state, footTarget, behavior, nb, dt);
    }

    @Override
    public AgentState state() {
        return state;
    }

    @Override
    public int id() {
        return state.id();
    }

    /**
     * Sensors de este agente. Lo usa el wiring de Servers (T6) para empujar
     * señales I13b/I13c desde el {@code ServersModule} al pipeline del agente.
     */
    public Sensors sensors() {
        return sensors;
    }

    /**
     * I13b: el wiring de Servers (T6) inyecta acá el target fino que el
     * {@code ServersModule} empuja para este agente mientras está delegado.
     */
    public void setServerTarget(Vec3 target) {
        this.serverTarget = target;
    }
}
