package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Malla de navegación interna <b>multiplanta</b> (paso 4, D6). No es el punto de
 * entrada del módulo: usar {@link StubGraph} que implementa
 * {@link ar.edu.itba.simped.core.ports.Graph}.
 *
 * <p>Los nodos son {@link Vec3} (cada uno en su planta {@code z}). El grafo se
 * arma corriendo el generador por grilla una vez por planta y uniendo las
 * plantas con aristas de escalera (ver {@link GraphBuilder}). El A* usa la
 * distancia euclídea 3D como heurística. La <b>visibilidad y el FVP son por
 * planta</b>: dos puntos en plantas distintas nunca son visibles entre sí (el
 * cruce de plantas solo ocurre por las aristas de escalera del grafo).</p>
 */
final class NavigationGraph {

    private static final int FVP_BINARY_SEARCH_ITERS = 25;
    /** Desvío angular máximo (grados) del hop aleatorio respecto del óptimo (más lejano visible). */
    private static final double MAX_HOP_ANGLE_DEG = 10.0;
    /** Tolerancia para agrupar paredes/nodos por planta. */
    private static final double FLOOR_EPS = 1e-6;

    private final Random hopRng = new Random();

    private final List<Vec3> nodes;
    private final List<Map<Integer, Double>> adjacency;
    private final List<Wall> walls;
    /** Paredes agrupadas por planta (clave = z redondeada), para visibilidad planar. */
    private final Map<Long, List<Wall>> wallsByFloor;
    /** Tipo por nodo (0=área, 1=conector, 2=servidor, 3=escalera); puede ser null. */
    private final List<Integer> nodeTypes;

    NavigationGraph(List<Vec3> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls) {
        this(nodes, adjacency, walls, null);
    }

    NavigationGraph(List<Vec3> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls,
                    List<Integer> nodeTypes) {
        this.nodes = List.copyOf(nodes);
        this.adjacency = adjacency; // mutable internamente, pero no se expone
        this.walls = List.copyOf(walls);
        this.nodeTypes = nodeTypes == null ? null : List.copyOf(nodeTypes);
        this.wallsByFloor = new HashMap<>();
        for (Wall w : this.walls) {
            wallsByFloor.computeIfAbsent(floorKey(w.z()), k -> new ArrayList<>()).add(w);
        }
    }

    private static long floorKey(double z) {
        return Math.round(z / FLOOR_EPS);
    }

    private static boolean sameFloor(double a, double b) {
        return floorKey(a) == floorKey(b);
    }

    private List<Wall> wallsOfFloor(double z) {
        return wallsByFloor.getOrDefault(floorKey(z), List.of());
    }

    // ------------------------------------------------------------------
    // I14: consulta de hop visible (3D)
    // ------------------------------------------------------------------

    Vec3 nextVisibleHop(Vec3 agentPosition, Vec3 target) {
        return computeQuery(agentPosition, target).hop3();
    }

    /** Variante detallada (debug/visualización); el {@code hop} del resultado es planar (xy). */
    HopQueryResult queryNextVisibleHop(Vec3 agentPosition, Vec3 target) {
        Query q = computeQuery(agentPosition, target);
        return new HopQueryResult(q.hop3().xy(), q.targetVisible(),
                q.startNode(), q.endNode(), q.astarPath(), q.segmentFrom(), q.segmentTo());
    }

    private record Query(Vec3 hop3, boolean targetVisible, int startNode, int endNode,
                         List<Integer> astarPath, int segmentFrom, int segmentTo) {}

