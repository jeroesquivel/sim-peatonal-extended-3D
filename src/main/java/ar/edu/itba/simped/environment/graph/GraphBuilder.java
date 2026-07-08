package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Geometry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera un {@link NavigationGraph} automáticamente a partir de la geometría de paredes.
 * <p>
 * <b>Estrategia de nodos (Tarea 1 — reducción por grilla):</b>
 * <ol>
 *   <li>Se divide el espacio en una <b>grilla de celdas de {@code gridSpacing} (20 cm)</b> y se
 *       coloca un nodo candidato en el centro de cada celda transitable.</li>
 *   <li>Se <b>eliminan los nodos redundantes</b> hasta quedarse con un conjunto dominante conexo
 *       casi-mínimo, con un nodo de atención por servidor.</li>
 * </ol>
 * El algoritmo de reducción y el criterio de redundancia están en {@link GridNodeReducer}.
 */
public final class GraphBuilder {

    /** Tamaño de celda de la grilla de candidatos (20 cm). */
    private static final double DEFAULT_GRID_SPACING = 0.20;

    private GraphBuilder() {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    private static final double FLOOR_EPS = 1e-6;

    /** Construye el grafo 3D desde {@link Geometry} (I17). Ver {@link #fromGeometry(Geometry, double)}. */
    public static NavigationGraph fromGeometry(Geometry geometry) {
        return fromGeometry(geometry, DEFAULT_GRID_SPACING);
    }

    /**
     * Construye el grafo multiplanta (paso 4, D6): corre el generador por grilla
     * <b>una vez por planta</b> (con las paredes de esa planta) y une las plantas
     * con aristas de escalera. Por cada escalera se agregan un nodo al pie y otro
     * al tope, conectados al nodo visible más cercano de su planta, y una arista
     * entre pie y tope cuyo costo es el largo 3D del tramo inclinado.
     */
    public static NavigationGraph fromGeometry(Geometry geometry, double gridSpacing) {
        List<Vec3> nodes = new ArrayList<>();
        List<Map<Integer, Double>> adjacency = new ArrayList<>();
        List<Integer> types = new ArrayList<>();
        List<Wall> allWalls = new ArrayList<>();
        Map<Long, List<Integer>> nodesByFloor = new HashMap<>();

        for (double z : geometry.floors()) {
            List<Wall> floorWalls = new ArrayList<>();
            for (ar.edu.itba.simped.core.Wall cw : geometry.wallsOn(z)) {
                floorWalls.add(new Wall(cw.p1(), cw.p2(), cw.z()));
            }
            if (floorWalls.isEmpty()) {
                // Sin paredes no hay bounding box para la grilla: esa planta no
                // aporta malla (sus escaleras igual se agregan abajo).
                continue;
            }
            List<ServerRect> servers = serverRectsOn(geometry, z);
            // D21: se EXCLUYEN de la grilla los nodos de piso dentro de la huella de
            // cualquier escalera (en todas las plantas: la huella no es piso caminable,
            // es el tubo de la escalera). Así se quitan los nodos-basura del tubo, el
            // ruteo queda limpio y —junto con un STAIR_FOOT_REACH chico— el agente
            // entra al tramo por el pie con avance ≈0 (la z arranca en ~0, sin salto).
            GridNodeReducer.Result r =
                    GridNodeReducer.reduce(floorWalls, servers, geometry.stairs(), gridSpacing);

            int base = nodes.size();
            for (Vec2 n : r.nodes()) {
                nodes.add(n.withZ(z));
                nodesByFloor.computeIfAbsent(floorKey(z), k -> new ArrayList<>()).add(nodes.size() - 1);
            }
            for (Map<Integer, Double> m : r.adjacency()) {
                Map<Integer, Double> shifted = new HashMap<>();
                for (Map.Entry<Integer, Double> e : m.entrySet()) {
                    shifted.put(e.getKey() + base, e.getValue());
                }
                adjacency.add(shifted);
            }
            types.addAll(r.types());
            allWalls.addAll(floorWalls);
        }

        List<NavigationGraph.StairSpan> spans = new ArrayList<>();
        for (ar.edu.itba.simped.core.Stairs s : geometry.stairs()) {
            int footIdx = addNode(nodes, adjacency, types, s.foot());
            int topIdx = addNode(nodes, adjacency, types, s.top());
            nodesByFloor.computeIfAbsent(floorKey(s.foot().z()), k -> new ArrayList<>()).add(footIdx);
            nodesByFloor.computeIfAbsent(floorKey(s.top().z()), k -> new ArrayList<>()).add(topIdx);

            connectToFloor(nodes, adjacency, allWalls, nodesByFloor, footIdx);
            connectToFloor(nodes, adjacency, allWalls, nodesByFloor, topIdx);
            // Arista de escalera: costo = largo 3D del tramo inclinado.
            addEdge(adjacency, footIdx, topIdx, s.foot().distanceTo(s.top()));
            // D24: semiancho del tramo para el gate lateral del hop en la boca.
            spans.add(new NavigationGraph.StairSpan(s.foot(), s.top(), s.width() / 2.0));
        }

        return new NavigationGraph(nodes, adjacency, allWalls, types, spans);
    }

    private static long floorKey(double z) {
        return Math.round(z / FLOOR_EPS);
    }

    /** Server rects (zonas de atención) de la planta {@code z}, como obstáculos del grafo. */
    private static List<ServerRect> serverRectsOn(Geometry geometry, double z) {
        List<ServerRect> out = new ArrayList<>();
        for (ar.edu.itba.simped.core.ServerZone sz : geometry.serverZonesOn(z)) {
            ar.edu.itba.simped.core.Rectangle area = sz.area();
            out.add(new ServerRect(sz.baseName() + "_" + sz.id() + "_SERVER", area.a(), area.c()));
        }
        return out;
    }

    private static int addNode(List<Vec3> nodes, List<Map<Integer, Double>> adjacency,
                               List<Integer> types, Vec3 p) {
        nodes.add(p);
        adjacency.add(new HashMap<>());
        types.add(3); // 3 = nodo de escalera
        return nodes.size() - 1;
    }

    private static void addEdge(List<Map<Integer, Double>> adjacency, int i, int j, double w) {
        adjacency.get(i).put(j, w);
        adjacency.get(j).put(i, w);
    }

    /**
     * Conecta el nodo {@code idx} (pie o tope de una escalera) con el nodo más cercano de su
     * planta que sea visible (planar, contra las paredes de esa planta); si ninguno es visible,
     * con el más cercano. El costo es la distancia planar (mismo plano).
     */
    private static void connectToFloor(List<Vec3> nodes, List<Map<Integer, Double>> adjacency,
                                       List<Wall> allWalls, Map<Long, List<Integer>> nodesByFloor,
                                       int idx) {
        Vec3 p = nodes.get(idx);
        long fk = floorKey(p.z());
        List<Integer> floorNodes = nodesByFloor.getOrDefault(fk, List.of());
        List<Wall> floorWalls = new ArrayList<>();
        for (Wall w : allWalls) {
            if (floorKey(w.z()) == fk) floorWalls.add(w);
        }

        int bestVisible = -1;
        double bestVisibleDist = Double.MAX_VALUE;
        int bestAny = -1;
        double bestAnyDist = Double.MAX_VALUE;
        for (int other : floorNodes) {
            if (other == idx) continue;
            double d = p.xy().distanceTo(nodes.get(other).xy());
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            if (d < bestVisibleDist
                    && VisibilityUtils.isVisible(p.xy(), nodes.get(other).xy(), floorWalls)) {
                bestVisibleDist = d;
                bestVisible = other;
            }
        }
        int target = bestVisible >= 0 ? bestVisible : bestAny;
        if (target >= 0) {
            addEdge(adjacency, idx, target, bestVisible >= 0 ? bestVisibleDist : bestAnyDist);
        }
    }

    /**
     * Construye el grafo a partir de un archivo WALLS.csv (mock de Geometry).
     */
    public static NavigationGraph fromWallsCsv(String wallsCsvPath) {
        return fromWallsCsv(wallsCsvPath, null, DEFAULT_GRID_SPACING);
    }

    public static NavigationGraph fromWallsCsv(String wallsCsvPath, double gridSpacing) {
        return fromWallsCsv(wallsCsvPath, null, gridSpacing);
    }

    /**
     * Construye el grafo desde WALLS.csv y, opcionalmente, SERVERS.csv del mismo escenario.
     */
    public static NavigationGraph fromWallsCsv(String wallsCsvPath, String serversCsvPath, double gridSpacing) {
        List<Wall> walls = parseWallsCsv(wallsCsvPath);
        List<ServerRect> servers = serversCsvPath != null
            ? parseServersCsv(serversCsvPath)
            : List.of();
        return build(walls, servers, gridSpacing);
    }

    /**
     * Construye el grafo dado paredes, servidores (opcional) y tamaño de celda de la grilla.
     */
    public static NavigationGraph build(List<Wall> walls, double gridSpacing) {
        return build(walls, List.of(), gridSpacing);
    }

    public static NavigationGraph build(List<Wall> walls, List<ServerRect> servers, double gridSpacing) {
        GridNodeReducer.Result result = GridNodeReducer.reduce(walls, servers, gridSpacing);
        // Camino mono-planta (mock por CSV): los nodos 2D del reducer se elevan a
        // Vec3 en la planta de las paredes (z de la primera, 0 si no hay).
        double z = walls.isEmpty() ? 0.0 : walls.get(0).z();
        List<Vec3> nodes3 = new ArrayList<>(result.nodes().size());
        for (Vec2 n : result.nodes()) {
            nodes3.add(n.withZ(z));
        }
        return new NavigationGraph(nodes3, result.adjacency(), walls, result.types());
    }

    /** Rectángulo de zona de servidor (SERVERS.csv). */
    public record ServerRect(String name, Vec2 p1, Vec2 p2) {
        public double minX() { return Math.min(p1.x(), p2.x()); }
        public double maxX() { return Math.max(p1.x(), p2.x()); }
        public double minY() { return Math.min(p1.y(), p2.y()); }
        public double maxY() { return Math.max(p1.y(), p2.y()); }
        public Vec2 center() {
            return new Vec2((minX() + maxX()) / 2, (minY() + maxY()) / 2);
        }

        /** El servidor ocupa esta zona: no se colocan nodos de paso acá. */
        public boolean contains(Vec2 p) {
            return p.x() >= minX() && p.x() <= maxX()
                && p.y() >= minY() && p.y() <= maxY();
        }
    }

    // ------------------------------------------------------------------
    // WALLS.csv parser (mock de Geometry I17)
    // ------------------------------------------------------------------

    public static List<Wall> parseWallsCsv(String csvPath) {
        List<Wall> walls = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(csvPath))) {
            String line = br.readLine(); // header
            if (line == null) return walls;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                double x1 = Double.parseDouble(parts[0].trim());
                double y1 = Double.parseDouble(parts[1].trim());
                double z1 = Double.parseDouble(parts[2].trim());
                double x2 = Double.parseDouble(parts[3].trim());
                double y2 = Double.parseDouble(parts[4].trim());
                // parts[5] = z2 (se asume == z1 para un elemento planar)
                walls.add(new Wall(new Vec2(x1, y1), new Vec2(x2, y2), z1));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error parsing WALLS.csv: " + csvPath, e);
        }
        return walls;
    }

    /**
     * Parsea SERVERS.csv y devuelve solo bloques {@code *_SERVER} (no colas).
     */
    public static List<ServerRect> parseServersCsv(String csvPath) {
        List<ServerRect> servers = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(csvPath))) {
            String line = br.readLine();
            if (line == null) return servers;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                String name = parts[0].trim();
                if (!name.endsWith("_SERVER")) continue;
                double x1 = Double.parseDouble(parts[1].trim());
                double y1 = Double.parseDouble(parts[2].trim());
                double x2 = Double.parseDouble(parts[4].trim());
                double y2 = Double.parseDouble(parts[5].trim());
                servers.add(new ServerRect(name, new Vec2(x1, y1), new Vec2(x2, y2)));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error parsing SERVERS.csv: " + csvPath, e);
        }
        return servers;
    }
}
