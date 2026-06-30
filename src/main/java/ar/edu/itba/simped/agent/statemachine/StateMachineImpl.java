package ar.edu.itba.simped.agent.statemachine;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.LocationTargetSelector;
import ar.edu.itba.simped.core.Task;
import ar.edu.itba.simped.core.TaskStatus;
import ar.edu.itba.simped.core.TaskType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import ar.edu.itba.simped.core.ports.Plan;
import ar.edu.itba.simped.core.ports.Server;
import ar.edu.itba.simped.core.ports.StateMachine;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * StateMachine per-agent (4.4). Traduce la tarea activa del Plan en un modo
 * de comportamiento y expone el foot-target para Sensors/PreOM.
 * Implementación original del Grupo 2, refactoreada a una instancia por agente.
 */
public final class StateMachineImpl implements StateMachine {

    // SERVER entra en WALKING: el agente camina hacia el puesto que le asigna
    // el modulo de Servers y recien pasa a QUEUEING cuando este confirma la
    // llegada (ARRIVED_AT_POST -> onServerPostArrival). Pedido de la catedra
    // 2026-06-05: QUEUEING es "estar en la fila/zona", no "ir hacia ella".
    private static final Map<TaskType, BehaviorState> ENTRY_BEHAVIOR = Map.of(
            TaskType.LOCATION, BehaviorState.WALKING,
            TaskType.SERVER,   BehaviorState.WALKING,
            TaskType.EXIT,     BehaviorState.WALKING
    );

    private final AgentState agentState;
    private final Plan plan;
    private Agent agent;
    private final Function<String, Server> serverByGroup;
    private final LocationOccupancy locationOccupancy;
    private final double leavingDistanceThreshold;

    private BehaviorState behavior = BehaviorState.IDLE;
    private Vec2 footTarget = null;
    private double remainingDwellSeconds = 0.0;
    private Vec2 leavingOrigin = null;
    private final Set<Vec2> rejectedLocationTargets = new LinkedHashSet<>();

    public StateMachineImpl(
            AgentState agentState,
            int agentId,
            Plan plan,
            LocationOccupancy locationOccupancy
    ) {
        this(agentState, agentId, plan, locationOccupancy, 0.0, null, group -> null);
    }

    public StateMachineImpl(
            AgentState agentState,
            int agentId,
            Plan plan,
            LocationOccupancy locationOccupancy,
            double leavingDistanceThreshold,
            Agent agent,
            Function<String, Server> serverByGroup
    ) {
        this.agentState = agentState;
        this.plan = plan;
        this.locationOccupancy = locationOccupancy;
        this.leavingDistanceThreshold = Math.max(0.0, leavingDistanceThreshold);
        this.agent = agent;
        this.serverByGroup = serverByGroup;
        enterTask(currentTask());
    }

    public StateMachineImpl(
            AgentState agentState,
            int agentId,
            Plan plan,
            LocationOccupancy locationOccupancy,
            double leavingDistanceThreshold,
            Agent agent,
            List<Server> servers
    ) {
        this(
                agentState,
                agentId,
                plan,
                locationOccupancy,
                leavingDistanceThreshold,
                agent,
                buildServerLookup(servers)
        );
    }

    /**
     * Inyecta el {@link Agent} dueño de esta SM después de construirla (el
     * AgentImpl no existe todavía cuando se arma la SM). Necesario para que la
     * delegación a Server (I13a) tenga a quién delegar.
     */
    public void attachAgent(Agent agent) {
        this.agent = agent;
        // Si el plan ARRANCA con un SERVER, la delegación (I13a) corrió en el
        // constructor cuando agent todavía era null y se salteó. Ahora que está
        // attacheado, delegamos el server inicial.
        Task task = currentTask();
        if (task != null && task.type() == TaskType.SERVER) {
            Server serverGroup = resolveServerGroup(task);
            if (serverGroup != null) {
                serverGroup.delegate(agent);
            }
        }
    }

    @Override
    public Vec3 currentFootTarget() {
        // El footTarget vive en la planta del target (Task.z): así el Graph rutea
        // hacia la planta correcta (a través de las escaleras si difiere de la del
        // agente). currentTask() es la task que produjo el footTarget actual
        // (también durante LEAVING, donde el plan ya avanzó a la próxima).
        if (footTarget == null) {
            return null;
        }
        Task task = currentTask();
        double targetZ = task != null ? task.z() : agentState.z();
        return footTarget.withZ(targetZ);
    }