    private Query computeQuery(Vec3 agentPosition, Vec3 target) {
        if (isVisible(agentPosition, target)) {
            return new Query(target, true, -1, -1, List.of(), -1, -1);
        }

        int startNode = closestVisibleNode(agentPosition);
        int endNode = closestVisibleNode(target);

        if (startNode < 0 || endNode < 0) {
            return new Query(safeFallbackHop(agentPosition, startNode), false, startNode, endNode,
                    List.of(), -1, -1);
        }

        List<Integer> astarPath;
        if (startNode == endNode) {
            astarPath = List.of(startNode);
        } else {
            List<Integer> nodePath = AStarPathfinder.findPath(startNode, endNode, nodes, adjacency);
            if (nodePath == null) {
                return new Query(safeFallbackHop(agentPosition, startNode), false, startNode, endNode,
                        List.of(), -1, -1);
            }
            astarPath = nodePath;
        }

        List<Vec3> geometricPath = new ArrayList<>(astarPath.size() + 1);
        for (int idx : astarPath) {
            geometricPath.add(nodes.get(idx));
        }
        geometricPath.add(target);

        FvpOnPath fvp = furthestVisibleHopOnPath(agentPosition, geometricPath);
        Vec3 hop = fvp.point();
        // Hop aleatorio sobre la arista del grafo (mismo comportamiento que en 2D),
        // solo cuando el FVP cae entre dos nodos del grafo (mismo plano).
        if (fvp.segmentFrom() >= 0 && fvp.segmentFrom() != fvp.segmentTo()) {
            hop = randomizeHopOnEdge(agentPosition, geometricPath.get(fvp.segmentFrom()), fvp.point());
        }
        return new Query(hop, false, startNode, endNode, astarPath, fvp.segmentFrom(), fvp.segmentTo());
    }

    /**
     * Hop aleatorio sobre la arista {@code [visibleEnd, optimal]} (todo el tramo visible, mismo
     * plano), dentro de un cono de hasta {@link #MAX_HOP_ANGLE_DEG} respecto del óptimo.
     */
    private Vec3 randomizeHopOnEdge(Vec3 agent, Vec3 visibleEnd, Vec3 optimal) {
        Vec3 dOpt = optimal.sub(agent);
        Vec3 dVis = visibleEnd.sub(agent);
        if (dOpt.norm() < 1e-9 || dVis.norm() < 1e-9) {
            return optimal;
        }
        double maxRad = Math.toRadians(MAX_HOP_ANGLE_DEG);
        double sStar = 0.0;
        if (angleBetween(dVis, dOpt) > maxRad) {
            double lo = 0.0, hi = 1.0;
            for (int i = 0; i < FVP_BINARY_SEARCH_ITERS; i++) {
                double mid = (lo + hi) / 2;
                Vec3 x = lerp3(visibleEnd, optimal, mid);
                if (angleBetween(x.sub(agent), dOpt) > maxRad) lo = mid; else hi = mid;
            }
            sStar = (lo + hi) / 2;
        }
        double s = sStar + hopRng.nextDouble() * (1.0 - sStar);
        Vec3 hop = lerp3(visibleEnd, optimal, s);
        if (!isVisible(agent, hop)) {
            return optimal;
        }
        return hop;
    }

    private static double angleBetween(Vec3 a, Vec3 b) {
        double cos = a.dot(b) / (a.norm() * b.norm());
        return Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
    }

    private static Vec3 lerp3(Vec3 a, Vec3 b, double t) {
        return a.add(b.sub(a).scale(t));
    }

    /**
     * Visibilidad <b>por planta</b>: si {@code from} y {@code to} están en plantas distintas no
     * son visibles (hay que usar la escalera). En la misma planta, visibilidad planar contra las
     * paredes de esa planta.
     */
    private boolean isVisible(Vec3 from, Vec3 to) {
        if (!sameFloor(from.z(), to.z())) {
            return false;
        }
        return VisibilityUtils.isVisible(from.xy(), to.xy(), wallsOfFloor(from.z()));
    }

    // ------------------------------------------------------------------
    // FVP (Furthest Visible Point)
    // ------------------------------------------------------------------

    private record FvpOnPath(Vec3 point, int segmentFrom, int segmentTo) {}

    private FvpOnPath furthestVisibleHopOnPath(Vec3 agent, List<Vec3> path) {
        int graphNodeCount = path.size() - 1; // último punto es el target
        for (int i = path.size() - 1; i >= 0; i--) {
            if (!isVisible(agent, path.get(i))) {
                continue;
            }
            if (i == path.size() - 1) {
                return new FvpOnPath(path.get(i), -1, -1);
            }
            if (i + 1 < graphNodeCount) {
                Vec3 hop = binarySearchFVP(agent, path.get(i), path.get(i + 1));
                return new FvpOnPath(hop, i, i + 1);
            }
            Vec3 hop = binarySearchFVP(agent, path.get(i), path.get(i + 1));
            return new FvpOnPath(hop, i, i);
        }
        return new FvpOnPath(safeFallbackHop(agent, -1), -1, -1);
    }

