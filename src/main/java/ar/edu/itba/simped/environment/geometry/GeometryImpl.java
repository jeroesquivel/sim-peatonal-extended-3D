package ar.edu.itba.simped.environment.geometry;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Wall;
import ar.edu.itba.simped.core.ports.Geometry;

import java.util.List;

/**
 * Implementación real del port {@link Geometry}. Record inmutable
 * construido por {@code GeometryAssembler} (Block H) a partir de
 * los 5 CSV de geometría + params.
 */
public record GeometryImpl(
        List<Wall> walls,
        List<Location> locations,
        List<Exit> exits,
        List<GeneratorZone> generatorZones,
        List<ServerZone> serverZones) implements Geometry {

    public GeometryImpl {
        if (walls == null || locations == null || exits == null
                || generatorZones == null || serverZones == null) {
            throw new IllegalArgumentException("All Geometry collections must be non-null");
        }
        walls = List.copyOf(walls);
        locations = List.copyOf(locations);
        exits = List.copyOf(exits);
        generatorZones = List.copyOf(generatorZones);
        serverZones = List.copyOf(serverZones);
    }
}
