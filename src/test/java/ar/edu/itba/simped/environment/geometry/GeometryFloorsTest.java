package ar.edu.itba.simped.environment.geometry;

import ar.edu.itba.simped.core.Circle;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryFloorsTest {

    /** Geometría de dos plantas (z=0 y z=3) unidas por una escalera. */
    private GeometryImpl twoFloors() {
        List<Wall> walls = List.of(
                new Wall(new Vec2(0, 0), new Vec2(5, 0), 0.0),
                new Wall(new Vec2(5, 0), new Vec2(5, 5), 0.0),
                new Wall(new Vec2(0, 0), new Vec2(5, 0), 3.0));
        List<Location> locations = List.of(
                new Location("AULA1", new Circle(new Vec2(1, 1), 0.5), 0.0, Optional.empty()),
                new Location("AULA2", new Circle(new Vec2(1, 1), 0.5), 3.0, Optional.empty()));
        List<Exit> exits = List.of(
                new Exit("MAIN", new Segment(new Vec2(0, 0), new Vec2(1, 0)), 0.0, OptionalDouble.empty()));
        List<Stairs> stairs = List.of(
                new Stairs("S1", new Vec3(5, 1, 0.0), new Vec3(5, 4, 3.0), 2.0));
        return new GeometryImpl(walls, locations, exits, List.of(), List.of(), stairs);
    }

    @Test
    void floorsListsDistinctSortedLevels() {
        assertThat(twoFloors().floors()).containsExactly(0.0, 3.0);
    }

    @Test
    void wallsOnFiltersByFloor() {
        GeometryImpl geom = twoFloors();
        assertThat(geom.wallsOn(0.0)).hasSize(2);
        assertThat(geom.wallsOn(3.0)).hasSize(1);
        assertThat(geom.wallsOn(1.0)).isEmpty();
    }

    @Test
    void locationsAndExitsFilterByFloor() {
        GeometryImpl geom = twoFloors();
        assertThat(geom.locationsOn(0.0)).extracting(Location::blockName).containsExactly("AULA1");
        assertThat(geom.locationsOn(3.0)).extracting(Location::blockName).containsExactly("AULA2");
        assertThat(geom.exitsOn(0.0)).hasSize(1);
        assertThat(geom.exitsOn(3.0)).isEmpty();
    }

    @Test
    void stairsAtMatchesBothEndpointsFloors() {
        GeometryImpl geom = twoFloors();
        assertThat(geom.stairsAt(0.0)).hasSize(1);
        assertThat(geom.stairsAt(3.0)).hasSize(1);
        assertThat(geom.stairsAt(6.0)).isEmpty();
    }
}