    private Vec3 safeFallbackHop(Vec3 agentPosition, int knownVisibleNode) {
        if (knownVisibleNode >= 0) {
            return nodes.get(knownVisibleNode);
        }
        int n = closestVisibleNode(agentPosition);
        if (n >= 0) {
            return nodes.get(n);
        }
        return agentPosition;
    }

    /**
     * FVP sobre la arista {@code visibleEnd → notVisibleEnd}: escaneo contiguo + refinamiento
     * binario (igual que en 2D). Si los extremos están en plantas distintas (arista de escalera
     * en el camino), el primer punto interpolado ya no es visible y el resultado queda en
     * {@code visibleEnd} (el agente se dirige al pie de la escalera).
     */
    private Vec3 binarySearchFVP(Vec3 agent, Vec3 visibleEnd, Vec3 notVisibleEnd) {
        // Si el tramo cruza plantas (arista de escalera en el camino), el FVP no
        // avanza sobre él: el hop es exactamente el pie de la escalera. El cruce
        // de planta lo hace la dinámica de la escalera (paso 6), no el ruteo.
        if (!sameFloor(visibleEnd.z(), notVisibleEnd.z())) {
            return visibleEnd;
        }
        final int samples = 48;
        double lastVisible = 0.0;
        double firstBlocked = 1.0;
        boolean blockedFound = false;
        for (int k = 1; k <= samples; k++) {
            double t = (double) k / samples;
            if (isVisible(agent, lerp3(visibleEnd, notVisibleEnd, t))) {
                lastVisible = t;
            } else {
                firstBlocked = t;
                blockedFound = true;
                break;
            }
        }
        if (!blockedFound) {
            return lerp3(visibleEnd, notVisibleEnd, lastVisible);
        }
        double lo = lastVisible, hi = firstBlocked;
        for (int i = 0; i < FVP_BINARY_SEARCH_ITERS; i++) {
            double mid = (lo + hi) / 2;
            if (isVisible(agent, lerp3(visibleEnd, notVisibleEnd, mid))) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lerp3(visibleEnd, notVisibleEnd, lo);
    }

    /**
     * Nodo del grafo más cercano (en 3D) que sea visible desde {@code point}. Como la visibilidad
     * es por planta, en la práctica solo considera nodos de la misma planta que {@code point}.
     */
    private int closestVisibleNode(Vec3 point) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            double d = point.distanceTo(nodes.get(i));
            if (d < bestDist && isVisible(point, nodes.get(i))) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Accessors (para visualización y debug)
    // ------------------------------------------------------------------

    public List<Vec3> getNodes() {
        return nodes;
    }

    public List<Map<Integer, Double>> getAdjacency() {
        return adjacency;
    }

    public List<Wall> getWalls() {
        return walls;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        int count = 0;
        for (Map<Integer, Double> neighbors : adjacency) {
            count += neighbors.size();
        }
        return count / 2; // cada arista está en ambas listas
    }

    // ------------------------------------------------------------------
    // CSV export (para visualización con Python)
    // ------------------------------------------------------------------

    /** Exporta nodos y aristas a CSV para el visualizador Python (incluye z). */
    public void exportCsv(String nodesPath, String edgesPath) {
        try (BufferedWriter nw = Files.newBufferedWriter(Path.of(nodesPath))) {
            nw.write("id,x,y,z,type");
            nw.newLine();
            for (int i = 0; i < nodes.size(); i++) {
                int type = nodeTypes != null ? nodeTypes.get(i) : 0;
                Vec3 n = nodes.get(i);
                nw.write(i + "," + n.x() + "," + n.y() + "," + n.z() + "," + type);
                nw.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing nodes CSV", e);
        }

        try (BufferedWriter ew = Files.newBufferedWriter(Path.of(edgesPath))) {
            ew.write("from,to");
            ew.newLine();
            Set<String> written = new HashSet<>();
            for (int i = 0; i < adjacency.size(); i++) {
                for (int j : adjacency.get(i).keySet()) {
                    String key = Math.min(i, j) + "-" + Math.max(i, j);
                    if (written.add(key)) {
                        ew.write(i + "," + j);
                        ew.newLine();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing edges CSV", e);
        }
    }
}