    @Override
    public void onTaskComplete() {
        Task task = currentTask();
        if (task == null) return;
        completeCurrentTask(task);
    }

    @Override
    public void onApproach() {
        Task task = currentTask();
        // APPROACHING solo para LOCATIONs reales (con dwell, donde el agente se
        // detiene). En SERVER competía con el QUEUEING por pisar la región y en
        // waypoints de tránsito (dwell 0) daba un APPROACHING de 1-2 frames
        // ("flash"); el camino queda en WALKING hasta llegar.
        if (task == null || task.type() != TaskType.LOCATION || task.dwellSeconds() <= 0.0) {
            return;
        }
        if (behavior == BehaviorState.WALKING) {
            setBehavior(BehaviorState.APPROACHING);
        }
    }

    @Override
    public void onApproachExit() {
        Task task = currentTask();
        if (task == null || task.type() != TaskType.LOCATION || task.dwellSeconds() <= 0.0) {
            return;
        }
        if (behavior == BehaviorState.APPROACHING) {
            setBehavior(BehaviorState.WALKING);
        }
    }

    @Override
    public void onArrival() {
        Task task = currentTask();
        if (task == null) return;
        if (behavior == BehaviorState.LEAVING) {
            return;
        }

        switch (task.type()) {
            case LOCATION -> {
                setBehavior(BehaviorState.ARRIVED);
                if (task.dwellSeconds() > 0) {
                    beginLocationUse(task);
                } else {
                    completeCurrentTask(task);
                }
            }
            case EXIT -> {
                setBehavior(BehaviorState.ARRIVED);
                completeCurrentTask(task);
            }
            case SERVER -> {
                // Arrival por distancia al centroide del grupo: ignorado. El
                // QUEUEING llega solo con ARRIVED_AT_POST (puesto real) y la
                // task completa solo con serviceComplete -> onTaskComplete().
            }
        }
    }

    @Override
    public void onServerPostArrival() {
        Task task = currentTask();
        if (task == null || task.type() != TaskType.SERVER) {
            return;
        }
        setBehavior(BehaviorState.QUEUEING);
    }

    @Override
    public BehaviorState currentBehavior() {
        return behavior;
    }

    @Override
    public void tick(double dt) {
        if (dt <= 0.0) {
            return;
        }

        if (behavior == BehaviorState.LEAVING) {
            maybeFinishLeaving();
            return;
        }

        if (behavior != BehaviorState.OCCUPYING) {
            return;
        }

        Task task = currentTask();
        if (task == null || task.type() != TaskType.LOCATION || task.dwellSeconds() <= 0.0) {
            return;
        }

        remainingDwellSeconds -= dt;
        if (remainingDwellSeconds <= 0.0) {
            remainingDwellSeconds = 0.0;
            completeCurrentTask(task);
        }
    }

    private void completeCurrentTask(Task task) {
        if (task.type() == TaskType.LOCATION && task.dwellSeconds() > 0) {
            locationOccupancy.release(task.target(), agentState.id());
        }
        task.setStatus(TaskStatus.COMPLETED);
        plan.advance();
        Task next = currentTask();
        if (next == null) {
            // Se completó la última task del plan: el agente queda DEAD (no IDLE).
            // IDLE se reserva para el arranque sin tareas (enterTask con plan vacío).
            setBehavior(BehaviorState.DEAD);
            footTarget = null;
            remainingDwellSeconds = 0.0;
            return;
        }
        if (task.type() == TaskType.LOCATION && task.dwellSeconds() > 0) {
            startLeavingPhase(task.target(), next);
            return;
        }
        enterTask(next);
    }

