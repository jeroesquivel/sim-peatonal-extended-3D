package ar.edu.itba.simped.environment.geometry;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Wall;
import ar.edu.itba.simped.core.ports.Geometry;

import java.util.List;

/**
 * Implementación real del port {@link Geometry}. Record inmutable
 * construido por {@code GeometryAssembler} (Block H) a partir de
 * los CSV de geometría + params (+ el opcional STAIRS.csv).
 */
public record GeometryImpl(
        List<Wall> walls,
        List<Location> locations,
        List<Exit> exits,
        List<GeneratorZone> generatorZones,
        List<ServerZone> serverZones,
        List<Stairs> stairs) implements Geometry {

    public GeometryImpl {
        if (walls == null || locations == null || exits == null
                || generatorZones == null || serverZones == null || stairs == null) {
            throw new IllegalArgumentException("All Geometry collections must be non-null");
        }
        walls = List.copyOf(walls);
        locations = List.copyOf(locations);
        exits = List.copyOf(exits);
        generatorZones = List.copyOf(generatorZones);
        serverZones = List.copyOf(serverZones);
        stairs = List.copyOf(stairs);
    }

    /** Geometría sin escaleras (escenario de una sola planta). */
    public GeometryImpl(
            List<Wall> walls,
            List<Location> locations,
            List<Exit> exits,
            List<GeneratorZone> generatorZones,
            List<ServerZone> serverZones) {
        this(walls, locations, exits, generatorZones, serverZones, List.of());
    }
}
