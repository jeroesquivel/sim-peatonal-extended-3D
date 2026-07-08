package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Seeds;
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

    private final Random hopRng = Seeds.rng("navgraph");

    private final List<Vec3> nodes;
    private final List<Map<Integer, Double>> adjacency;
    private final List<Wall> walls;
    /** Paredes agrupadas por planta (clave = z redondeada), para visibilidad planar. */
    private final Map<Long, List<Wall>> wallsByFloor;
    /** Tipo por nodo (0=área, 1=conector, 2=servidor, 3=escalera); puede ser null. */
    private final List<Integer> nodeTypes;

    /** Aristas de escalera: pares de nodos en plantas distintas (pie↔tope). */
    private final List<int[]> stairEdges;
    /** Niveles de planta distintos presentes en los nodos (claves redondeadas). */
    private final java.util.Set<Long> floorKeys;

    /**
     * Cuán cerca del extremo cercano de la escalera (el pie al subir, el tope al
     * bajar) el agente cambia el hop hacia el otro extremo para empezar a recorrer
     * el tramo. Se mide <b>a lo largo del eje</b> del tramo (no distancia euclídea):
     * es la misma proyección que usa {@link ar.edu.itba.simped.core.Stairs#zAt}, así
     * que cuando el hop cambia, {@code zAt(agente) ≈ zAt(extremo) = z de la planta} y
     * la {@code z} engancha desde el nivel del piso <b>sin salto</b>.
     *
     * <p><b>Bug del salto de z (D21).</b> Con el valor viejo (1.5, medido como
     * distancia euclídea al pie) el cambio de hop ocurría a mitad de la huella
     * (avance ≈ 0.37 sobre un tramo de 4 m con Δz=1.5), donde {@code zAt ≈ 0.56}: al
     * enganchar la {@code z} el agente <b>saltaba de 0 a ~0.56 en un frame</b>. Con
     * 0.15 a-lo-largo-del-eje el cambio de hop ocurre pegado al pie (avance ≈0), donde
     * {@code zAt ≈ 0}, así la z arranca en ~0. Funciona junto con la <b>exclusión de
     * las huellas de la grilla</b> ({@code GridNodeReducer}), que limpia los nodos del
     * tubo y evita el atasco en el descanso (turnback) que este reach chico causaba
     * antes con la grilla vieja. */
    private static final double STAIR_FOOT_REACH = 0.15;
    /** Tolerancia planar para considerar que el agente está sobre el eje de una escalera. */
    private static final double STAIR_AXIS_TOL = 3.0;

    /**
     * Tramo de escalera con su semiancho, para el gate lateral del hop (D24): el
     * cambio de hop hacia el otro extremo sólo ocurre si el agente está DE FRENTE
     * al tramo (desvío perpendicular ≤ semiancho), no sólo cerca a lo largo del eje.
     */
    record StairSpan(Vec3 a, Vec3 b, double halfWidth) {
        boolean matches(Vec3 p, Vec3 q) {
            return (a.equals(p) && b.equals(q)) || (a.equals(q) && b.equals(p));
        }
    }

    private final List<StairSpan> stairSpans;

    NavigationGraph(List<Vec3> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls) {
        this(nodes, adjacency, walls, null, null);
    }

    NavigationGraph(List<Vec3> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls,
                    List<Integer> nodeTypes) {
        this(nodes, adjacency, walls, nodeTypes, null);
    }

    NavigationGraph(List<Vec3> nodes,
                    List<Map<Integer, Double>> adjacency,
                    List<Wall> walls,
                    List<Integer> nodeTypes,
                    List<StairSpan> stairSpans) {
        this.stairSpans = stairSpans == null ? List.of() : List.copyOf(stairSpans);
        this.nodes = List.copyOf(nodes);
        this.adjacency = adjacency; // mutable internamente, pero no se expone
        this.walls = List.copyOf(walls);
        this.nodeTypes = nodeTypes == null ? null : List.copyOf(nodeTypes);
        this.wallsByFloor = new HashMap<>();
        for (Wall w : this.walls) {
            wallsByFloor.computeIfAbsent(floorKey(w.z()), k -> new ArrayList<>()).add(w);
        }
        this.floorKeys = new java.util.HashSet<>();
        for (Vec3 n : this.nodes) {
            floorKeys.add(floorKey(n.z()));
        }
        this.stairEdges = new ArrayList<>();
        for (int a = 0; a < this.nodes.size(); a++) {
            for (int b : adjacency.get(a).keySet()) {
                if (a < b && !sameFloor(this.nodes.get(a).z(), this.nodes.get(b).z())) {
                    stairEdges.add(new int[]{a, b});
                }
            }
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

        // Multiplanta: si el agente ya está SOBRE una escalera (z entre plantas),
        // su hop es el extremo de la escalera hacia la planta del target. Así sigue
        // subiendo/bajando el tramo en vez de quedar sin nodo visible.
        if (!isOnFloor(agentPosition.z())) {
            Vec3 onStair = stairTraversalHop(agentPosition, target);
            if (onStair != null) {
                return new Query(onStair, false, -1, -1, List.of(), -1, -1);
            }
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
            Vec3 cur = path.get(i);
            Vec3 nxt = path.get(i + 1);
            // Arista de escalera en el camino (cur=pie, nxt=tope en otra planta):
            // mientras el agente no llegó al pie, el hop es el pie; una vez cerca,
            // el hop pasa al tope para que el agente suba el tramo (la z la interpola
            // el OM al estar sobre la escalera, paso 6).
            if (!sameFloor(cur.z(), nxt.z())) {
                // Cambiar el hop al otro extremo SÓLO cuando el agente llegó al
                // extremo cercano (cur) MEDIDO A LO LARGO DEL EJE del tramo — no la
                // distancia euclídea. Es la misma proyección que usa Stairs.zAt, así
                // que al cambiar el hop zAt(agente) ≈ zAt(cur) = z de su planta y la
                // z engancha desde el nivel del piso, sin el salto 0→0.56 (D21). El
                // desvío perpendicular se ignora a propósito: el agente puede llegar
                // al extremo con offset lateral y aun así enganchar suave.
                // D24 (gate lateral): además de estar en la línea de la boca, el
                // agente debe estar DE FRENTE al tramo (desvío perpendicular ≤
                // semiancho). Sin esto, en una multitud los agentes que cruzaban la
                // línea desplazados lateralmente recibían el hop del extremo lejano
                // y quedaban clavados contra las barandas desde afuera, taponando
                // la boca (arco del stress-test E300–E800). Fuera del ancho, el hop
                // sigue siendo el extremo cercano: el agente apunta al centro de la
                // boca y entra bordeando la punta de la baranda.
                double lateralTol = lateralTolFor(cur, nxt);
                if (alongAxisDistFromNear(agent.xy(), cur.xy(), nxt.xy()) <= STAIR_FOOT_REACH
                        && perpDistFromAxis(agent.xy(), cur.xy(), nxt.xy()) <= lateralTol) {
                    return new FvpOnPath(nxt, i + 1, i + 1);
                }
                // D24 (boca ancha): mientras el agente se acerca, el hop no es el
                // nodo puntual del extremo sino SU proyección sobre el segmento de
                // la boca (perpendicular al eje, largo = ancho del tramo). Con un
                // punto único, una multitud convergía isotrópicamente y formaba un
                // arco estable (deadlock del stress-test); con la boca ancha cada
                // agente apunta a su propio punto de entrada, como en una puerta.
                return new FvpOnPath(mouthPoint(agent, cur, nxt, lateralTol), i, i);
            }
            if (i + 1 < graphNodeCount) {
                Vec3 hop = binarySearchFVP(agent, cur, nxt);
                return new FvpOnPath(hop, i, i + 1);
            }
            Vec3 hop = binarySearchFVP(agent, cur, nxt);
            return new FvpOnPath(hop, i, i);
        }
        return new FvpOnPath(safeFallbackHop(agent, -1), -1, -1);
    }

    /** ¿{@code z} coincide con alguna planta del grafo (no está sobre una escalera)? */
    private boolean isOnFloor(double z) {
        return floorKeys.contains(floorKey(z));
    }

    /**
     * Hop para un agente que está sobre una escalera (z entre plantas): el extremo
     * del tramo hacia la planta del {@code target}. Busca la arista de escalera cuyo
     * eje planar contiene al agente; null si no está sobre ninguna.
     */
    private Vec3 stairTraversalHop(Vec3 agent, Vec3 target) {
        for (int[] e : stairEdges) {
            Vec3 a = nodes.get(e[0]);
            Vec3 b = nodes.get(e[1]);
            if (distanceToSegmentXy(agent.xy(), a.xy(), b.xy()) > STAIR_AXIS_TOL) {
                continue;
            }
            // Guard multi-tramo (D19, switchback): con dos tramos del mismo
            // switchback cercanos entre sí (p.ej. piso0↔landing y landing↔piso1),
            // la sola distancia planar al eje no alcanza para distinguirlos si sus
            // huellas quedan próximas. Exigir además que la z del agente caiga
            // dentro del rango [min,max] de ESTA arista (± FLOOR_EPS) para no
            // matchear el tramo equivocado. Con un solo tramo no cambia nada: el
            // agente siempre está dentro del rango de su único tramo.
            double zlo = Math.min(a.z(), b.z());
            double zhi = Math.max(a.z(), b.z());
            if (agent.z() < zlo - FLOOR_EPS || agent.z() > zhi + FLOOR_EPS) {
                continue;
            }
            Vec3 lower = a.z() <= b.z() ? a : b;
            Vec3 upper = a.z() <= b.z() ? b : a;
            // Subir si el target está por encima del agente; bajar si está por debajo.
            return target.z() >= agent.z() ? upper : lower;
        }
        return null;
    }

    /**
     * Distancia de {@code p} al extremo {@code near} <b>medida a lo largo del eje</b>
     * {@code near→far} (proyección sobre el eje, recortada a ≥0). Ignora el desvío
     * perpendicular. Es la magnitud que gobierna {@link ar.edu.itba.simped.core.Stairs#zAt}
     * (que también proyecta sobre el eje): mientras esta distancia sea chica, la z
     * interpolada del tramo está cerca del nivel del extremo cercano. Si {@code p}
     * se pasó del extremo cercano hacia afuera (proyección negativa) devuelve 0
     * (enganche suave, ver D21).
     */
    private static double alongAxisDistFromNear(Vec2 p, Vec2 near, Vec2 far) {
        double dx = far.x() - near.x(), dy = far.y() - near.y();
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-12) return p.distanceTo(near);
        double t = ((p.x() - near.x()) * dx + (p.y() - near.y()) * dy) / len2;
        if (t < 0.0) t = 0.0; // se pasó del extremo cercano hacia afuera del tramo
        return t * Math.sqrt(len2);
    }

    /**
     * Punto de entrada personal a la boca del tramo: la proyección del agente sobre el
     * segmento perpendicular al eje que pasa por {@code cur}, recortada al semiancho
     * (menos un margen de hombro para no apuntar al filo de la baranda). Conserva la
     * {@code z} de {@code cur} (la boca está al nivel de la planta).
     */
    private static Vec3 mouthPoint(Vec3 agent, Vec3 cur, Vec3 nxt, double halfWidth) {
        double dx = nxt.x() - cur.x(), dy = nxt.y() - cur.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return cur;
        // Perpendicular unitaria al eje del tramo.
        double px = -dy / len, py = dx / len;
        double s = (agent.x() - cur.x()) * px + (agent.y() - cur.y()) * py;
        double margin = 0.35; // hombro: no apuntar al filo de la baranda
        double lim = Math.max(0.0, halfWidth - margin);
        if (s > lim) s = lim;
        if (s < -lim) s = -lim;
        return new Vec3(cur.x() + s * px, cur.y() + s * py, cur.z());
    }

    /** Componente PERPENDICULAR al eje {@code near→far} de la posición {@code p} (m). */
    private static double perpDistFromAxis(Vec2 p, Vec2 near, Vec2 far) {
        double dx = far.x() - near.x(), dy = far.y() - near.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return p.distanceTo(near);
        return Math.abs((p.x() - near.x()) * dy - (p.y() - near.y()) * dx) / len;
    }

    /**
     * Semiancho del tramo de escalera {@code cur↔nxt} para el gate lateral del hop.
     * Si el grafo no fue construido con los tramos (mocks/CSV), cae en
     * {@link #STAIR_AXIS_TOL} — el comportamiento histórico (sin gate efectivo).
     */
    private double lateralTolFor(Vec3 cur, Vec3 nxt) {
        for (StairSpan s : stairSpans) {
            if (s.matches(cur, nxt)) return s.halfWidth();
        }
        return STAIR_AXIS_TOL;
    }

    private static double distanceToSegmentXy(Vec2 p, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-12) return p.distanceTo(a);
        double t = ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        return p.distanceTo(new Vec2(a.x() + t * dx, a.y() + t * dy));
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
