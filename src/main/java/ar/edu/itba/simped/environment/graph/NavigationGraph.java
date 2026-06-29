package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Malla de navegación interna. No es el punto de entrada del módulo:
 * usar {@link StubGraph} que implementa {@link ar.edu.itba.simped.core.ports.Graph}.
 * <p>
 * TODO: cache de rutas en grilla para optimización (segunda iteración).
 */
final class NavigationGraph {

    private static final int FVP_BINARY_SEARCH_ITERS = 25;
    /** Desvío angular máximo (grados) del hop aleatorio respecto del óptimo (más lejano visible). */
    private static final double MAX_HOP_ANGLE_DEG = 10.0;

    private final Random hopRng = new Random();

    private final List<Vec2> nodes;
    private final List<Map<Integer, Double>> adjacency;
    private final List<Wall> walls;
    /** Tipo por nodo (ver {@link GridNodeReducer}: 0=área, 1=conector, 2=servidor); puede ser null. */
    private final List<Integer> nodeTypes;

    NavigationGraph(List<Vec2> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls) {
        this(nodes, adjacency, walls, null);
    }

    NavigationGraph(List<Vec2> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls,
                    List<Integer> nodeTypes) {
        this.nodes = List.copyOf(nodes);
        this.adjacency = adjacency; // mutable internamente, pero no se expone
        this.walls = List.copyOf(walls);
        this.nodeTypes = nodeTypes == null ? null : List.copyOf(nodeTypes);
    }

    // ------------------------------------------------------------------
    // I14: consulta de hop visible
    // ------------------------------------------------------------------

    Vec2 nextVisibleHop(Vec2 agentPosition, Vec2 target) {
        return queryNextVisibleHop(agentPosition, target).hop();
    }

    HopQueryResult queryNextVisibleHop(Vec2 agentPosition, Vec2 target) {
        if (isVisible(agentPosition, target)) {
            return new HopQueryResult(target, true, -1, -1, List.of(), -1, -1);
        }

        int startNode = closestVisibleNode(agentPosition);
        int endNode = closestVisibleNode(target);

        if (startNode < 0 || endNode < 0) {
            return new HopQueryResult(safeFallbackHop(agentPosition, startNode), false, startNode, endNode,
                    List.of(), -1, -1);
        }

        List<Integer> astarPath;
        if (startNode == endNode) {
            astarPath = List.of(startNode);
        } else {
            List<Integer> nodePath = AStarPathfinder.findPath(startNode, endNode, nodes, adjacency);
            if (nodePath == null) {
                return new HopQueryResult(safeFallbackHop(agentPosition, startNode), false, startNode, endNode,
                        List.of(), -1, -1);
            }
            astarPath = nodePath;
        }

        List<Vec2> geometricPath = new ArrayList<>(astarPath.size() + 1);
        for (int idx : astarPath) {
            geometricPath.add(nodes.get(idx));
        }
        geometricPath.add(target);

        FvpOnPath fvp = furthestVisibleHopOnPath(agentPosition, geometricPath);
        Vec2 hop = fvp.point();
        // Hop aleatorio: un punto sobre la arista del grafo, visible, dentro de un cono de hasta
        // MAX_HOP_ANGLE_DEG respecto de la dirección al óptimo (más lejano visible). Solo cuando el
        // FVP cae sobre una arista del grafo (entre dos nodos), no en el tramo final nodo→target.
        if (fvp.segmentFrom() >= 0 && fvp.segmentFrom() != fvp.segmentTo()) {
            hop = randomizeHopOnEdge(agentPosition, geometricPath.get(fvp.segmentFrom()), fvp.point());
        }
        return new HopQueryResult(
            hop,
            false,
            startNode,
            endNode,
            astarPath,
            fvp.segmentFrom(),
            fvp.segmentTo()
        );
    }

    /**
     * Devuelve un hop aleatorio sobre la arista {@code [visibleEnd, optimal]} (todo el tramo es
     * visible, ya que {@code optimal} es el punto más lejano visible). Se elige uniformemente entre
     * {@code optimal} y el punto del tramo cuya dirección desde {@code agent} se desvía
     * {@link #MAX_HOP_ANGLE_DEG} grados de la dirección al óptimo. Así el agente no apunta siempre
     * exactamente al óptimo, pero el hop sigue siendo visible y está sobre la arista del grafo.
     */
    private Vec2 randomizeHopOnEdge(Vec2 agent, Vec2 visibleEnd, Vec2 optimal) {
        Vec2 dOpt = optimal.sub(agent);
        Vec2 dVis = visibleEnd.sub(agent);
        if (dOpt.norm() < 1e-9 || dVis.norm() < 1e-9) {
            return optimal;
        }
        double maxRad = Math.toRadians(MAX_HOP_ANGLE_DEG);
        // s parametriza [visibleEnd(0) .. optimal(1)]; el ángulo respecto del óptimo decrece de
        // s=0 a s=1 (en s=1 vale 0). Buscamos sStar donde el ángulo = maxRad.
        double sStar = 0.0;
        if (angleBetween(dVis, dOpt) > maxRad) {
            double lo = 0.0, hi = 1.0;
            for (int i = 0; i < FVP_BINARY_SEARCH_ITERS; i++) {
                double mid = (lo + hi) / 2;
                Vec2 x = VisibilityUtils.lerp(visibleEnd, optimal, mid);
                if (angleBetween(x.sub(agent), dOpt) > maxRad) lo = mid; else hi = mid;
            }
            sStar = (lo + hi) / 2;
        }
        double s = sStar + hopRng.nextDouble() * (1.0 - sStar);
        Vec2 hop = VisibilityUtils.lerp(visibleEnd, optimal, s);
        // El cono angular no garantiza visibilidad frente a paredes: si el punto aleatorio
        // queda oculto, usamos el FVP ya validado.
        if (!isVisible(agent, hop)) {
            return optimal;
        }
        return hop;
    }

