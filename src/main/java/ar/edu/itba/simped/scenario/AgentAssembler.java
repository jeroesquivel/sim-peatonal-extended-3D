package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.agent.AgentImpl;
import ar.edu.itba.simped.agent.plan.PlanImpl;
import ar.edu.itba.simped.agent.preom.CpmPreOM;
import ar.edu.itba.simped.agent.sensors.SensorsImpl;
import ar.edu.itba.simped.agent.statemachine.StateMachineImpl;
import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.LocationTargetSelector;
import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.PlanStep;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.Seeds;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.Task;
import ar.edu.itba.simped.core.TaskStep;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.Graph;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.OperationalModel;
import ar.edu.itba.simped.core.ports.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Arma un {@link AgentImpl} cableando los sub-módulos por agente desde un
 * {@link PlanTemplate} de G3 y el {@link Graph} real (G8).
 *
 * <p>Las dependencias compartidas entre agentes (mapa de occupied
 * locations, NeighborsIndex y Graph) se inyectan una sola vez en el
 * constructor. El {@link AgentProfile} default se asigna al
 * {@link AgentState} en {@link #wireAgent(AgentState)} — la PG puede
 * sobrescribirlo si quiere asignar perfiles por tipo de agente.</p>
 */
public final class AgentAssembler {

    // 0.5 m: a 0.3 m los agentes que se acercaban a una LOCATION/EXIT en multitud se
    // quedaban a 0.30-0.35 m y nunca "llegaban" (no disparaban arrival). 0.5 m da
    // margen sin solapar puntos contiguos (los bloques de Location están a >=1 m).
    private static final double DEFAULT_ARRIVAL_THRESHOLD = 0.5;
    private static final double DEFAULT_APPROACH_TO_ARRIVAL_RATIO = 3.0;

    private final PlanTemplate planTemplate;
    private final NeighborsIndex neighbors;
    private final Graph graph;
    private final AgentProfile defaultProfile;
    private final OperationalModel om;
    private final LocationOccupancy locationOccupancy;
    private final List<Server> servers;
    private final Map<Integer, Agent> registry;

    public AgentAssembler(
            PlanTemplate planTemplate,
            NeighborsIndex neighbors,
            Graph graph,
            AgentProfile defaultProfile,
            OperationalModel om,
            LocationOccupancy locationOccupancy,
            List<Server> servers,
            Map<Integer, Agent> registry
    ) {
        this.registry = registry;
        this.planTemplate = planTemplate;
        this.neighbors = neighbors;
        this.graph = graph;
        this.defaultProfile = defaultProfile;
        this.om = om;
        this.locationOccupancy = locationOccupancy;
        this.servers = List.copyOf(servers);
    }

    public Agent wireAgent(AgentState state) {
        return wireAgent(state, planTemplate);
    }

    public Agent wireAgent(AgentState state, PlanTemplate template) {
        if (state.profile() == null) {
            state.setProfile(defaultProfile);
        }

        PlanImpl plan = newPlanFromTemplate(state, template);

        StateMachineImpl sm = new StateMachineImpl(
                state,
                state.id(),
                plan,
                locationOccupancy,
                DEFAULT_ARRIVAL_THRESHOLD * DEFAULT_APPROACH_TO_ARRIVAL_RATIO,
                null,            // el Agent se inyecta abajo con attachAgent
                servers
        );

        SensorsImpl sensors = new SensorsImpl(state, sm, DEFAULT_ARRIVAL_THRESHOLD);
        CpmPreOM preom = new CpmPreOM(state, graph);

        AgentImpl agent = new AgentImpl(state, sensors, sm, preom, om, neighbors);
        // Registrar ANTES de attachAgent: si el plan arranca con un SERVER,
        // attachAgent delega de una y el módulo emite el primer target (I13b) por
        // los sinks, que buscan al agente en el registry. Si registráramos
        // después, ese primer target (p.ej. el punto random del semáforo) se
        // perdería y el agente caería al foot-target grueso (el centroide).
        if (registry != null) {
            registry.put(agent.id(), agent);
        }
        sm.attachAgent(agent);   // I13a: la SM ya puede delegar a los servers
        return agent;
    }

    /**
     * Resuelve el plan por-agente: para cada {@link PlanStep} elige cuáles
     * candidatos visitar según su selección ({@code CLOSEST}/{@code RANDOM}/
     * {@code ALL}) y cantidad, encadenando desde la posición del agente (un
     * cursor que avanza con cada target elegido). La selección es reproducible:
     * usa un RNG seedeado por {@code (agentId, templateName)}.
     */
    private PlanImpl newPlanFromTemplate(AgentState state, PlanTemplate t) {
        List<Task> tasks = new ArrayList<>();
        Vec2 cursor = new Vec2(state.x(), state.y());
        SplittableRandom rng = selectionRng(state, t);
        for (int i = 0; i < t.steps().size(); i++) {
            PlanStep step = t.steps().get(i);
            if (shouldDeferLocationChoiceToStateMachine(step)) {
                Task grouped = toGroupedLocationTask(state, t, step, i);
                tasks.add(grouped);
                Vec2 preview = LocationTargetSelector.choose(
                        grouped.locationCandidates(),
                        grouped.locationSelection(),
                        cursor,
                        grouped.locationSelectionSeed(),
                        Set.of());
                if (preview != null) {
                    cursor = preview;
                }
                continue;
            }
            for (TaskStep chosen : ObjectiveSelectionResolver.resolve(step, cursor, rng)) {
                tasks.add(toTask(state, t, chosen, i));
                cursor = chosen.target();
            }
        }
        return new PlanImpl(tasks);
    }

    private SplittableRandom selectionRng(AgentState state, PlanTemplate t) {
        long seed = -7046029254386353131L;   // 0x9E3779B97F4A7C15
        seed = 31L * seed + state.id();
        seed = 31L * seed + t.name().hashCode();
        // D23: con simped.seed seteada, la selección (p. ej. de salida en
        // exit_selection) varía entre réplicas; sin setear, XOR 0 = hoy.
        seed ^= Seeds.mixOr(0L, "assembler.selection");
        return new SplittableRandom(seed);
    }

    private Task toTask(AgentState state, PlanTemplate template, TaskStep step, int stepIndex) {
        return switch (step.type()) {
            case LOCATION -> Task.location(step.target(), sampleDwellSeconds(state, template, step, stepIndex), step.z());
            case SERVER -> Task.server(step.target(), step.targetBlockName(), step.z());
            case EXIT -> Task.exit(step.target(),
                    step.exitSegment().map(AgentAssembler::doorClearance).orElse(null), step.z());
        };
    }

    /**
     * Tramo útil de una salida: descuenta en cada extremo el radio máximo del
     * agente más un margen. Los extremos del segmento de EXIT suelen coincidir
     * con esquinas de pared (jambas de la puerta); si el closest-point del
     * segmento cae ahí, la repulsión de pared del OM mantiene al agente fuera
     * del umbral de arrival y queda orbitando sin salir nunca. Con el inset,
     * el target queda siempre en el vano libre de la puerta.
     */
    private static Segment doorClearance(Segment exit) {
        double clear = 0.7;
        double len = exit.length();
        if (len <= 2.0 * clear + 0.2) {
            Vec2 mid = exit.midpoint();
            return new Segment(mid, mid);
        }
        Vec2 dir = exit.b().sub(exit.a()).scale(1.0 / len);
        return new Segment(exit.a().add(dir.scale(clear)), exit.b().sub(dir.scale(clear)));
    }

    private Task toGroupedLocationTask(AgentState state, PlanTemplate template, PlanStep step, int stepIndex) {
        // Un grupo puede abarcar varias plantas (p. ej. aulas en PB y P1): pasamos
        // la planta de cada candidato para que la z efectiva del Task siga al
        // candidato que se resuelva (CLOSEST/RANDOM), no a la del primero.
        return Task.locationGroup(
                step.blockName(),
                step.candidates().stream().map(TaskStep::target).toList(),
                step.candidates().stream().map(TaskStep::z).toList(),
                step.selection(),
                sampleGroupedDwellSeconds(state, template, step, stepIndex),
                groupedLocationSeed(state, template, step, stepIndex)
        );
    }

    private double sampleDwellSeconds(AgentState state, PlanTemplate template, TaskStep step, int stepIndex) {
        return step.dwellDistribution()
                .map(distribution -> Math.max(0.0, distribution.sample(dwellRng(state, template, step, stepIndex))))
                .orElse(0.0);
    }

    private double sampleGroupedDwellSeconds(AgentState state, PlanTemplate template, PlanStep step, int stepIndex) {
        if (step.candidates().isEmpty()) {
            return 0.0;
        }
        TaskStep sampleSource = step.candidates().get(0);
        return sampleDwellSeconds(state, template, sampleSource, stepIndex);
    }

    private SplittableRandom dwellRng(AgentState state, PlanTemplate template, TaskStep step, int stepIndex) {
        long seed = 1469598103934665603L;
        seed = 31L * seed + state.id();
        seed = 31L * seed + template.name().hashCode();
        seed = 31L * seed + step.targetBlockName().hashCode();
        seed = 31L * seed + stepIndex;
        // Distingue candidatos del mismo grupo (mismo blockName e índice) por su
        // posición, para que dos targets elegidos no compartan el mismo dwell.
        seed = 31L * seed + Double.hashCode(step.target().x());
        seed = 31L * seed + Double.hashCode(step.target().y());
        seed ^= Seeds.mixOr(0L, "assembler.dwell");   // D23
        return new SplittableRandom(seed);
    }

    private long groupedLocationSeed(AgentState state, PlanTemplate template, PlanStep step, int stepIndex) {
        long seed = 1099511628211L;
        seed = 31L * seed + state.id();
        seed = 31L * seed + template.name().hashCode();
        seed = 31L * seed + step.blockName().hashCode();
        seed = 31L * seed + stepIndex;
        seed ^= Seeds.mixOr(0L, "assembler.grouped-location");   // D23
        return seed;
    }

    private boolean shouldDeferLocationChoiceToStateMachine(PlanStep step) {
        return step.type() == ar.edu.itba.simped.core.TaskType.LOCATION
                && step.candidates().size() > 1
                && step.selection() != ObjectiveSelection.ALL
                && quantityResolvesToExactlyOne(step.quantity().orElse(null));
    }

    private boolean quantityResolvesToExactlyOne(Distribution distribution) {
        if (distribution == null) {
            return true;
        }
        if (distribution instanceof Deterministic deterministic) {
            return Math.round(deterministic.value()) == 1L;
        }
        if (distribution instanceof Uniform uniform && uniform.min() == uniform.max()) {
            return Math.round(uniform.min()) == 1L;
        }
        return false;
    }
}
