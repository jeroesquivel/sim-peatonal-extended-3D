package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Geometry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * I17 vía {@link Geometry} (grupo T4). Pendiente: ver {@code GEOMETRY_INTEGRATION.md}.
     */
    public static NavigationGraph fromGeometry(Geometry geometry) {
        throw new UnsupportedOperationException(
            "Geometry no expone aún paredes ni servidores. "
            + "Usar StubGraph.fromScenarioFiles() o ver GEOMETRY_INTEGRATION.md en environment.graph");
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
        return new NavigationGraph(result.nodes(), result.adjacency(), walls, result.types());
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
                // parts[2] = z1 (ignored)
                double x2 = Double.parseDouble(parts[3].trim());
                double y2 = Double.parseDouble(parts[4].trim());
                // parts[5] = z2 (ignored)
                walls.add(new Wall(new Vec2(x1, y1), new Vec2(x2, y2)));
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