    /** Ángulo (radianes) entre dos vectores. */
    private static double angleBetween(Vec2 a, Vec2 b) {
        double cos = a.dot(b) / (a.norm() * b.norm());
        return Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
    }

    private boolean isVisible(Vec2 from, Vec2 to) {
        return VisibilityUtils.isVisible(from, to, walls);
    }

    // ------------------------------------------------------------------
    // FVP (Furthest Visible Point)
    // ------------------------------------------------------------------

    private record FvpOnPath(Vec2 point, int segmentFrom, int segmentTo) {}

    /**
     * Punto visible más lejano sobre el camino A* visto desde {@code agent}.
     * {@code segmentFrom/To} son índices en la lista de nodos A* (no incluye el target final).
     */
    private FvpOnPath furthestVisibleHopOnPath(Vec2 agent, List<Vec2> path) {
        int graphNodeCount = path.size() - 1; // último punto es el target
        for (int i = path.size() - 1; i >= 0; i--) {
            if (!isVisible(agent, path.get(i))) {
                continue;
            }
            if (i == path.size() - 1) {
                return new FvpOnPath(path.get(i), -1, -1);
            }
            if (i + 1 < graphNodeCount) {
                Vec2 hop = binarySearchFVP(agent, path.get(i), path.get(i + 1));
                return new FvpOnPath(hop, i, i + 1);
            }
            // FVP sobre tramo último nodo → target (fuera del grafo)
            Vec2 hop = binarySearchFVP(agent, path.get(i), path.get(i + 1));
            return new FvpOnPath(hop, i, i);
        }
        return new FvpOnPath(safeFallbackHop(agent, -1), -1, -1);
    }

    /**
     * Si no hay ruta o nodos visibles, no devolver el target final (atraviesa paredes):
     * devolver el nodo de grafo visible más cercano al agente, o la propia posición.
     */
    private Vec2 safeFallbackHop(Vec2 agentPosition, int knownVisibleNode) {
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
     * Punto visible más lejano sobre la arista {@code visibleEnd → notVisibleEnd} visto desde
     * {@code agent}, garantizando <b>contigüidad</b>: se avanza desde {@code visibleEnd} (que el
     * llamador ya validó como visible) y se corta en el primer punto tapado.
     *
     * <p>Una búsqueda binaria pura supondría que la visibilidad es monótona a lo largo de la arista
     * (primero visible, luego tapada). Eso NO se cumple con huecos/puertas: un punto lejano puede ser
     * visible aunque uno intermedio esté tapado por una pared, y la binaria aterrizaría en la zona
     * tapada devolviendo un hop que cruza pared. El escaneo contiguo evita ese salto; el refinamiento
     * binario solo se aplica dentro del intervalo válido [último visible, primer tapado].</p>
     */
    private Vec2 binarySearchFVP(Vec2 agent, Vec2 visibleEnd, Vec2 notVisibleEnd) {
        final int samples = 48;
        double lastVisible = 0.0;
        double firstBlocked = 1.0;
        boolean blockedFound = false;
        for (int k = 1; k <= samples; k++) {
            double t = (double) k / samples;
            if (VisibilityUtils.isVisible(agent, VisibilityUtils.lerp(visibleEnd, notVisibleEnd, t), walls)) {
                lastVisible = t;
            } else {
                firstBlocked = t;
                blockedFound = true;
                break;
            }
        }
        if (!blockedFound) {
            return VisibilityUtils.lerp(visibleEnd, notVisibleEnd, lastVisible);
        }
        double lo = lastVisible, hi = firstBlocked;
        for (int i = 0; i < FVP_BINARY_SEARCH_ITERS; i++) {
            double mid = (lo + hi) / 2;
            Vec2 midPoint = VisibilityUtils.lerp(visibleEnd, notVisibleEnd, mid);
            if (VisibilityUtils.isVisible(agent, midPoint, walls)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return VisibilityUtils.lerp(visibleEnd, notVisibleEnd, lo);
    }

    /**
     * Encuentra el nodo del grafo más cercano que sea visible desde {@code point}.
     *
     * @return índice del nodo, o -1 si ninguno es visible (no debería pasar
     *         con un grafo bien construido).
     */
    private int closestVisibleNode(Vec2 point) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            double d = point.distanceTo(nodes.get(i));
            if (d < bestDist && VisibilityUtils.isVisible(point, nodes.get(i), walls)) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Accessors (para visualización y debug)
    // ------------------------------------------------------------------

    public List<Vec2> getNodes() {
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

    /**
     * Exporta nodos y aristas a archivos CSV para el visualizador Python.
     */
    public void exportCsv(String nodesPath, String edgesPath) {
        try (BufferedWriter nw = Files.newBufferedWriter(Path.of(nodesPath))) {
            nw.write("id,x,y,type");
            nw.newLine();
            for (int i = 0; i < nodes.size(); i++) {
                int type = nodeTypes != null ? nodeTypes.get(i) : 0;
                nw.write(i + "," + nodes.get(i).x() + "," + nodes.get(i).y() + "," + type);
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
