package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Valida la reducción de nodos (Tarea 1) sobre varias topologías sintéticas y el escenario de
 * ejemplo, chequeando los dos invariantes del criterio:
 * <ul>
 *   <li><b>Conexión:</b> el grafo conservado es una única componente conexa.</li>
 *   <li><b>Cobertura:</b> toda celda transitable (muestreada) es visible desde ≥1 nodo.</li>
 * </ul>
 *
 * <pre>java -cp target/classes ar.edu.itba.simped.environment.graph.GraphReductionValidator</pre>
 */
public final class GraphReductionValidator {

    private static final double SPACING = 0.20;
    private static final double COVERAGE_CHECK_STEP = 0.5;
    private static final double WALL_CLEARANCE = 0.30;
    private static final double PERSONAL_SPACE = 0.50; // distancia mínima a servidores (igual que el reductor)

    private GraphReductionValidator() {
    }

    public static void main(String[] args) {
        boolean ok = true;
        ok &= check("Ejemplo (super 50x20, 4 servidores)",
            GraphBuilder.parseWallsCsv("scenarios/example/WALLS.csv"),
            GraphBuilder.parseServersCsv("scenarios/example/SERVERS.csv"));
        ok &= check("Sala única 12x8", room(0, 0, 12, 8), List.of());
        ok &= check("Dos salas con puerta", twoRoomsWithDoor(), List.of());
        ok &= check("Pasillo en L", lCorridor(), List.of());
        ok &= check("Sala con un servidor", room(0, 0, 12, 8),
            List.of(new GraphBuilder.ServerRect("S_SERVER", new Vec2(4, 7), new Vec2(8, 5))));

        System.out.println(ok ? "\nTODOS LOS INVARIANTES OK" : "\nHAY FALLAS");
        if (!ok) System.exit(1);
    }

    private static boolean check(String name, List<Wall> walls, List<GraphBuilder.ServerRect> servers) {
        NavigationGraph g = GraphBuilder.build(walls, servers, SPACING);
        // El validador es planar (una sola planta): proyectamos los nodos Vec3 a xy.
        List<Vec2> nodes = g.getNodes().stream().map(Vec3::xy).toList();
        List<Map<Integer, Double>> adj = g.getAdjacency();

        boolean connected = isConnected(nodes.size(), adj);
        int[] cov = coverage(nodes, walls, servers);
        boolean covered = cov[1] == 0;
        int crossings = countCrossings(nodes, adj);

        boolean ok = connected && covered && crossings == 0;
        System.out.printf("%-32s nodos=%-3d aristas=%-3d conexo=%-5s cobertura=%d/%d cruces=%d %s%n",
            name, nodes.size(), g.edgeCount(), connected,
            cov[0] - cov[1], cov[0], crossings, ok ? "OK" : "<-- FALLA");
        return ok;
    }

