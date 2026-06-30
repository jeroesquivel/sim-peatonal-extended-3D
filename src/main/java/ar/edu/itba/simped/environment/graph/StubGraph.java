package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.Graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Implementación del sub-módulo 5.2 {@link Graph} (grupo navegación).
 * <p>
 * Punto de entrada del módulo: construye la malla una vez al inicio y responde
 * {@link #nextVisibleHop} con A* + Furthest Visible Point.
 * </p>
 *
 * <h2>Inicialización hoy (mock de Geometry)</h2>
 * <pre>
 *   StubGraph graph = StubGraph.fromScenarioFiles(
 *       "scenarios/example/WALLS.csv",
 *       "scenarios/example/SERVERS.csv");
 * </pre>
 *
 * <h2>Inicialización futura (I17 desde Geometry, grupo T4)</h2>
 * Cuando {@link Geometry} exponga paredes y posiciones de servidores, usar
 * {@link #fromGeometry(Geometry)} — ver {@code GEOMETRY_INTEGRATION.md} en este paquete.
 */
public final class StubGraph implements Graph {

    /** Salidas del visualizador y del {@code main} de prueba (subcarpeta de este paquete). */
    public static final Path OUTPUT_DIR = Path.of(
        "src", "main", "java", "ar", "edu", "itba", "simped",
        "environment", "graph", "output"
    );

    /** Tamaño de celda de la grilla de candidatos (20 cm) — ver {@link GridNodeReducer}. */
    private static final double DEFAULT_GRID_SPACING = 0.20;

    private NavigationGraph mesh;

    /** Sin malla cargada: {@link #nextVisibleHop} devuelve el target (fallback). */
    public StubGraph() {
        this.mesh = null;
    }

    private StubGraph(NavigationGraph mesh) {
        this.mesh = mesh;
    }

    /**
     * Mock de I17: lee {@code WALLS.csv} y {@code SERVERS.csv} hasta que Geometry esté listo.
     */
    public static StubGraph fromScenarioFiles(String wallsCsvPath, String serversCsvPath) {
        NavigationGraph built = GraphBuilder.fromWallsCsv(
            wallsCsvPath,
            serversCsvPath,
            DEFAULT_GRID_SPACING
        );
        return new StubGraph(built);
    }

    /**
     * Inicialización en dos fases (p. ej. desde {@code Environment.init()}).
     */
    public void initFromScenarioFiles(String wallsCsvPath, String serversCsvPath) {
        this.mesh = GraphBuilder.fromWallsCsv(wallsCsvPath, serversCsvPath, DEFAULT_GRID_SPACING);
    }

    /**
     * I17 vía Geometry (cuando T4 defina la interfaz). Hoy lanza {@link UnsupportedOperationException}.
     *
     * @see GraphBuilder#fromGeometry(Geometry)
     */
    public static StubGraph fromGeometry(Geometry geometry) {
        return new StubGraph(GraphBuilder.fromGeometry(geometry));
    }

    public void initFromGeometry(Geometry geometry) {
        this.mesh = GraphBuilder.fromGeometry(geometry);
    }

    /** Exporta nodos y aristas a {@link #OUTPUT_DIR} (o rutas indicadas). */
    public void exportToOutputDir() {
        try {
            Files.createDirectories(OUTPUT_DIR);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear " + OUTPUT_DIR, e);
        }
        exportCsv(
            OUTPUT_DIR.resolve("graph_nodes.csv").toString(),
            OUTPUT_DIR.resolve("graph_edges.csv").toString()
        );
    }

    public void exportCsv(String nodesPath, String edgesPath) {
        requireMesh().exportCsv(nodesPath, edgesPath);
    }

    public int nodeCount() {
        return requireMesh().nodeCount();
    }

    public int edgeCount() {
        return requireMesh().edgeCount();
    }

    @Override
    public Vec3 nextVisibleHop(Vec3 agentPosition, Vec3 target) {
        // Fase A del paso 4: el grafo interno sigue siendo de una planta (2D).
        // Proyectamos a xy, consultamos, y devolvemos el hop en la planta del
        // agente. La Fase B reemplaza esto por el grafo 3D por planta.
        Vec2 hop = queryNextVisibleHop(agentPosition.xy(), target.xy()).hop();
        return hop.withZ(agentPosition.z());
    }

    /** Consulta con detalle de A* y segmento FVP (tests / visualización). */
    public HopQueryResult queryNextVisibleHop(Vec2 agentPosition, Vec2 target) {
        if (mesh == null) {
            return new HopQueryResult(target, true, -1, -1, List.of(), -1, -1);
        }
        return mesh.queryNextVisibleHop(agentPosition, target);
    }

    private NavigationGraph requireMesh() {
        if (mesh == null) {
            throw new IllegalStateException(
                "Graph no inicializado. Llamar initFromScenarioFiles() o fromScenarioFiles().");
        }
        return mesh;
    }

    /**
     * Construye el grafo, exporta CSV a {@link #OUTPUT_DIR} y prueba un hop.
     *
     * <pre>
     *   java -cp target/classes ar.edu.itba.simped.environment.graph.StubGraph
     *   python src/main/java/ar/edu/itba/simped/environment/graph/test-scripts/visualize_graph.py
     * </pre>
     */
    public static void main(String[] args) throws IOException {
        String wallsPath = args.length > 0 ? args[0] : "scenarios/example/WALLS.csv";
        String serversPath = args.length > 1 ? args[1] : "scenarios/example/SERVERS.csv";

        System.out.println("Building graph (StubGraph) from: " + wallsPath);
        StubGraph graph = fromScenarioFiles(wallsPath, serversPath);

        System.out.println("Nodes: " + graph.nodeCount());
        System.out.println("Edges: " + graph.edgeCount());

        graph.exportToOutputDir();
        System.out.println("Exported to: " + OUTPUT_DIR);

        Vec2 from = new Vec2(25.0, 1.0);
        Vec2 to = new Vec2(25.0, 19.0);
        Vec2 hop = graph.queryNextVisibleHop(from, to).hop();
        System.out.println("Hop from " + from + " toward " + to + ": " + hop);
    }
}
