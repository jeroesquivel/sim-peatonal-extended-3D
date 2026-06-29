package ar.edu.itba.simped.agent.sensors;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Sensors;
import ar.edu.itba.simped.core.ports.ServerSignal;
import ar.edu.itba.simped.core.ports.StandardServerSignal;
import ar.edu.itba.simped.core.ports.StateMachine;

/**
 * Sensors per-agent (4.3). Calcula distancia al foot-target y detecta
 * arrival/completion. Implementación original del Grupo 2, adaptada al port.
 *
 * <p>Del lado de Servers, el evento esperado hoy es service completion: mientras
 * el agente está en {@link BehaviorState#QUEUEING}, la task no completa por
 * distancia y solo se destraba cuando llega la señal del Server.</p>
 */
public final class SensorsImpl implements Sensors {

    private static final double DEFAULT_APPROACH_TO_ARRIVAL_RATIO = 3.0;

    private final AgentState agentState;
    private final StateMachine stateMachine;
    private final double approachThreshold;
    private final double arrivalThreshold;

    private double dToTarget = Double.POSITIVE_INFINITY;
    private Vec2 lastTarget = null;
    private boolean approachReported = false;
    private boolean arrivalReported = false;

    public SensorsImpl(AgentState agentState, StateMachine stateMachine, double arrivalThreshold) {
        this(
                agentState,
                stateMachine,
                arrivalThreshold * DEFAULT_APPROACH_TO_ARRIVAL_RATIO,
                arrivalThreshold
        );
    }

    public SensorsImpl(
            AgentState agentState,
            StateMachine stateMachine,
            double approachThreshold,
            double arrivalThreshold
    ) {
        this.agentState = agentState;
        this.stateMachine = stateMachine;
        this.approachThreshold = approachThreshold;
        this.arrivalThreshold = arrivalThreshold;
    }

    @Override
    public double distanceToTarget() {
        return dToTarget;
    }

    @Override
    public void setFootTarget(Vec2 footTarget) {
        if (footTarget != null && !footTarget.equals(lastTarget)) {
            lastTarget = footTarget;
            approachReported = false;
            arrivalReported = false;
        }
    }

    @Override
    public void onServerSignal(ServerSignal signal) {
        // Relay de las señales de Servers (I13c) hacia la SM:
        // - ARRIVED_AT_POST: el agente pisó su puesto -> la SM pasa a QUEUEING.
        // - SERVICE_COMPLETE (o señal desconocida, back-compat): la task
        //   delegada terminó -> task_complete (I10a).
        if (signal == StandardServerSignal.ARRIVED_AT_POST) {
            stateMachine.onServerPostArrival();
            return;
        }
        stateMachine.onTaskComplete();
    }

    @Override
    public void sense() {
        Vec2 target = stateMachine.currentFootTarget();
        setFootTarget(target);

        if (target == null) {
            dToTarget = Double.POSITIVE_INFINITY;
            return;
        }

        Vec2 pos = new Vec2(agentState.x(), agentState.y());
        dToTarget = pos.distanceTo(target);

        if (stateMachine.currentBehavior() == BehaviorState.QUEUEING) {
            return;
        }

        if (dToTarget < approachThreshold && !approachReported) {
            approachReported = true;
            stateMachine.onApproach();
        } else if (dToTarget >= approachThreshold && approachReported) {
            approachReported = false;
            stateMachine.onApproachExit();
        }

        if (dToTarget < arrivalThreshold && !arrivalReported) {
            arrivalReported = true;
            stateMachine.onArrival();
            if (stateMachine.currentBehavior() == BehaviorState.WALKING
                    || stateMachine.currentBehavior() == BehaviorState.APPROACHING) {
                arrivalReported = false;
            }
        }
    }
}