    /** Cuenta pares de aristas que se cruzan (sin contar extremos compartidos). */
    private static int countCrossings(List<Vec2> nodes, List<Map<Integer, Double>> adj) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < adj.size(); i++) {
            for (int j : adj.get(i).keySet()) if (i < j) edges.add(new int[]{ i, j });
        }
        int count = 0;
        for (int e = 0; e < edges.size(); e++) {
            for (int f = e + 1; f < edges.size(); f++) {
                int[] a = edges.get(e), b = edges.get(f);
                if (a[0] == b[0] || a[0] == b[1] || a[1] == b[0] || a[1] == b[1]) continue;
                if (VisibilityUtils.segmentsIntersect(nodes.get(a[0]), nodes.get(a[1]),
                                                      nodes.get(b[0]), nodes.get(b[1]))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isConnected(int n, List<Map<Integer, Double>> adj) {
        if (n == 0) return false;
        boolean[] seen = new boolean[n];
        Deque<Integer> q = new ArrayDeque<>();
        q.add(0);
        seen[0] = true;
        int count = 1;
        while (!q.isEmpty()) {
            for (int nb : adj.get(q.poll()).keySet()) {
                if (!seen[nb]) { seen[nb] = true; count++; q.add(nb); }
            }
        }
        return count == n;
    }

    /**
     * Cobertura sobre la <b>región navegable principal</b> (mayor componente conexa de muestras
     * libres), del mismo modo que el reductor. @return {totalMuestras, descubiertas}
     */
    private static int[] coverage(List<Vec2> nodes, List<Wall> walls,
                                  List<GraphBuilder.ServerRect> servers) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Wall w : walls) {
            minX = Math.min(minX, Math.min(w.p1().x(), w.p2().x()));
            minY = Math.min(minY, Math.min(w.p1().y(), w.p2().y()));
            maxX = Math.max(maxX, Math.max(w.p1().x(), w.p2().x()));
            maxY = Math.max(maxY, Math.max(w.p1().y(), w.p2().y()));
        }
        int gx = (int) Math.floor((maxX - minX) / COVERAGE_CHECK_STEP);
        int gy = (int) Math.floor((maxY - minY) / COVERAGE_CHECK_STEP);
        boolean[][] free = new boolean[gx][gy];
        for (int i = 0; i < gx; i++) {
            for (int j = 0; j < gy; j++) {
                Vec2 p = new Vec2(minX + (i + 0.5) * COVERAGE_CHECK_STEP, minY + (j + 0.5) * COVERAGE_CHECK_STEP);
                free[i][j] = VisibilityUtils.minDistanceToWalls(p, walls) >= WALL_CLEARANCE
                    && minDistanceToServers(p, servers) >= PERSONAL_SPACE;
            }
        }
        boolean[][] main = largestComponent(free, gx, gy);

        int total = 0, uncovered = 0;
        for (int i = 0; i < gx; i++) {
            for (int j = 0; j < gy; j++) {
                if (!main[i][j]) continue;
                Vec2 p = new Vec2(minX + (i + 0.5) * COVERAGE_CHECK_STEP, minY + (j + 0.5) * COVERAGE_CHECK_STEP);
                total++;
                boolean visible = false;
                for (Vec2 node : nodes) {
                    if (visibleThrough(p, node, walls, servers)) { visible = true; break; }
                }
                if (!visible) uncovered++;
            }
        }
        return new int[]{ total, uncovered };
    }

    private static boolean[][] largestComponent(boolean[][] free, int gx, int gy) {
        int[][] comp = new int[gx][gy];
        for (int[] row : comp) java.util.Arrays.fill(row, -1);
        int bestId = -1, bestSize = 0, id = 0;
        for (int si = 0; si < gx; si++) {
            for (int sj = 0; sj < gy; sj++) {
                if (!free[si][sj] || comp[si][sj] != -1) continue;
                int size = 0;
                Deque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{ si, sj });
                comp[si][sj] = id;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    size++;
                    int[][] nb = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
                    for (int[] n : nb) {
                        if (n[0] < 0 || n[0] >= gx || n[1] < 0 || n[1] >= gy) continue;
                        if (free[n[0]][n[1]] && comp[n[0]][n[1]] == -1) {
                            comp[n[0]][n[1]] = id;
                            q.add(n);
                        }
                    }
                }
                if (size > bestSize) { bestSize = size; bestId = id; }
                id++;
            }
        }
        boolean[][] main = new boolean[gx][gy];
        for (int i = 0; i < gx; i++) {
            for (int j = 0; j < gy; j++) main[i][j] = comp[i][j] == bestId;
        }
        return main;
    }

    private static boolean insideAnyServer(Vec2 p, List<GraphBuilder.ServerRect> servers) {
        for (GraphBuilder.ServerRect s : servers) if (s.contains(p)) return true;
        return false;
    }

    private static double minDistanceToServers(Vec2 p, List<GraphBuilder.ServerRect> servers) {
        if (servers.isEmpty()) return Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        for (GraphBuilder.ServerRect s : servers) {
            double dx = Math.max(Math.max(s.minX() - p.x(), p.x() - s.maxX()), 0.0);
            double dy = Math.max(Math.max(s.minY() - p.y(), p.y() - s.maxY()), 0.0);
            min = Math.min(min, Math.sqrt(dx * dx + dy * dy));
        }
        return min;
    }

    /**
     * Visibilidad para el chequeo de cobertura: solo paredes ocluyen. Los servidores NO ocluyen la
     * visión (se ve por encima de ellos), igual que el reductor ({@code GridNodeReducer.seesOver}) y
     * el runtime ({@code NavigationGraph}). Los {@code servers} se mantienen en la firma porque las
     * celdas de muestreo igualmente se excluyen por cercanía a servidores en {@link #coverage}.
     */
    private static boolean visibleThrough(Vec2 a, Vec2 b, List<Wall> walls,
                                          List<GraphBuilder.ServerRect> servers) {
        return VisibilityUtils.isVisible(a, b, walls);
    }

    // ------------------------------------------------------------------
    // Topologías sintéticas
    // ------------------------------------------------------------------

    private static List<Wall> room(double x0, double y0, double x1, double y1) {
        List<Wall> w = new ArrayList<>();
        w.add(new Wall(new Vec2(x0, y0), new Vec2(x1, y0)));
        w.add(new Wall(new Vec2(x1, y0), new Vec2(x1, y1)));
        w.add(new Wall(new Vec2(x1, y1), new Vec2(x0, y1)));
        w.add(new Wall(new Vec2(x0, y1), new Vec2(x0, y0)));
        return w;
    }

    /** Dos salas 8x8 lado a lado separadas por un muro central con una puerta. */
    private static List<Wall> twoRoomsWithDoor() {
        List<Wall> w = room(0, 0, 16, 8);
        // muro divisorio en x=8 con puerta entre y=3.5 e y=4.5
        w.add(new Wall(new Vec2(8, 0), new Vec2(8, 3.5)));
        w.add(new Wall(new Vec2(8, 4.5), new Vec2(8, 8)));
        return w;
    }

    /** Pasillo en L. */
    private static List<Wall> lCorridor() {
        List<Wall> w = new ArrayList<>();
        // contorno exterior en L (ancho 3 m)
        double[][] outline = {
            {0, 0}, {12, 0}, {12, 12}, {9, 12}, {9, 3}, {0, 3}, {0, 0}
        };
        for (int i = 0; i + 1 < outline.length; i++) {
            w.add(new Wall(new Vec2(outline[i][0], outline[i][1]),
                           new Vec2(outline[i + 1][0], outline[i + 1][1])));
        }
        return w;
    }
}
