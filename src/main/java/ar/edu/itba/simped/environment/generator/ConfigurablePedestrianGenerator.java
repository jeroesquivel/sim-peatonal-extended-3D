package ar.edu.itba.simped.environment.generator;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Seeds;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Generador de peatones configurable.
 *
 * <p>Opera como una máquina de estados tick a tick: no usa sleep ni wait.
 * Decide si generar comparando el tiempo de simulación actual con los límites
 * de los períodos activo e inactivo.</p>
 *
 * <h3>Parámetros de entrada</h3>
 * <ul>
 *   <li>{@code activeTime}  – segundos que dura la creación de agentes por ciclo.</li>
 *   <li>{@code inactiveTime} – segundos de apagado entre ciclos.</li>
 *   <li>{@code spawnZones}  – rectangulos de spawn; normalmente puertas/salidas,
 *       o el mapa completo si se usa en modo ocupación inicial.</li>
 *   <li>{@code flowRatePerMinPerDoor} – caudal en personas/minuto por puerta.
 *       Se recorta a 3 p/m × ancho de puerta si supera ese límite.</li>
 *   <li>{@code mode} – {@link GenerationMode#CALM} (de a uno) o
 *       {@link GenerationMode#BATCH} (todos de una).</li>
 *   <li>{@code planTemplates} – lista de plantillas de plan (G2), asignadas
 *       en round-robin. Menos planes que agentes → agentes comparten plan.
 *       Más planes que agentes → planes sin usar.</li>
 *   <li>{@code existingAgentsSupplier} – provee posiciones de agentes actuales
 *       (G7) para verificar solapamientos antes de cada spawn.</li>
 * </ul>
 *
 * <h3>Zonas y límite de densidad</h3>
 * <p>En zonas de puerta/salida el caudal máximo es 3 personas por metro de
 * ancho de puerta. Si el input supera ese límite, se recorta y se emite un
 * warning. En el modo ocupación inicial (todo el mapa) este límite no aplica;
 * llamar a {@link #spawnInitial()} en lugar de {@link #spawnTick}.</p>
 *
 * <h3>Anti-solapamiento</h3>
 * <p>Antes de cada spawn se verifica que la posición candidata no solape con
 * agentes existentes ni con agentes ya ubicados en este tick. Si no se puede
 * ubicar tras {@value #PLACEMENT_ATTEMPTS} intentos, el spawn queda pendiente
 * y se reintenta en ticks siguientes. Si el reintento supera
 * {@value #SPAWN_TIMEOUT_WARNING_SECONDS} s de simulación, se emite warning.</p>
 */
public final class ConfigurablePedestrianGenerator implements PlanAwarePedestrianGenerator {

    private static final Logger LOG =
        Logger.getLogger(ConfigurablePedestrianGenerator.class.getName());

    /** Densidad máxima permitida por metro de ancho de puerta (personas/min). */
    private static final double MAX_PEOPLE_PER_METER = 3.0;
    /** Tiempo de espera de spawn (s de simulación) que dispara un warning. */
    private static final double SPAWN_TIMEOUT_WARNING_SECONDS = 5.0;
    /** Radio estándar de agente [m]. */
    private static final double AGENT_RADIUS = 0.25;
    /** Separación mínima centro-a-centro al nacer [m]. */
    private static final double MIN_SEPARATION = AGENT_RADIUS * 2 + 0.05;
    /** Intentos aleatorios de ubicación antes de dejar el spawn pendiente. */
    private static final int PLACEMENT_ATTEMPTS = 200;

    // ── Configuración (inmutable post-construcción) ──────────────────────────

    private final String generatorId;
    private final double activeTime;
    private final double inactiveTime;
    private final List<Rectangle> spawnZones;
    private final GenerationMode mode;
    private final List<PlanTemplate> planTemplates;
    private final Supplier<List<AgentState>> existingAgentsSupplier;
    /** Planta (z) en la que nacen los agentes de este generador. */
    private final double spawnZ;

    /** Caudal efectivo por puerta (personas/min), tras posible recorte. */
    private final double[] effectiveFlowRatePerDoor;
    /** Agentes comprometidos por puerta por ciclo activo. */
    private final int[] agentsPerDoorPerCycle;
    /** Intervalo entre spawns individuales por puerta (CALM) [s]. */
    private final double[] interArrivalSecondsPerDoor;

    // ── Estado mutable ───────────────────────────────────────────────────────

    private GeneratorPhase phase = GeneratorPhase.ACTIVE;
    private boolean manuallyPaused = false;

    private int agentIdCounter = 0;

    /** Tiempo (simulación) en que termina la fase activa del ciclo corriente. */
    private double activePhaseEndTime;
    /** Tiempo (simulación) en que termina la fase inactiva. MAX_VALUE si no hay. */
    private double inactivePhaseEndTime = Double.MAX_VALUE;

    /** BATCH: flag que evita encolar el mismo ciclo dos veces. */
    private boolean batchQueuedThisCycle = false;

    /** CALM: próximo momento de spawn por puerta [s de simulación]. */
    private final double[] nextSpawnTimePerDoor;
    /**
     * Agentes comprometidos (spawneados + pendientes) en el ciclo actual
     * por puerta. Evita encolar más agentes de los que corresponden al ciclo.
     */
    private final int[] committedThisCyclePerDoor;

    /** Spawns que no pudieron ubicarse y esperan reintento. */
    private final Deque<PendingSpawn> pendingSpawns = new ArrayDeque<>();
    /** IDs de spawns pendientes para los que ya se emitió el warning de timeout. */
    private final Set<Integer> warnedPendingIds = new HashSet<>();
    /** Contador para IDs únicos de PendingSpawn. */
    private int pendingIdCounter = 0;

    /** Mapa agentId → planTemplate asignado, para uso del wiring externo (G9 → assembler). */
    private final Map<Integer, PlanTemplate> agentPlanMap = new HashMap<>();

    /**
     * Perfil físico con el que nacen los agentes de este generador, o
     * {@code null} para dejar que el assembler asigne el default (D22: permite
     * p. ej. el perfil "crisis" con vd mayor en el sub-escenario Evacuación,
     * derivado del {@code max_velocity} del Formato B).
     */
    private final AgentProfile profileOverride;

    private final Random rng;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param generatorId             Identificador para logs y warnings.
     * @param activeTime              Duración del período activo por ciclo [s].
     * @param inactiveTime            Duración del período de apagado entre ciclos [s].
     * @param spawnZones              Zonas de spawn (puertas o área de todo el mapa).
     * @param flowRatePerMinPerDoor   Caudal de entrada [personas/min] por puerta.
     * @param mode                    Modo de llegada: CALM o BATCH.
     * @param planTemplates           Plantillas de plan de G2, asignadas en round-robin.
     * @param existingAgentsSupplier  Proveedor de posiciones de agentes existentes (G7).
     * @param spawnZ                  Planta (z) en la que nacen los agentes de este generador.
     */
    public ConfigurablePedestrianGenerator(
            String generatorId,
            double activeTime,
            double inactiveTime,
            List<Rectangle> spawnZones,
            double flowRatePerMinPerDoor,
            GenerationMode mode,
            List<PlanTemplate> planTemplates,
            Supplier<List<AgentState>> existingAgentsSupplier,
            double spawnZ
    ) {
        this(generatorId, activeTime, inactiveTime, spawnZones, flowRatePerMinPerDoor,
                mode, planTemplates, existingAgentsSupplier, spawnZ, null);
    }

    /**
     * Variante con perfil físico propio: los agentes de este generador nacen
     * con {@code profileOverride} en lugar del default del assembler (D22).
     *
     * @param profileOverride perfil de los agentes de esta zona, o {@code null}
     *                        para usar el default.
     */
    public ConfigurablePedestrianGenerator(
            String generatorId,
            double activeTime,
            double inactiveTime,
            List<Rectangle> spawnZones,
            double flowRatePerMinPerDoor,
            GenerationMode mode,
            List<PlanTemplate> planTemplates,
            Supplier<List<AgentState>> existingAgentsSupplier,
            double spawnZ,
            AgentProfile profileOverride
    ) {
        if (spawnZones == null || spawnZones.isEmpty())
            throw new IllegalArgumentException("spawnZones must be non-empty");
        if (planTemplates == null || planTemplates.isEmpty())
            throw new IllegalArgumentException("planTemplates must be non-empty");
        if (existingAgentsSupplier == null)
            throw new IllegalArgumentException("existingAgentsSupplier required");
        if (activeTime <= 0)
            throw new IllegalArgumentException("activeTime must be positive");
        if (inactiveTime < 0)
            throw new IllegalArgumentException("inactiveTime must be >= 0");
        if (flowRatePerMinPerDoor <= 0)
            throw new IllegalArgumentException("flowRatePerMinPerDoor must be positive");
        if (mode == null)
            throw new IllegalArgumentException("mode required");

        this.generatorId            = (generatorId != null && !generatorId.isBlank()) ? generatorId : "gen";
        this.rng                    = Seeds.rng(this.generatorId);
        this.activeTime             = activeTime;
        this.inactiveTime           = inactiveTime;
        this.spawnZones             = List.copyOf(spawnZones);
        this.mode                   = mode;
        this.planTemplates          = List.copyOf(planTemplates);
        this.existingAgentsSupplier = existingAgentsSupplier;
        this.spawnZ                 = spawnZ;
        this.profileOverride        = profileOverride;

        int n = this.spawnZones.size();
        this.effectiveFlowRatePerDoor    = new double[n];
        this.agentsPerDoorPerCycle       = new int[n];
        this.interArrivalSecondsPerDoor  = new double[n];
        this.nextSpawnTimePerDoor        = new double[n];
        this.committedThisCyclePerDoor   = new int[n];

        for (int i = 0; i < n; i++) {
            Rectangle zone    = this.spawnZones.get(i);
            double doorWidth  = Math.max(zone.width(), zone.height());

            if (mode == GenerationMode.BATCH) {
                // instant_occupation: el parámetro es una CANTIDAD literal de
                // agentes por puerta (no un caudal). No se recorta por densidad
                // de puerta — la ocupación inicial puede usar un área grande.
                this.effectiveFlowRatePerDoor[i]   = flowRatePerMinPerDoor;
                this.agentsPerDoorPerCycle[i]      = Math.max(1, (int) Math.round(flowRatePerMinPerDoor));
                this.interArrivalSecondsPerDoor[i] = 0.0;   // no aplica en BATCH
            } else {
                // flowrate (CALM): el parámetro es un caudal [personas/min] por
                // puerta. Se recorta a 3 p/m × ancho si lo supera.
                double maxRate = MAX_PEOPLE_PER_METER * doorWidth;
                double rate    = flowRatePerMinPerDoor;
                if (rate > maxRate) {
                    LOG.warning(String.format(
                        "[%s] Puerta %d: caudal %.2f p/min supera el máximo de %.2f p/min" +
                        " (ancho %.2f m × 3 p/m). Recortando a %.2f.",
                        this.generatorId, i, flowRatePerMinPerDoor, maxRate, doorWidth, maxRate));
                    rate = maxRate;
                }
                this.effectiveFlowRatePerDoor[i]   = rate;
                this.agentsPerDoorPerCycle[i]      = Math.max(1, (int) Math.round(rate * activeTime / 60.0));
                this.interArrivalSecondsPerDoor[i] = 60.0 / rate;
            }
        }

        this.activePhaseEndTime = activeTime;
    }

    // ── PedestrianGenerator ──────────────────────────────────────────────────

    /**
     * Ocupación inicial en t=0 — sólo para modo {@link GenerationMode#BATCH}
     * (instant_occupation): coloca el lote del primer ciclo de una sola vez.
     * No aplica el límite de 3 p/m. Los agentes que no caben por solapamiento
     * quedan pendientes para {@link #spawnTick}.
     *
     * <p>En modo {@link GenerationMode#CALM} no genera nada: el goteo arranca
     * desde el primer {@link #spawnTick} (t=0). Marca el lote del primer ciclo
     * como ya sembrado y NO toca el timing de fase, de modo que el
     * {@code spawnTick(0)} que el driver llama a continuación no lo duplique.</p>
     */
    @Override
    public List<Agent> spawnInitial() {
        if (manuallyPaused || phase == GeneratorPhase.DONE) return List.of();
        if (mode != GenerationMode.BATCH) return List.of();

        List<AgentState> existing  = existingAgentsSupplier.get();
        List<Vec2>       placed    = new ArrayList<>();
        List<Agent>      spawned   = new ArrayList<>();

        for (int di = 0; di < spawnZones.size(); di++) {
            int total = agentsPerDoorPerCycle[di];
            for (int j = 0; j < total; j++) {
                Vec2 pos = tryPlace(spawnZones.get(di), existing, placed);
                if (pos != null) {
                    placed.add(pos);
                    spawned.add(buildAgent(pos, consumeNextPlanTemplate()));
                } else {
                    pendingSpawns.add(new PendingSpawn(di, 0.0, consumePlanIndex(), pendingIdCounter++));
                }
                committedThisCyclePerDoor[di]++;
            }
        }

        // El lote del primer ciclo activo ya quedó sembrado: el tickBatch del
        // primer spawnTick(0) lo saltea por este flag. El timing de fase queda
        // como lo dejó el constructor (ACTIVE hasta activeTime).
        batchQueuedThisCycle = true;
        return spawned;
    }

    /**
     * Spawn por tick: máquina de estados sin sleep.
     *
     * <ul>
     *   <li>CALM  – un agente por puerta cada {@code interArrival} segundos.</li>
     *   <li>BATCH – todos los agentes del ciclo de una vez al inicio del período activo.</li>
     * </ul>
     *
     * <p>En cada tick también se procesan los spawns pendientes por solapamiento.</p>
     */
    @Override
    public List<Agent> spawnTick(double currentTime, double dt) {
        if (manuallyPaused || phase == GeneratorPhase.DONE) return List.of();

        advancePhase(currentTime);

        List<AgentState> existing       = existingAgentsSupplier.get();
        List<Vec2>       placedThisTick = new ArrayList<>();
        List<Agent>      spawned        = new ArrayList<>();

        drainPending(currentTime, existing, placedThisTick, spawned);

        if (phase == GeneratorPhase.ACTIVE) {
            switch (mode) {
                case BATCH -> tickBatch(currentTime, existing, placedThisTick, spawned);
                case CALM  -> tickCalm(currentTime,  existing, placedThisTick, spawned);
            }
        }

        return spawned;
    }

    // ── Control manual ───────────────────────────────────────────────────────

    /** Pausa el generador; no genera hasta llamar a {@link #resume()}. */
    public void pause() {
        manuallyPaused = true;
    }

    /** Reanuda la generación si estaba pausada manualmente. */
    public void resume() {
        manuallyPaused = false;
    }

    public boolean isPaused()         { return manuallyPaused; }
    public GeneratorPhase phase()     { return phase; }
    public String generatorId()       { return generatorId; }

    // ── PlanAwarePedestrianGenerator ─────────────────────────────────────────

    @Override
    public PlanTemplate planTemplateFor(int agentId) {
        return agentPlanMap.get(agentId);
    }

    // ── Lógica interna ───────────────────────────────────────────────────────

    /** Avanza la fase (ACTIVE → INACTIVE → ACTIVE) según el tiempo de simulación. */
    private void advancePhase(double currentTime) {
        if (phase == GeneratorPhase.ACTIVE && currentTime >= activePhaseEndTime) {
            if (inactiveTime > 0) {
                phase = GeneratorPhase.INACTIVE;
                inactivePhaseEndTime = activePhaseEndTime + inactiveTime;
            } else {
                startNewCycle(activePhaseEndTime);
            }
        }
        if (phase == GeneratorPhase.INACTIVE && currentTime >= inactivePhaseEndTime) {
            startNewCycle(inactivePhaseEndTime);
        }
    }

    /** Inicia un nuevo ciclo activo a partir de {@code fromTime}. */
    private void startNewCycle(double fromTime) {
        phase                = GeneratorPhase.ACTIVE;
        activePhaseEndTime   = fromTime + activeTime;
        inactivePhaseEndTime = Double.MAX_VALUE;
        batchQueuedThisCycle = false;
        for (int i = 0; i < spawnZones.size(); i++) {
            committedThisCyclePerDoor[i] = 0;
            nextSpawnTimePerDoor[i]      = fromTime;
        }
    }

    /** BATCH: encola todos los agentes del ciclo al inicio del período activo. */
    private void tickBatch(double currentTime, List<AgentState> existing,
                           List<Vec2> placedThisTick, List<Agent> spawned) {
        if (batchQueuedThisCycle) return;
        batchQueuedThisCycle = true;

        for (int di = 0; di < spawnZones.size(); di++) {
            int remaining = agentsPerDoorPerCycle[di] - committedThisCyclePerDoor[di];
            for (int j = 0; j < remaining; j++) {
                Vec2 pos = tryPlace(spawnZones.get(di), existing, placedThisTick);
                if (pos != null) {
                    placedThisTick.add(pos);
                    spawned.add(buildAgent(pos, consumeNextPlanTemplate()));
                    committedThisCyclePerDoor[di]++;
                } else {
                    pendingSpawns.add(new PendingSpawn(di, currentTime, consumePlanIndex(), pendingIdCounter++));
                    committedThisCyclePerDoor[di]++;
                }
            }
        }
    }

    /** CALM: un agente por puerta cada interArrival segundos. */
    private void tickCalm(double currentTime, List<AgentState> existing,
                          List<Vec2> placedThisTick, List<Agent> spawned) {
        for (int di = 0; di < spawnZones.size(); di++) {
            if (committedThisCyclePerDoor[di] >= agentsPerDoorPerCycle[di]) continue;
            if (currentTime < nextSpawnTimePerDoor[di]) continue;

            nextSpawnTimePerDoor[di] = currentTime + interArrivalSecondsPerDoor[di];
            committedThisCyclePerDoor[di]++;

            Vec2 pos = tryPlace(spawnZones.get(di), existing, placedThisTick);
            if (pos != null) {
                placedThisTick.add(pos);
                spawned.add(buildAgent(pos, consumeNextPlanTemplate()));
            } else {
                pendingSpawns.add(new PendingSpawn(di, currentTime, consumePlanIndex(), pendingIdCounter++));
            }
        }
    }

    /**
     * Intenta ubicar los spawns pendientes (bloqueados por solapamiento en ticks previos).
     * Los que llevan más de {@value #SPAWN_TIMEOUT_WARNING_SECONDS} s emiten warning.
     */
    private void drainPending(double currentTime, List<AgentState> existing,
                              List<Vec2> placedThisTick, List<Agent> spawned) {
        int size = pendingSpawns.size();
        for (int i = 0; i < size; i++) {
            PendingSpawn ps = pendingSpawns.poll();
            if (ps == null) break;

            double elapsed = currentTime - ps.queuedAt();
            if (elapsed > SPAWN_TIMEOUT_WARNING_SECONDS
                    && !warnedPendingIds.contains(ps.pendingId())) {
                LOG.warning(String.format(
                    "[%s] Puerta %d: agente lleva %.1f s esperando lugar libre. "
                    + "¿La zona está bloqueada por agentes salientes?",
                    generatorId, ps.zoneIndex(), elapsed));
                warnedPendingIds.add(ps.pendingId());
            }

            Vec2 pos = tryPlace(spawnZones.get(ps.zoneIndex()), existing, placedThisTick);
            if (pos != null) {
                placedThisTick.add(pos);
                PlanTemplate pt = planTemplates.get(ps.planIndex() % planTemplates.size());
                spawned.add(buildAgent(pos, pt));
                warnedPendingIds.remove(ps.pendingId());
            } else {
                pendingSpawns.add(ps);
            }
        }
    }

    /**
     * Intenta encontrar una posición libre dentro de {@code zone} en
     * {@value #PLACEMENT_ATTEMPTS} intentos aleatorios.
     *
     * @return posición libre, o {@code null} si no se encontró.
     */
    private Vec2 tryPlace(Rectangle zone, List<AgentState> existing, List<Vec2> batch) {
        double xMin = Math.min(zone.a().x(), zone.c().x());
        double yMin = Math.min(zone.a().y(), zone.c().y());

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double x = xMin + rng.nextDouble() * zone.width();
            double y = yMin + rng.nextDouble() * zone.height();
            Vec2 candidate = new Vec2(x, y);
            if (!overlapsAny(candidate, existing, batch)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean overlapsAny(Vec2 pos, List<AgentState> existing, List<Vec2> batch) {
        for (AgentState a : existing) {
            double minDist = AGENT_RADIUS + a.radius() + 0.05;
            if (Math.hypot(pos.x() - a.x(), pos.y() - a.y()) < minDist) return true;
        }
        for (Vec2 b : batch) {
            if (Math.hypot(pos.x() - b.x(), pos.y() - b.y()) < MIN_SEPARATION) return true;
        }
        return false;
    }

    /** Selecciona aleatoriamente una plantilla de plan con probabilidad uniforme 1/N. */
    private PlanTemplate consumeNextPlanTemplate() {
        return planTemplates.get(rng.nextInt(planTemplates.size()));
    }

    /** Selecciona aleatoriamente un índice de plan con probabilidad uniforme 1/N. */
    private int consumePlanIndex() {
        return rng.nextInt(planTemplates.size());
    }

    private Agent buildAgent(Vec2 pos, PlanTemplate planTemplate) {
        int id        = agentIdCounter++;
        AgentState st = new AgentState(id, planTemplate.name());
        st.setPosition(pos.x(), pos.y());
        st.setZ(spawnZ);
        st.setRadius(AGENT_RADIUS);
        st.setState(BehaviorState.IDLE);
        if (profileOverride != null) {
            st.setProfile(profileOverride);
        }
        agentPlanMap.put(id, planTemplate);
        return new SimpleAgent(st);
    }

    // ── Inner types ──────────────────────────────────────────────────────────

    /** Spawn que no pudo ubicarse y espera reintento en el próximo tick. */
    private record PendingSpawn(int zoneIndex, double queuedAt, int planIndex, int pendingId) {}

    /** Contenedor mínimo de agente. Sub-módulos se conectan externamente (WiredPedestrianGenerator). */
    static final class SimpleAgent implements Agent {
        private final AgentState state;

        SimpleAgent(AgentState state) { this.state = state; }

        @Override public void step(double dt) { /* pipeline conectado por WiredPedestrianGenerator */ }
        @Override public AgentState state()   { return state; }
        @Override public int id()             { return state.id(); }
    }
}