    private void enterTask(Task task) {
        if (task == null) {
            setBehavior(BehaviorState.IDLE);
            footTarget = null;
            remainingDwellSeconds = 0.0;
            leavingOrigin = null;
            return;
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        setBehavior(ENTRY_BEHAVIOR.get(task.type()));
        remainingDwellSeconds = 0.0;
        leavingOrigin = null;
        rejectedLocationTargets.clear();

        if (task.type() == TaskType.SERVER) {
            enterServerTask(task);
            return;
        }

        if (task.type() == TaskType.EXIT) {
            footTarget = resolveExitTarget(task);
            return;
        }

        if (task.hasLocationChoices()) {
            resolveGroupedLocationTarget(task, Set.of());
            return;
        }

        footTarget = task.target();
    }

    private void beginLocationUse(Task task) {
        // Las LOCATIONs son puntos de capacidad 1 (decisión de cátedra). La
        // SM no es dueña del estado de ocupación — le pregunta a LocationOccupancy
        // (estado compartido en el Environment). Si el punto ya está ocupado por
        // otro agente, este vuelve a APPROACHING y Sensors va a reintentar el
        // arrival en los próximos dt mientras el agente siga cerca del target.
        boolean acquired = locationOccupancy.tryOccupy(task.target(), agentState.id());
        if (acquired) {
            rejectedLocationTargets.clear();
            setBehavior(BehaviorState.OCCUPYING);
            remainingDwellSeconds = task.dwellSeconds();
        } else {
            if (task.hasLocationChoices()) {
                rejectedLocationTargets.add(task.target());
                if (resolveGroupedLocationTarget(task, rejectedLocationTargets)) {
                    setBehavior(BehaviorState.WALKING);
                    return;
                }
                rejectedLocationTargets.clear();
            }
            setBehavior(BehaviorState.APPROACHING);
        }
    }

    private void startLeavingPhase(Vec2 origin, Task nextTask) {
        leavingOrigin = origin;
        prepareTaskTarget(nextTask);
        setBehavior(BehaviorState.LEAVING);
    }

    private void maybeFinishLeaving() {
        if (leavingOrigin == null) {
            Task task = currentTask();
            if (task != null) {
                setBehavior(ENTRY_BEHAVIOR.get(task.type()));
            }
            return;
        }

        double distanceFromOrigin = new Vec2(agentState.x(), agentState.y()).distanceTo(leavingOrigin);
        if (distanceFromOrigin < leavingDistanceThreshold) {
            return;
        }

        Task task = currentTask();
        if (task == null) {
            setBehavior(BehaviorState.DEAD);
            footTarget = null;
            remainingDwellSeconds = 0.0;
            leavingOrigin = null;
            return;
        }

        setBehavior(ENTRY_BEHAVIOR.get(task.type()));
        leavingOrigin = null;
    }

    private void prepareTaskTarget(Task task) {
        if (task == null) {
            footTarget = null;
            return;
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        remainingDwellSeconds = 0.0;

        if (task.type() == TaskType.SERVER) {
            enterServerTask(task);
            return;
        }

        if (task.type() == TaskType.EXIT) {
            footTarget = resolveExitTarget(task);
            return;
        }

        if (task.hasLocationChoices()) {
            resolveGroupedLocationTarget(task, Set.of());
            return;
        }

        footTarget = task.target();
    }

    private void enterServerTask(Task task) {
        Server serverGroup = resolveServerGroup(task);
        // TODO(T3/T5/T6): decide whether missing server-group wiring should
        // fail fast instead of silently falling back to task.target(). The
        // fallback is useful while integration is still incomplete, but once
        // Environment.servers() is fully wired it may be better to surface an
        // error if task.targetRef() does not match any logical server group.
        footTarget = serverGroup != null ? serverGroup.position() : task.target();

        if (serverGroup != null && agent != null) {
            serverGroup.delegate(agent);
        }
    }

    private Vec2 resolveExitTarget(Task task) {
        if (task.exitSegment() == null) {
            return task.target();
        }
        Vec2 position = new Vec2(agentState.x(), agentState.y());
        return task.exitSegment().closestPointTo(position);
    }

    private boolean resolveGroupedLocationTarget(Task task, Set<Vec2> excluded) {
        Vec2 selected = LocationTargetSelector.choose(
                task.locationCandidates(),
                task.locationSelection(),
                new Vec2(agentState.x(), agentState.y()),
                task.locationSelectionSeed(),
                excluded);
        if (selected == null) {
            return false;
        }
        task.resolveLocationTarget(selected);
        footTarget = selected;
        return true;
    }

    private Server resolveServerGroup(Task task) {
        String groupName = task.targetRef();
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        return serverByGroup.apply(groupName);
    }

    private void setBehavior(BehaviorState nextBehavior) {
        this.behavior = nextBehavior;
        this.agentState.setState(nextBehavior);
    }

    private Task currentTask() {
        if (isPlanComplete()) {
            return null;
        }
        return (Task) plan.taskList().get(plan.currentTaskIndex());
    }

    private static Function<String, Server> buildServerLookup(List<Server> servers) {
        if (servers == null) {
            return group -> null;
        }
        Map<String, Server> byName = new java.util.LinkedHashMap<>();
        for (Server server : servers) {
            byName.put(server.name(), server);
        }
        return byName::get;
    }

    private boolean isPlanComplete() {
        return plan.currentTaskIndex() >= plan.taskList().size();
    }
}
