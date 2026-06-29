package ar.edu.itba.simped.environment;

import ar.edu.itba.simped.core.ports.Environment;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.Graph;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;
import ar.edu.itba.simped.core.ports.Server;
import ar.edu.itba.simped.environment.generator.StubPedestrianGenerator;
import ar.edu.itba.simped.environment.geometry.StubGeometry;
import ar.edu.itba.simped.environment.graph.StubGraph;
import ar.edu.itba.simped.environment.neighbors.StubNeighborsIndex;

import java.util.List;

/**
 * STUB - punto de ensamblaje de los sub-módulos del Environment.
 *
 * <p>Quién implementa este ensamblaje queda a coordinar entre los grupos
 * dueños de los sub-módulos (G9, G8, G3, G0, G5) — por defecto puede vivir
 * acá hasta que el grupo lo defina. Reemplazar.</p>
 */
public final class StubEnvironment implements Environment {

    private final Geometry geometry = new StubGeometry();
    private final StubGraph graph = new StubGraph();
    private final NeighborsIndex neighbors = new StubNeighborsIndex();
    private final PedestrianGenerator pg = new StubPedestrianGenerator();
    private final List<Server> servers = List.of();
    private final LocationOccupancy locationOccupancy = new LocationOccupancyImpl();

    @Override
    public void init() {
        // I17: malla desde escenario de ejemplo hasta que Geometry (T4) esté integrado
        graph.initFromScenarioFiles(
            "scenarios/example/WALLS.csv",
            "scenarios/example/SERVERS.csv"
        );
        // TODO: resto de la cascada init del contract §SECTION C.
    }

    @Override
    public Geometry geometry() {
        return geometry;
    }

    @Override
    public Graph graph() {
        return graph;
    }

    @Override
    public NeighborsIndex neighbors() {
        return neighbors;
    }

    @Override
    public PedestrianGenerator pedestrianGenerator() {
        return pg;
    }

    @Override
    public List<Server> servers() {
        return servers;
    }

    @Override
    public LocationOccupancy locationOccupancy() {
        return locationOccupancy;
    }
}
