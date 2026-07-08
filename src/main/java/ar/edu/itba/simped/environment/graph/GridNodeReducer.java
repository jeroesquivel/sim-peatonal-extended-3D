package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construcción del grafo de navegación por reducción sobre una grilla (Tarea 1 — grupo navegación).
 *
 * <h2>Idea</h2>
 * Se divide el espacio en una <b>grilla de celdas de {@code gridSpacing} (20 cm)</b> y se construye
 * el grafo en tres capas (cada nodo queda etiquetado con su {@link #TYPE_AREA tipo}):
 * <ol>
 *   <li><b>Nodos de área</b> ({@link #TYPE_AREA}): uno por región, <b>bien centrado</b> (en el
 *       máximo de holgura/{@code clearance} de la región). Se eligen por holgura descendente
 *       cubriendo todo el espacio navegable, de modo que cada región accesible queda representada
 *       por un nodo central. <b>Invariante de cobertura:</b> todo punto transitable es visible
 *       desde ≥1 nodo.</li>
 *   <li><b>Nodos conectores</b> ({@link #TYPE_CONNECTOR}): se agregan solo los necesarios para que
 *       el grafo sea <b>totalmente conexo</b> (puentes por las puertas), sin conexiones de más.</li>
 *   <li><b>Nodos de servidor</b> ({@link #TYPE_SERVER}): uno por servidor, en el pasillo frente a
 *       él, agregado al final y unido con la <b>mínima cantidad de aristas</b> (una hoja).</li>
 * </ol>
 * Las aristas son un <b>árbol de expansión mínimo</b> (sin conexiones innecesarias).
 *
 * <p>Los <b>servidores NO ocluyen la visión</b> (se ve por encima de ellos, igual que en el runtime
 * de {@link NavigationGraph}, que solo considera paredes): la cobertura usa {@link #seesOver}. Pero
 * <b>sí bloquean el paso</b> (no se camina a través de ellos), por lo que la conectividad y las
 * aristas usan {@link #walkLine}, que los trata como obstáculos. Tampoco se colocan nodos dentro ni
 * pegados a un servidor (clearance/{@link #PERSONAL_SPACE}).</p>
 */
final class GridNodeReducer {

    static final int TYPE_AREA = 0;
    static final int TYPE_CONNECTOR = 1;
    static final int TYPE_SERVER = 2;

    /** Distancia mínima admisible de un nodo a cualquier pared (≈ radio del agente). */
    private static final double WALL_CLEARANCE = 0.30;
    /** Resolución a la que se exige cobertura ("toda zona accesible"): un objetivo cada N celdas. */
    private static final double COVERAGE_SAMPLE = 0.40;
    /** Offset hacia el pasillo del nodo de atención del servidor. */
    private static final double SERVER_APPROACH_DIST = 0.50;
    /**
     * Espacio personal (m): distancia que se mantiene respecto de los obstáculos. Cada nodo de
     * puerta se ubica a esta distancia del eje del hueco, por lo que los dos nodos de una puerta
     * quedan a <b>2×PERSONAL_SPACE</b> entre sí; las aristas también respetan esta distancia a
     * paredes/servidores cuando es posible. Ajustable.
     */
    private static final double PERSONAL_SPACE = 0.50;
    /**
     * Distancia de cada nodo de puerta al eje del hueco; los dos nodos de una puerta quedan a
     * <b>2×DOOR_OFFSET</b> entre sí. Independiente del espacio personal. Ajustable.
     */
    private static final double DOOR_OFFSET = 1.0;
    /** Ancho máximo de un hueco para considerarlo "puerta". */
    private static final double DOOR_GAP_MAX = 8.0;
    private static final double EPS = 1e-9;

    private final List<Wall> walls;
    private final List<GraphBuilder.ServerRect> servers;
    /**
     * Huellas de escalera cuyos nodos de piso se EXCLUYEN (D21): no se colocan nodos
     * de piso a ras del suelo dentro del tubo de una escalera (ahí "se está en la
     * escalera", no en el piso). Quita los nodos-basura del tubo y limpia el ruteo,
     * que era la causa del atasco en el descanso (turnback). Se aplica en todas las
     * plantas (la huella no es piso caminable en ninguna).
     */
    private final List<ar.edu.itba.simped.core.Stairs> stairsExclude;
    private final double spacing;

    private final double minX, minY;
    private final int nx, ny;

    private final boolean[] free;       // celda transitable
    private final double[] clearance;   // distancia al obstáculo más cercano (pared o servidor)
    /**
     * Componente conexa (4-vecindad de celdas libres) de cada celda; -1 si no es libre.
     * D24: dos puntos "se ven" a través de un hueco pero pueden no compartir piso
     * caminable (p. ej. los dos descansos de escaleras distintas a la misma z, con el
     * vacío entre medio). Cobertura, aristas y puentes exigen misma componente.
     */
    private int[] gridComp;
    /** z de la planta que se está mallando (todas las paredes comparten z). */
    private final double floorZ;

    private int[] targetCells;
    private long[][] covers;            // covers[g] = bitset de targets visibles desde g
    private int numTargets;
    private int words;

    private final double[] wMinX, wMinY, wMaxX, wMaxY; // bounding box por pared

    private GridNodeReducer(List<Wall> walls, List<GraphBuilder.ServerRect> servers,
                            List<ar.edu.itba.simped.core.Stairs> stairsExclude, double spacing) {
        this.walls = walls;
        this.servers = servers;
        this.stairsExclude = stairsExclude;
        this.spacing = spacing;
        this.floorZ = walls.isEmpty() ? Double.NaN : walls.get(0).z();

        double bMinX = Double.MAX_VALUE, bMinY = Double.MAX_VALUE;
        double bMaxX = -Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE;
        for (Wall w : walls) {
            bMinX = Math.min(bMinX, Math.min(w.p1().x(), w.p2().x()));
            bMinY = Math.min(bMinY, Math.min(w.p1().y(), w.p2().y()));
            bMaxX = Math.max(bMaxX, Math.max(w.p1().x(), w.p2().x()));
            bMaxY = Math.max(bMaxY, Math.max(w.p1().y(), w.p2().y()));
        }
        this.minX = bMinX;
        this.minY = bMinY;
        this.nx = Math.max(1, (int) Math.floor((bMaxX - bMinX) / spacing));
        this.ny = Math.max(1, (int) Math.floor((bMaxY - bMinY) / spacing));

        this.wMinX = new double[walls.size()];
        this.wMinY = new double[walls.size()];
        this.wMaxX = new double[walls.size()];
        this.wMaxY = new double[walls.size()];
        for (int k = 0; k < walls.size(); k++) {
            Wall w = walls.get(k);
            wMinX[k] = Math.min(w.p1().x(), w.p2().x());
            wMinY[k] = Math.min(w.p1().y(), w.p2().y());
            wMaxX[k] = Math.max(w.p1().x(), w.p2().x());
            wMaxY[k] = Math.max(w.p1().y(), w.p2().y());
        }

        this.free = new boolean[nx * ny];
        this.clearance = new double[nx * ny];
    }

    record Result(List<Vec2> nodes, List<Map<Integer, Double>> adjacency, List<Integer> types) {}

    static Result reduce(List<Wall> walls, List<GraphBuilder.ServerRect> servers, double spacing) {
        return reduce(walls, servers, List.of(), spacing);
    }

    static Result reduce(List<Wall> walls, List<GraphBuilder.ServerRect> servers,
                         List<ar.edu.itba.simped.core.Stairs> stairsExclude, double spacing) {
        return new GridNodeReducer(walls, servers, stairsExclude, spacing).run();
    }

    // ------------------------------------------------------------------

    private Result run() {
        buildGrid();
        keepMainFreeComponent();
        buildCoverageTargets();
        buildCoverageBitsets();

        List<Integer> selected = new ArrayList<>();
        List<Integer> types = new ArrayList<>();
        boolean[] inSelected = new boolean[nx * ny];

        // 1) Nodos de área: conjunto dominante (greedy por máxima cobertura) + centrado medial.
        greedyAreaCover(selected, types, inSelected);
        recenterMedial(selected, inSelected);

        // 2) Nodos conectores en las puertas: uno a cada lado del hueco, dentro del recinto,
        //    separado del eje por PERSONAL_SPACE (los dos quedan a 2×PERSONAL_SPACE entre sí).
        addDoorNodes(selected, types, inSelected);

        // 3) Nodos de servidor (uno por server).
        addServerNodes(selected, types, inSelected);

        // 4) Reparación de cobertura a resolución completa (20 cm): garantiza el invariante
        //    "todo punto transitable es visible desde ≥1 nodo".
        repairCoverage(selected, types, inSelected);

        // 5) Conectividad: se asegura un grafo conexo (puentes por puertas/pasillos) ANTES de fusionar,
        //    para que la fusión vea las conexiones definitivas y no conserve nodos que luego sobran.
        ensureConnected(selected, types, inSelected);

        // 6) Fusión: se elimina un nodo si al quitarlo ninguna celda pierde visibilidad (cada celda
        //    que cubría sigue cubierta por otro nodo) y el grafo sigue conexo. Los servidores no se tocan.
        coverageMerge(selected, types, inSelected);

        // 6.5) Espaciado máximo (D24): la cobertura por visibilidad deja áreas abiertas
        //     grandes (p. ej. el patio del recreo, 30×60 m) con 2–3 nodos, y el grafo de
        //     aristas resultante distorsiona las distancias en decenas de metros (el A*
        //     elegía escalera por esos artefactos). Se agregan nodos hasta que ninguna
        //     celda libre quede a más de MAX_NODE_SPACING (distancia de grilla) de un nodo.
        //     Va DESPUÉS de la fusión (la fusión los eliminaría: no aportan cobertura).
        enforceNodeSpacing(selected, types, inSelected);

        // 7) Aristas: árbol planar (sin cruces, respetando el espacio personal a obstáculos).
        return assemblePlanar(selected, types);
    }

    // ------------------------------------------------------------------
    // Grilla y geometría
    // ------------------------------------------------------------------

    private int idx(int i, int j) { return i * ny + j; }
    private int gi(int g) { return g / ny; }
    private int gj(int g) { return g % ny; }

    private Vec2 pos(int g) {
        int i = gi(g), j = gj(g);
        return new Vec2(minX + (i + 0.5) * spacing, minY + (j + 0.5) * spacing);
    }

    private void buildGrid() {
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                Vec2 c = new Vec2(minX + (i + 0.5) * spacing, minY + (j + 0.5) * spacing);
                double dWall = VisibilityUtils.minDistanceToWalls(c, walls);
                double dServer = minDistanceToServers(c);
                int g = idx(i, j);
                clearance[g] = Math.min(dWall, dServer);
                // El espacio personal también aplica a los servidores: ningún nodo a menos de
                // PERSONAL_SPACE de un servidor (además del margen de pared para caminar).
                free[g] = dWall >= WALL_CLEARANCE && dServer >= PERSONAL_SPACE
                        && !insideAnyStairFootprint(c);
            }
        }
    }

    /**
     * Filtra las regiones transitables conexas (celdas libres unidas por 4-vecindad).
     *
     * <p>Si alguna región está <b>anclada por una escalera</b> (contiene el pie o el tope de un
     * tramo de esta planta), se conservan exactamente las regiones ancladas: son piso real que
     * el ruteo multiplanta necesita (p. ej. los descansos de un switchback a z intermedia). El
     * resto — en particular la franja "fantasma" que aparece entre dos descansos porque el
     * bounding box de la planta los abarca a ambos — se descarta (D24: antes esa franja era la
     * región más grande de z=1.5, se quedaba con la malla, y los descansos reales quedaban sin
     * nodos; los extremos de escalera se encadenaban entonces por el vacío y el A* embudaba
     * toda la planta alta por una sola escalera).</p>
     *
     * <p>Sin anclas (plantas comunes o escenarios sin escaleras) se conserva la mayor región,
     * como siempre: descarta "bolsillos" exteriores de plantas cóncavas. Deja {@link #gridComp}
     * con el id de región de cada celda superviviente.</p>
     */
    private void keepMainFreeComponent() {
        int[] comp = new int[nx * ny];
        java.util.Arrays.fill(comp, -1);
        List<Integer> sizes = new ArrayList<>();
        for (int start = 0; start < nx * ny; start++) {
            if (!free[start] || comp[start] != -1) continue;
            int id = sizes.size();
            int size = 0;
            Deque<Integer> q = new ArrayDeque<>();
            q.add(start);
            comp[start] = id;
            while (!q.isEmpty()) {
                int g = q.poll();
                size++;
                int i = gi(g), j = gj(g);
                int[][] nb = {{i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1}};
                for (int[] c : nb) {
                    if (c[0] < 0 || c[0] >= nx || c[1] < 0 || c[1] >= ny) continue;
                    int h = idx(c[0], c[1]);
                    if (free[h] && comp[h] == -1) { comp[h] = id; q.add(h); }
                }
            }
            sizes.add(size);
        }
        this.gridComp = comp;
        if (sizes.isEmpty()) return;

        Set<Integer> keep = anchoredComponents(comp);
        if (keep.isEmpty()) {
            int bestId = 0;
            for (int id = 1; id < sizes.size(); id++) {
                if (sizes.get(id) > sizes.get(bestId)) bestId = id;
            }
            keep = Set.of(bestId);
        }
        for (int g = 0; g < nx * ny; g++) {
            if (free[g] && !keep.contains(comp[g])) {
                free[g] = false;
                comp[g] = -1;
            }
        }
    }

    /** Radio de búsqueda de la celda libre más cercana a un extremo de escalera (ancla). */
    private static final double STAIR_ANCHOR_RADIUS = 1.0;

    /**
     * D24: distancia de grilla máxima admisible de una celda libre al nodo más cercano.
     * Acota la distorsión métrica del grafo en áreas abiertas grandes (la cobertura por
     * visibilidad sola puede dejar 2–3 nodos para un patio de 30×60 m).
     */
    private static final double MAX_NODE_SPACING = 12.0;

    /**
     * Agrega nodos de área hasta que ninguna celda libre quede a más de
     * {@link #MAX_NODE_SPACING} (distancia de grilla, multi-source BFS desde los nodos)
     * del nodo más cercano. Cada iteración agrega la celda más lejana y repite.
     */
    private void enforceNodeSpacing(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        int guard = 0;
        while (guard++ < 128) {
            double[] dist = new double[nx * ny];
            java.util.Arrays.fill(dist, Double.MAX_VALUE);
            Deque<Integer> q = new ArrayDeque<>();
            for (int g : selected) {
                dist[g] = 0.0;
                q.add(g);
            }
            while (!q.isEmpty()) {
                int g = q.poll();
                int i = gi(g), j = gj(g);
                int[][] nb = {{i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1}};
                for (int[] c : nb) {
                    if (c[0] < 0 || c[0] >= nx || c[1] < 0 || c[1] >= ny) continue;
                    int h = idx(c[0], c[1]);
                    if (!free[h] || dist[h] != Double.MAX_VALUE) continue;
                    dist[h] = dist[g] + spacing;
                    q.add(h);
                }
            }
            // Celda más lejana que además tenga línea de vista a algún nodo existente
            // (sin eso quedaría AISLADA en el grafo de aristas — p. ej. un bolsillo
            // detrás de las aulas alcanzable por una rendija — y un nodo aislado puede
            // capturar la consulta de "nodo más cercano" de un agente y dejarlo sin
            // camino). Las celdas sin enlace posible se saltean.
            int far = -1;
            double farD = MAX_NODE_SPACING;
            for (int g = 0; g < nx * ny; g++) {
                if (!free[g] || dist[g] == Double.MAX_VALUE || dist[g] <= farD) continue;
                boolean linkable = false;
                for (int s : selected) {
                    if (walkLine(pos(g), pos(s))) { linkable = true; break; }
                }
                if (linkable) { farD = dist[g]; far = g; }
            }
            if (far < 0) return;
            selected.add(far);
            types.add(TYPE_AREA);
            inSelected[far] = true;
        }
    }

    /**
     * Ids de las regiones que contienen una celda libre a menos de {@link #STAIR_ANCHOR_RADIUS}
     * del pie o el tope de un tramo de escalera de ESTA planta (misma z). Vacío si la planta no
     * tiene extremos de escalera.
     */
    private Set<Integer> anchoredComponents(int[] comp) {
        Set<Integer> out = new HashSet<>();
        int r = Math.max(1, (int) Math.ceil(STAIR_ANCHOR_RADIUS / spacing));
        for (ar.edu.itba.simped.core.Stairs s : stairsExclude) {
            for (Vec3 e : new Vec3[]{s.foot(), s.top()}) {
                if (Math.abs(e.z() - floorZ) > 1e-6) continue;
                int ei = (int) Math.floor((e.x() - minX) / spacing);
                int ej = (int) Math.floor((e.y() - minY) / spacing);
                int bestG = -1;
                double bestD = Double.MAX_VALUE;
                for (int i = Math.max(0, ei - r); i <= Math.min(nx - 1, ei + r); i++) {
                    for (int j = Math.max(0, ej - r); j <= Math.min(ny - 1, ej + r); j++) {
                        int g = idx(i, j);
                        if (!free[g]) continue;
                        double d = pos(g).distanceTo(new Vec2(e.x(), e.y()));
                        if (d <= STAIR_ANCHOR_RADIUS && d < bestD) { bestD = d; bestG = g; }
                    }
                }
                if (bestG >= 0) out.add(comp[bestG]);
            }
        }
        return out;
    }

    /** ¿Las celdas que contienen a {@code a} y {@code b} comparten región transitable? */
    private boolean sameComp(Vec2 a, Vec2 b) {
        return compOf(a) >= 0 && compOf(a) == compOf(b);
    }

    private int compOf(Vec2 p) {
        int i = Math.min(nx - 1, Math.max(0, (int) Math.floor((p.x() - minX) / spacing)));
        int j = Math.min(ny - 1, Math.max(0, (int) Math.floor((p.y() - minY) / spacing)));
        return gridComp[idx(i, j)];
    }

    /** Ídem {@link #sameComp(Vec2, Vec2)} pero por id de celda. */
    private boolean sameCompCells(int g, int h) {
        return gridComp[g] >= 0 && gridComp[g] == gridComp[h];
    }

    /**
     * ¿El punto cae dentro de la huella de alguna escalera? (D21) Los nodos de piso
     * a ras del suelo dentro de una huella no tienen sentido; excluyéndolos, el A*
     * rutea al pie/tope por AFUERA de la huella (boca) y el agente entra con avance
     * ≈0 (z sin salto). Aplica en TODAS las plantas (una huella no es piso caminable
     * en ninguna: es el tubo de la escalera).
     */
    private boolean insideAnyStairFootprint(Vec2 p) {
        for (ar.edu.itba.simped.core.Stairs s : stairsExclude) {
            if (s.containsXy(p.x(), p.y())) return true;
        }
        return false;
    }

    /** Distancia (≥0) de un punto al rectángulo de servidor más cercano; 0 si está dentro. */
    private double minDistanceToServers(Vec2 p) {
        if (servers.isEmpty()) return Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        for (GraphBuilder.ServerRect s : servers) {
            double dx = Math.max(Math.max(s.minX() - p.x(), p.x() - s.maxX()), 0.0);
            double dy = Math.max(Math.max(s.minY() - p.y(), p.y() - s.maxY()), 0.0);
            min = Math.min(min, Math.sqrt(dx * dx + dy * dy));
        }
        return min;
    }

    // ------------------------------------------------------------------
    // Cobertura
    // ------------------------------------------------------------------

    private void buildCoverageTargets() {
        int stride = Math.max(1, (int) Math.round(COVERAGE_SAMPLE / spacing));
        List<Integer> tg = new ArrayList<>();
        for (int i = 0; i < nx; i += stride) {
            for (int j = 0; j < ny; j += stride) {
                int g = idx(i, j);
                if (free[g]) tg.add(g);
            }
        }
        numTargets = tg.size();
        words = (numTargets + 63) >>> 6;
        targetCells = new int[numTargets];
        for (int t = 0; t < numTargets; t++) targetCells[t] = tg.get(t);
    }

    private void buildCoverageBitsets() {
        covers = new long[nx * ny][];
        for (int g = 0; g < nx * ny; g++) {
            if (!free[g]) continue;
            long[] bits = new long[words];
            Vec2 pg = pos(g);
            for (int t = 0; t < numTargets; t++) {
                int tg = targetCells[t];
                // D24: un nodo solo "cubre" celdas de su propia región transitable
                // (visible a través del vacío no es cubierto: sin esto, el nodo de un
                // descanso cubría el descanso de la OTRA escalera y lo dejaba sin nodos).
                if (tg == g || (sameCompCells(g, tg) && seesOver(pg, pos(tg)))) {
                    bits[t >>> 6] |= 1L << (t & 63);
                }
            }
            covers[g] = bits;
        }
    }

    // ------------------------------------------------------------------
    // 1) Nodos de área (cobertura por holgura descendente)
    // ------------------------------------------------------------------

    private void greedyAreaCover(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        long[] covered = new long[words];
        int remaining = numTargets;
        while (remaining > 0) {
            int best = -1, bestGain = 0;
            double bestClear = -1;
            for (int g = 0; g < nx * ny; g++) {
                if (!free[g] || inSelected[g]) continue;
                int gain = gainAgainst(covers[g], covered);
                if (gain > bestGain || (gain == bestGain && gain > 0 && clearance[g] > bestClear)) {
                    bestGain = gain;
                    bestClear = clearance[g];
                    best = g;
                }
            }
            if (best < 0 || bestGain == 0) break;
            selected.add(best);
            types.add(TYPE_AREA);
            inSelected[best] = true;
            orInto(covered, covers[best]);
            remaining -= bestGain;
        }
    }

    /**
     * Garantiza cobertura total: si alguna celda transitable (resolución 20 cm) no es visible desde
     * ningún nodo en las posiciones finales, agrega nodos de área (los más centrales primero) hasta
     * cubrirla. El residuo suele ser mínimo (esquirlas junto a servidores), así que no explota.
     */
    private void repairCoverage(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        boolean[] coveredCell = new boolean[nx * ny];
        for (int g = 0; g < nx * ny; g++) {
            if (free[g]) coveredCell[g] = visibleFromAny(g, selected);
        }
        List<Integer> uncovered = new ArrayList<>();
        for (int g = 0; g < nx * ny; g++) if (free[g] && !coveredCell[g]) uncovered.add(g);
        uncovered.sort((a, b) -> Double.compare(clearance[b], clearance[a]));

        for (int g : uncovered) {
            if (coveredCell[g]) continue;
            selected.add(g);
            types.add(TYPE_AREA);
            inSelected[g] = true;
            Vec2 pg = pos(g);
            for (int h = 0; h < nx * ny; h++) {
                if (free[h] && !coveredCell[h] && (h == g || seesOver(pg, pos(h)))) coveredCell[h] = true;
            }
        }
    }

    private boolean visibleFromAny(int g, List<Integer> selected) {
        Vec2 p = pos(g);
        for (int s : selected) {
            if (s == g || seesOver(p, pos(s))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 2) Nodos conectores en las puertas
    // ------------------------------------------------------------------

    /**
     * Coloca un nodo conector a <b>cada lado</b> de cada puerta (hueco entre paredes), dentro del
     * recinto y separado del hueco por {@link #DOOR_PERSONAL_SPACE} (espacio personal). El par a
     * ambos lados es mutuamente visible a través de la puerta, lo que une las dos regiones.
     */
    private void addDoorNodes(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        for (Vec2[] door : detectDoors()) {
            Vec2 a = door[0], b = door[1];
            Vec2 center = new Vec2((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
            Vec2 along = b.sub(a).normalized();
            Vec2 normal = new Vec2(-along.y(), along.x());
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec2 p = center.add(normal.scale(sign * DOOR_OFFSET));
                int g = nearestFreeCell(p);
                if (g < 0) continue;
                // si el lado no es transitable (la puerta da al exterior), la celda libre queda lejos
                if (pos(g).distanceTo(p) > 1.5 * spacing) continue;
                if (!inSelected[g]) {
                    selected.add(g);
                    types.add(TYPE_CONNECTOR);
                    inSelected[g] = true;
                }
            }
        }
    }

    /**
     * Detecta puertas: pares de extremos de pared "libres" (aparecen una sola vez) visibles entre sí
     * y a distancia de hueco razonable. Devuelve los dos extremos {@code a, b} de cada hueco.
     */
    private List<Vec2[]> detectDoors() {
        Map<String, Vec2> tips = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Wall w : walls) {
            for (Vec2 e : new Vec2[]{ w.p1(), w.p2() }) {
                String key = Math.round(e.x() * 100) + "," + Math.round(e.y() * 100);
                counts.merge(key, 1, Integer::sum);
                tips.put(key, e);
            }
        }
        List<Vec2> ends = new ArrayList<>();
        for (var en : counts.entrySet()) if (en.getValue() == 1) ends.add(tips.get(en.getKey()));
        ends.sort((p, q) -> p.x() != q.x() ? Double.compare(p.x(), q.x()) : Double.compare(p.y(), q.y()));

        List<Vec2[]> doors = new ArrayList<>();
        boolean[] used = new boolean[ends.size()];
        for (int i = 0; i < ends.size(); i++) {
            if (used[i]) continue;
            for (int j = i + 1; j < ends.size(); j++) {
                if (used[j]) continue;
                double d = ends.get(i).distanceTo(ends.get(j));
                if (d > 0.4 && d < DOOR_GAP_MAX && gapIsOpen(ends.get(i), ends.get(j))) {
                    doors.add(new Vec2[]{ ends.get(i), ends.get(j) });
                    used[i] = true;
                    used[j] = true;
                    break;
                }
            }
        }
        return doors;
    }

    /**
     * Fusión por redundancia de cobertura: se elimina un nodo si <b>ninguna celda transitable
     * pierde visibilidad</b> al quitarlo (toda celda que veía sigue siendo vista por otro nodo) y
     * el grafo <b>sigue conexo</b>. Se evalúa a resolución completa (20 cm). Los servidores no se
     * eliminan (uno por server).
     */
    private void coverageMerge(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        int[] cover = fullCoverCount(selected); // por celda: cuántos nodos seleccionados la ven
        boolean changed = true;
        while (changed) {
            changed = false;
            int compsBefore = components(selected).size();
            for (int i = 0; i < selected.size(); i++) {
                if (types.get(i) == TYPE_SERVER) continue; // los servidores se mantienen
                int gi = selected.get(i);
                if (uniquelyCoversSome(gi, cover)) continue;     // perdería visibilidad de una celda
                List<Integer> tmp = new ArrayList<>(selected);
                tmp.remove(i);
                if (components(tmp).size() > compsBefore) continue; // desconectaría el grafo
                addCover(gi, cover, -1);
                inSelected[gi] = false;
                selected.remove(i);
                types.remove(i);
                changed = true;
                break;
            }
        }
    }

    /** Cuenta, por celda libre, cuántos nodos seleccionados la ven. */
    private int[] fullCoverCount(List<Integer> selected) {
        int[] cover = new int[nx * ny];
        for (int g : selected) addCover(g, cover, +1);
        return cover;
    }

    private void addCover(int g, int[] cover, int delta) {
        Vec2 pg = pos(g);
        for (int c = 0; c < nx * ny; c++) {
            if (free[c] && (c == g || seesOver(pg, pos(c)))) cover[c] += delta;
        }
    }

    /** ¿Hay alguna celda libre que solo vea {@code g}? (al quitarlo quedaría descubierta) */
    private boolean uniquelyCoversSome(int g, int[] cover) {
        Vec2 pg = pos(g);
        for (int c = 0; c < nx * ny; c++) {
            if (!free[c]) continue;
            if (cover[c] == 1 && (c == g || seesOver(pg, pos(c)))) return true;
        }
        return false;
    }

    /**
     * El hueco entre dos extremos está abierto si el segmento (acortado en los extremos para no
     * "tocar" las paredes que terminan ahí) no cruza ninguna pared.
     */
    private boolean gapIsOpen(Vec2 a, Vec2 b) {
        Vec2 dir = b.sub(a).normalized();
        double shrink = Math.min(0.05, a.distanceTo(b) / 4);
        Vec2 a2 = a.add(dir.scale(shrink));
        Vec2 b2 = b.sub(dir.scale(shrink));
        return VisibilityUtils.isVisible(a2, b2, walls);
    }

    // ------------------------------------------------------------------
    // 4) Nodo de atención por servidor
    // ------------------------------------------------------------------

    private void addServerNodes(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        for (GraphBuilder.ServerRect s : servers) {
            int g = serverApproachCell(s);
            if (g >= 0 && !inSelected[g]) {
                selected.add(g);
                types.add(TYPE_SERVER);
                inSelected[g] = true;
            }
        }
    }

    /**
     * Celda libre en el pasillo <b>frente</b> al servidor: se elige el lado que da al espacio libre
     * más abierto (mayor profundidad transitable) y se coloca el nodo a {@code SERVER_APPROACH_DIST}.
     */
    private int serverApproachCell(GraphBuilder.ServerRect s) {
        double cx = (s.minX() + s.maxX()) / 2;
        double cy = (s.minY() + s.maxY()) / 2;
        Vec2[] sideCenter = {
            new Vec2(cx, s.minY()), new Vec2(cx, s.maxY()),
            new Vec2(s.minX(), cy), new Vec2(s.maxX(), cy),
        };
        Vec2[] normal = { new Vec2(0, -1), new Vec2(0, 1), new Vec2(-1, 0), new Vec2(1, 0) };
        int best = -1;
        double bestDepth = -1;
        for (int k = 0; k < 4; k++) {
            double depth = freeDepth(sideCenter[k], normal[k]);
            if (depth < SERVER_APPROACH_DIST) continue;
            int g = nearestFreeCell(sideCenter[k].add(normal[k].scale(SERVER_APPROACH_DIST)));
            if (g < 0) continue;
            if (depth > bestDepth) { bestDepth = depth; best = g; }
        }
        return best >= 0 ? best : nearestFreeCell(s.center());
    }

    private double freeDepth(Vec2 from, Vec2 dir) {
        double depth = 0;
        for (double d = SERVER_APPROACH_DIST; d <= Math.max(nx, ny) * spacing; d += spacing) {
            Vec2 p = from.add(dir.scale(d));
            if (p.x() <= minX || p.y() <= minY
                || p.x() >= minX + nx * spacing || p.y() >= minY + ny * spacing) break;
            int g = idx((int) Math.floor((p.x() - minX) / spacing),
                        (int) Math.floor((p.y() - minY) / spacing));
            if (g < 0 || g >= nx * ny || !free[g]) break;
            depth = d;
        }
        return depth;
    }

    private int nearestFreeCell(Vec2 p) {
        int ci = (int) Math.floor((p.x() - minX) / spacing);
        int cj = (int) Math.floor((p.y() - minY) / spacing);
        int maxR = Math.max(nx, ny);
        for (int r = 0; r <= maxR; r++) {
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = ci - r; i <= ci + r; i++) {
                for (int j = cj - r; j <= cj + r; j++) {
                    if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != r) continue;
                    if (i < 0 || i >= nx || j < 0 || j >= ny) continue;
                    int g = idx(i, j);
                    if (!free[g]) continue;
                    double d = pos(g).distanceTo(p);
                    if (d < bestD) { bestD = d; best = g; }
                }
            }
            if (best >= 0) return best;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 2) Conectividad (nodos conectores en las puertas)
    // ------------------------------------------------------------------

    private void ensureConnected(List<Integer> selected, List<Integer> types, boolean[] inSelected) {
        int guard = 0;
        while (true) {
            List<List<Integer>> comps = components(selected);
            if (comps.size() <= 1) return;
            if (guard++ > selected.size() + 8) return;

            comps.sort((a, b) -> Integer.compare(b.size(), a.size()));
            List<Integer> path = freeGridPath(comps.get(1), comps.get(0));
            if (path == null) return; // regiones realmente desconectadas
            insertBridgeNodes(path, selected, types, inSelected);
        }
    }

    /** Componentes conexas (por visibilidad) del conjunto seleccionado. */
    private List<List<Integer>> components(List<Integer> selected) {
        int n = selected.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (walkLine(pos(selected.get(i)), pos(selected.get(j)))) union(parent, i, j);
            }
        }
        Map<Integer, List<Integer>> byRoot = new HashMap<>();
        for (int i = 0; i < n; i++) {
            byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(selected.get(i));
        }
        return new ArrayList<>(byRoot.values());
    }

    private int find(int[] p, int x) {
        while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; }
        return x;
    }

    private void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

    /** Camino BFS de celdas libres (4-vecinos) de {@code from} a {@code to}, o null. */
    private List<Integer> freeGridPath(List<Integer> from, List<Integer> to) {
        boolean[] isTarget = new boolean[nx * ny];
        for (int g : to) isTarget[g] = true;
        int[] prev = new int[nx * ny];
        java.util.Arrays.fill(prev, -2);
        Deque<Integer> q = new ArrayDeque<>();
        for (int g : from) { prev[g] = -1; q.add(g); }
        int hit = -1;
        while (!q.isEmpty()) {
            int g = q.poll();
            if (isTarget[g] && prev[g] != -1) { hit = g; break; }
            int i = gi(g), j = gj(g);
            int[][] nb = {{i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1}};
            for (int[] c : nb) {
                if (c[0] < 0 || c[0] >= nx || c[1] < 0 || c[1] >= ny) continue;
                int h = idx(c[0], c[1]);
                if (!free[h] || prev[h] != -2) continue;
                prev[h] = g;
                if (isTarget[h]) { hit = h; break; }
                q.add(h);
            }
            if (hit >= 0) break;
        }
        if (hit < 0) return null;
        List<Integer> path = new ArrayList<>();
        for (int g = hit; g != -1; g = prev[g]) path.add(g);
        java.util.Collections.reverse(path);
        return path;
    }

    /** Inserta nodos conectores a lo largo del camino, mutuamente visibles (estilo FVP). */
    private void insertBridgeNodes(List<Integer> path, List<Integer> selected,
                                   List<Integer> types, boolean[] inSelected) {
        int anchor = path.get(0);
        for (int k = 1; k < path.size(); k++) {
            if (!walkLine(pos(anchor), pos(path.get(k)))) {
                int add = path.get(k - 1);
                if (!inSelected[add]) {
                    selected.add(add);
                    types.add(TYPE_CONNECTOR);
                    inSelected[add] = true;
                }
                anchor = add;
            }
        }
        int last = path.get(path.size() - 1);
        if (!inSelected[last]) {
            selected.add(last);
            types.add(TYPE_CONNECTOR);
            inSelected[last] = true;
        }
    }

    // ------------------------------------------------------------------
    // Centrado medial
    // ------------------------------------------------------------------

    /**
     * Cada nodo asciende, de a una celda, por el gradiente local de {@code clearance} (alejándose de
     * la pared más cercana) mientras no rompa cobertura ni conexión. Ajuste local: se detiene en el
     * lomo medial, por lo que cae en el centro de la puerta o en el eje del pasillo, sin derivar.
     */
    private void recenterMedial(List<Integer> selected, boolean[] inSelected) {
        int[] coverCount = computeCoverCount(selected);
        boolean moved = true;
        int guard = 0;
        while (moved && guard++ < 2 * (nx + ny)) {
            moved = false;
            int compsBefore = components(selected).size();
            for (int k = 0; k < selected.size(); k++) {
                int g = selected.get(k);
                int to = bestMedialNeighbor(g, inSelected);
                if (to < 0) continue;
                if (!coveragePreservedOnMove(g, to, coverCount)) continue;
                // El movimiento no debe fragmentar más el grafo (aún puede no estar conectado).
                List<Integer> tmp = new ArrayList<>(selected);
                tmp.set(k, to);
                if (components(tmp).size() > compsBefore) continue;
                updateCoverCount(coverCount, covers[g], -1);
                updateCoverCount(coverCount, covers[to], +1);
                inSelected[g] = false;
                inSelected[to] = true;
                selected.set(k, to);
                moved = true;
            }
        }
    }

    private int bestMedialNeighbor(int g, boolean[] inSelected) {
        int i = gi(g), j = gj(g);
        int[][] nb = {
            {i + 1, j}, {i - 1, j}, {i, j + 1}, {i, j - 1},
            {i + 1, j + 1}, {i + 1, j - 1}, {i - 1, j + 1}, {i - 1, j - 1},
        };
        int best = -1;
        double bestClear = clearance[g];
        for (int[] c : nb) {
            if (c[0] < 0 || c[0] >= nx || c[1] < 0 || c[1] >= ny) continue;
            int m = idx(c[0], c[1]);
            if (!free[m] || inSelected[m]) continue;
            if (clearance[m] > bestClear) { bestClear = clearance[m]; best = m; }
        }
        return best;
    }

    private boolean coveragePreservedOnMove(int from, int to, int[] coverCount) {
        long[] a = covers[from], b = covers[to];
        for (int w = 0; w < words; w++) {
            long lost = a[w] & ~b[w];
            while (lost != 0) {
                int t = (w << 6) + Long.numberOfTrailingZeros(lost);
                if (coverCount[t] <= 1) return false;
                lost &= lost - 1;
            }
        }
        return true;
    }

    private int[] computeCoverCount(List<Integer> selected) {
        int[] cc = new int[numTargets];
        for (int g : selected) updateCoverCount(cc, covers[g], +1);
        return cc;
    }

    private void updateCoverCount(int[] cc, long[] bits, int delta) {
        for (int w = 0; w < words; w++) {
            long b = bits[w];
            while (b != 0) {
                int t = (w << 6) + Long.numberOfTrailingZeros(b);
                cc[t] += delta;
                b &= b - 1;
            }
        }
    }

    // ------------------------------------------------------------------
    // 6) Aristas: árbol planar (Kruskal sobre pares visibles, descartando cruces)
    // ------------------------------------------------------------------

    /**
     * Árbol de expansión sobre los pares visibles, agregando las aristas más cortas según
     * prioridades decrecientes: (1) que no crucen otra arista <b>y</b> respeten el espacio personal
     * a los obstáculos; (2) que no crucen (aunque pasen cerca de un obstáculo, p. ej. una puerta
     * angosta); (3) lo que falte para garantizar conectividad. Resultado: grafo planar que evita
     * pegarse a las paredes salvo cuando es inevitable.
     */
    private Result assemblePlanar(List<Integer> selected, List<Integer> types) {
        int n = selected.size();
        List<Vec2> nodes = new ArrayList<>(n);
        for (int g : selected) nodes.add(pos(g));

        List<double[]> cand = new ArrayList<>(); // {dist, i, j}
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (walkLine(nodes.get(i), nodes.get(j))) {
                    cand.add(new double[]{ nodes.get(i).distanceTo(nodes.get(j)), i, j });
                }
            }
        }
        cand.sort((a, b) -> Double.compare(a[0], b[0]));

        List<Map<Integer, Double>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new HashMap<>());
        List<int[]> placed = new ArrayList<>();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        int[] added = { 0 };

        // 1) sin cruce y con espacio personal a obstáculos.
        for (double[] e : cand) {
            int i = (int) e[1], j = (int) e[2];
            if (added[0] == n - 1) break;
            if (find(parent, i) == find(parent, j) || crosses(i, j, placed, nodes)) continue;
            if (segDistToObstacles(nodes.get(i), nodes.get(j)) < PERSONAL_SPACE - EPS) continue;
            addEdge(adj, parent, placed, i, j, e[0]); added[0]++;
        }
        // 2) sin cruce (permite acercarse a obstáculos: puertas angostas).
        for (double[] e : cand) {
            int i = (int) e[1], j = (int) e[2];
            if (added[0] == n - 1) break;
            if (find(parent, i) == find(parent, j) || crosses(i, j, placed, nodes)) continue;
            addEdge(adj, parent, placed, i, j, e[0]); added[0]++;
        }
        // 3) respaldo de conectividad (permite cruces si es inevitable).
        for (double[] e : cand) {
            int i = (int) e[1], j = (int) e[2];
            if (added[0] == n - 1) break;
            if (find(parent, i) == find(parent, j)) continue;
            addEdge(adj, parent, placed, i, j, e[0]); added[0]++;
        }
        // 4) Densificación (D24): el árbol de expansión deja UNA sola ruta entre
        // cada par de puntos, con costos muy distorsionados (zigzag), y el A* no
        // puede comparar alternativas — en la Escuela toda la planta alta se
        // embudaba por una única escalera y a N grande la boca se clavaba en un
        // arco. Se agregan las aristas restantes que no crucen a las ya puestas
        // y respeten el espacio personal: grafo planar (≤3n−6 aristas) con rutas
        // alternativas y distancias cercanas a las euclídeas.
        for (double[] e : cand) {
            int i = (int) e[1], j = (int) e[2];
            if (adj.get(i).containsKey(j)) continue;
            if (crosses(i, j, placed, nodes)) continue;
            if (segDistToObstacles(nodes.get(i), nodes.get(j)) < PERSONAL_SPACE - EPS) continue;
            addEdge(adj, parent, placed, i, j, e[0]);
        }
        return new Result(nodes, adj, types);
    }

    /** Distancia mínima de un segmento a cualquier obstáculo (paredes y servidores). */
    private double segDistToObstacles(Vec2 a, Vec2 b) {
        double min = Double.MAX_VALUE;
        for (Wall w : walls) min = Math.min(min, segSegDistance(a, b, w.p1(), w.p2()));
        for (GraphBuilder.ServerRect s : servers) {
            Vec2 p1 = new Vec2(s.minX(), s.minY());
            Vec2 p2 = new Vec2(s.maxX(), s.minY());
            Vec2 p3 = new Vec2(s.maxX(), s.maxY());
            Vec2 p4 = new Vec2(s.minX(), s.maxY());
            min = Math.min(min, segSegDistance(a, b, p1, p2));
            min = Math.min(min, segSegDistance(a, b, p2, p3));
            min = Math.min(min, segSegDistance(a, b, p3, p4));
            min = Math.min(min, segSegDistance(a, b, p4, p1));
        }
        return min;
    }

    private static double segSegDistance(Vec2 a, Vec2 b, Vec2 c, Vec2 d) {
        if (VisibilityUtils.segmentsIntersect(a, b, c, d)) return 0;
        return Math.min(
            Math.min(VisibilityUtils.pointToSegmentDistance(a, c, d),
                     VisibilityUtils.pointToSegmentDistance(b, c, d)),
            Math.min(VisibilityUtils.pointToSegmentDistance(c, a, b),
                     VisibilityUtils.pointToSegmentDistance(d, a, b)));
    }

    private void addEdge(List<Map<Integer, Double>> adj, int[] parent, List<int[]> placed,
                         int i, int j, double w) {
        union(parent, i, j);
        adj.get(i).put(j, w);
        adj.get(j).put(i, w);
        placed.add(new int[]{ i, j });
    }

    /** ¿La arista (i,j) cruza alguna ya puesta? (los extremos compartidos no cuentan como cruce). */
    private boolean crosses(int i, int j, List<int[]> placed, List<Vec2> nodes) {
        Vec2 a = nodes.get(i), b = nodes.get(j);
        for (int[] e : placed) {
            int u = e[0], v = e[1];
            if (u == i || u == j || v == i || v == j) continue; // comparten un nodo: no es cruce
            if (VisibilityUtils.segmentsIntersect(a, b, nodes.get(u), nodes.get(v))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Bitset + visibilidad (paredes y servidores como obstáculos)
    // ------------------------------------------------------------------

    private static void orInto(long[] dst, long[] src) {
        for (int i = 0; i < dst.length; i++) dst[i] |= src[i];
    }

    private static int gainAgainst(long[] src, long[] covered) {
        int c = 0;
        for (int i = 0; i < src.length; i++) c += Long.bitCount(src[i] & ~covered[i]);
        return c;
    }

    /**
     * Visión: el segmento no debe cruzar ninguna pared. Los <b>servidores NO ocluyen visión</b>
     * (se ve por encima de ellos), por lo que aquí no se chequean. Coincide con la visibilidad de
     * runtime ({@link NavigationGraph}, solo paredes). Se usa para la cobertura del grafo.
     */
    private boolean seesOver(Vec2 a, Vec2 b) {
        double segMinX = Math.min(a.x(), b.x()) - EPS;
        double segMaxX = Math.max(a.x(), b.x()) + EPS;
        double segMinY = Math.min(a.y(), b.y()) - EPS;
        double segMaxY = Math.max(a.y(), b.y()) + EPS;
        for (int k = 0; k < walls.size(); k++) {
            if (wMaxX[k] < segMinX || wMinX[k] > segMaxX
                || wMaxY[k] < segMinY || wMinY[k] > segMaxY) continue;
            Wall w = walls.get(k);
            if (VisibilityUtils.segmentsIntersect(a, b, w.p1(), w.p2())) return false;
        }
        return true;
    }

    /**
     * Caminabilidad en línea recta: el segmento no cruza paredes <b>ni</b> servidores. Aunque los
     * servidores no tapan la vista, no se puede caminar a través de ellos, así que la conectividad
     * y las aristas del grafo sí los tratan como obstáculos.
     */
    private boolean walkLine(Vec2 a, Vec2 b) {
        // D24: verse no alcanza — hay pares visibles a través de un hueco sin piso
        // caminable entre medio (descansos de escaleras distintas a la misma z).
        if (!sameComp(a, b)) return false;
        if (!seesOver(a, b)) return false;
        double segMinX = Math.min(a.x(), b.x()) - EPS;
        double segMaxX = Math.max(a.x(), b.x()) + EPS;
        double segMinY = Math.min(a.y(), b.y()) - EPS;
        double segMaxY = Math.max(a.y(), b.y()) + EPS;
        for (GraphBuilder.ServerRect s : servers) {
            if (s.maxX() < segMinX || s.minX() > segMaxX
                || s.maxY() < segMinY || s.minY() > segMaxY) continue;
            if (segmentIntersectsServer(a, b, s)) return false;
        }
        return true;
    }

    private static boolean segmentIntersectsServer(Vec2 a, Vec2 b, GraphBuilder.ServerRect s) {
        Vec2 p1 = new Vec2(s.minX(), s.minY());
        Vec2 p2 = new Vec2(s.maxX(), s.minY());
        Vec2 p3 = new Vec2(s.maxX(), s.maxY());
        Vec2 p4 = new Vec2(s.minX(), s.maxY());
        return VisibilityUtils.segmentsIntersect(a, b, p1, p2)
            || VisibilityUtils.segmentsIntersect(a, b, p2, p3)
            || VisibilityUtils.segmentsIntersect(a, b, p3, p4)
            || VisibilityUtils.segmentsIntersect(a, b, p4, p1);
    }

    // ------------------------------------------------------------------
    // Diagnóstico: por qué se conserva cada nodo (cobertura única / corte)
    // ------------------------------------------------------------------

    private int cellOf(Vec2 p) {
        int i = Math.max(0, Math.min(nx - 1, (int) Math.floor((p.x() - minX) / spacing)));
        int j = Math.max(0, Math.min(ny - 1, (int) Math.floor((p.y() - minY) / spacing)));
        return idx(i, j);
    }

    private int countUniqueCells(int g, int[] cover) {
        Vec2 pg = pos(g);
        int c = 0;
        for (int cell = 0; cell < nx * ny; cell++) {
            if (free[cell] && cover[cell] == 1 && (cell == g || seesOver(pg, pos(cell)))) c++;
        }
        return c;
    }

    /**
     * Reporta, para el grafo del escenario de ejemplo, por qué se conserva cada nodo: cuántas
     * celdas cubre en exclusiva ({@code unicas}) y si al quitarlo se desconectaría ({@code corte}).
     * Un nodo se conserva si {@code unicas>0} O {@code corte=si}.
     *
     * <pre>java -cp target/classes ar.edu.itba.simped.environment.graph.GridNodeReducer [x y]</pre>
     */
    public static void main(String[] args) {
        List<Wall> walls = GraphBuilder.parseWallsCsv("scenarios/example/WALLS.csv");
        List<GraphBuilder.ServerRect> servers = GraphBuilder.parseServersCsv("scenarios/example/SERVERS.csv");
        GridNodeReducer r = new GridNodeReducer(walls, servers, List.of(), 0.20);
        Result res = r.run();

        List<Integer> cells = new ArrayList<>();
        for (Vec2 p : res.nodes()) cells.add(r.cellOf(p));
        int[] cover = new int[r.nx * r.ny];
        for (int g : cells) r.addCover(g, cover, +1);

        String[] tn = { "AREA     ", "CONECTOR ", "SERVIDOR " };
        System.out.println("idx  tipo       posición              unicas  corte");
        for (int k = 0; k < cells.size(); k++) {
            int g = cells.get(k);
            int uniq = r.countUniqueCells(g, cover);
            List<Integer> tmp = new ArrayList<>(cells);
            tmp.remove(k);
            boolean corte = r.components(tmp).size() > r.components(cells).size();
            System.out.printf("%-4d %s (%5.1f,%5.1f)   %-6d  %s%n",
                k, tn[res.types().get(k)], res.nodes().get(k).x(), res.nodes().get(k).y(),
                uniq, corte ? "SI" : "no");
        }
    }
}
