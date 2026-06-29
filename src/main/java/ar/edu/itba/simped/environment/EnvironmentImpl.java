package ar.edu.itba.simped.environment;

import ar.edu.itba.simped.core.ports.Environment;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.Graph;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;
import ar.edu.itba.simped.core.ports.Server;

import java.util.List;
import java.util.Objects;

/**
 * Punto de ensamblaje del Environment (módulo 5). Recibe los sub-módulos
 * pre-construidos por el ScenarioLoader y los expone al SimulationLoop.
 *
 * <p>{@link #init()} dispara la cascada de I4 descrita en SECTION C del
 * contract: por ahora delega en cada sub-módulo (los stubs son no-op).</p>
 */
public final class EnvironmentImpl implements Environment {

    private final Geometry geometry;
    private final Graph graph;
    private final NeighborsIndex neighbors;
    private final PedestrianGenerator pedestrianGenerator;
    private final List<Server> servers;
    private final LocationOccupancy locationOccupancy;

    public EnvironmentImpl(
            Geometry geometry,
            Graph graph,
            NeighborsIndex neighbors,
            PedestrianGenerator pedestrianGenerator,
            List<Server> servers,
            LocationOccupancy locationOccupancy
    ) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.graph = Objects.requireNonNull(graph, "graph");
        this.neighbors = Objects.requireNonNull(neighbors, "neighbors");
        this.pedestrianGenerator = Objects.requireNonNull(pedestrianGenerator, "pedestrianGenerator");
        this.servers = List.copyOf(servers);
        this.locationOccupancy = Objects.requireNonNull(locationOccupancy, "locationOccupancy");
    }

    @Override
    public void init() {
        // I4: hoy los sub-módulos son construidos completos por el loader.
        // Cuando Geometry/Graph/Servers expongan métodos init() reales se llaman acá.
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
        return pedestrianGenerator;
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
