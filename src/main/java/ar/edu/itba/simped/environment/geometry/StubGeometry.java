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
 * STUB — implementación real en {@link GeometryImpl}, construida por
 * {@link ar.edu.itba.simped.input.ScenarioLoaderImpl}. Este stub
 * sigue existiendo para no romper el wiring transitorio de G6 en
 * {@code StubEnvironment} (que se reemplazará cuando se implemente
 * {@code simulation/}).
 */
public final class StubGeometry implements Geometry {

    @Override
    public List<Wall> walls() {
        return List.of();
    }

    @Override
    public List<Location> locations() {
        return List.of();
    }

    @Override
    public List<Exit> exits() {
        return List.of();
    }

    @Override
    public List<GeneratorZone> generatorZones() {
        return List.of();
    }

    @Override
    public List<ServerZone> serverZones() {
        return List.of();
    }

    @Override
    public List<Stairs> stairs() {
        return List.of();
    }
}
