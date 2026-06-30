package ar.edu.itba.simped.agent.preom;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Graph;
import ar.edu.itba.simped.core.ports.PreOM;

/**
 * Pre-Operational Model (PreOM) implementation for Group 7.
 * Resolves the intermediate foot-target by routing through the Graph
 * if direct line-of-sight is blocked, and handles direct server targets.
 *
 * <p>Graph-query throttling (pedido por G8): consultar el Graph en cada
 * {@code dt} es caro (A* + visibilidad). Aplicamos dos optimizaciones que
 * no cambian el comportamiento, solo reducen llamadas a {@link Graph}:</p>
 * <ol>
 *   <li><b>Re-query cada ~0.25 s</b>: entre consultas devolvemos el último
 *       hop cacheado. El intervalo se mide acumulando el {@code dt} real
 *       inyectado por el AgentAssembler (default {@link #DEFAULT_DT} si no
 *       se provee).</li>
 *   <li><b>Target visible ⇒ no consultar más</b>: cuando {@code nextVisibleHop}
 *       devuelve el target mismo (línea de vista directa), dejamos de
 *       consultar el Graph y vamos directo al target. El corte se resetea
 *       cuando cambia el target activo (nuevo {@link #activate} /
 *       {@link #onServerTarget}).</li>
 * </ol>
 */
public final class CpmPreOM implements PreOM {

    /** Intervalo mínimo entre consultas al Graph. */
    private static final double REQUERY_INTERVAL_SECONDS = 0.25;

    /** dt asumido si el AgentAssembler no inyecta uno (retrocompat). */
    private static final double DEFAULT_DT = 0.05;

    private final AgentState agentState;
    private final Graph graph;
    private final double dt;

    private Vec3 activeTarget;

    // Estado de throttling
    private Vec3 cachedHop;
    private double timeSinceLastQuery;

    /**
     * Default constructor for generic instantiations.
     */
    public CpmPreOM() {
        this(null, null, DEFAULT_DT);
    }

    /**
     * Stateful constructor for a specific agent (retrocompat: asume dt default).
     */
    public CpmPreOM(AgentState agentState, Graph graph) {
        this(agentState, graph, DEFAULT_DT);
    }

    /**
     * Stateful constructor con dt real de la simulación, para que el
     * re-query cada 0.25 s sea exacto independientemente del dt del escenario.
     */
    public CpmPreOM(AgentState agentState, Graph graph, double dt) {
        this.agentState = agentState;
        this.graph = graph;
        this.dt = dt > 0.0 ? dt : DEFAULT_DT;
        this.activeTarget = null;
        resetThrottle();
    }

    @Override
    public Vec3 resolvedFootTarget() {
        if (activeTarget == null) {
            return null;
        }

        // Sin graph o sin agente: ir directo al target (sin routing).
        if (graph == null || agentState == null) {
            return activeTarget;
        }

        // Re-query throttling: devolvemos el hop cacheado hasta que pase
        // REQUERY_INTERVAL_SECONDS, y ahí re-consultamos el Graph. NO cacheamos
        // "target visible para siempre": el agente puede desviarse (esquivando a
        // otro o por la dinámica del CPM) y quedar con una pared entre él y el
        // target; si dejáramos de re-rutear, empujaría contra la pared en vez de
        // rodearla. Re-consultar cada 0.25 s corrige el rumbo a tiempo.
        timeSinceLastQuery += dt;
        if (cachedHop != null && timeSinceLastQuery < REQUERY_INTERVAL_SECONDS) {
            return cachedHop;
        }
        timeSinceLastQuery = 0.0;

        Vec3 currentPos = agentState.position();

        // I14: nextVisibleHop devuelve el target si hay línea de vista directa,
        // o el Furthest Visible Point sobre la ruta A* si no.
        Vec3 hop = graph.nextVisibleHop(currentPos, activeTarget);
        cachedHop = hop;
        return hop;
    }

    @Override
    public void activate(Vec3 footTarget) {
        // I11: Activates target resolution for a normal plan task.
        // Si el target cambió, reseteamos el estado de throttling.
        if (!sameTarget(footTarget, this.activeTarget)) {
            this.activeTarget = footTarget;
            resetThrottle();
        }
    }

    @Override
    public void onServerTarget(Vec3 target) {
        // I13b: Server-provided queue/service target overrides during delegation.
        if (!sameTarget(target, this.activeTarget)) {
            this.activeTarget = target;
            resetThrottle();
        }
    }

    private void resetThrottle() {
        this.cachedHop = null;
        this.timeSinceLastQuery = REQUERY_INTERVAL_SECONDS; // fuerza query inmediata
    }

    private static boolean sameTarget(Vec3 a, Vec3 b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
