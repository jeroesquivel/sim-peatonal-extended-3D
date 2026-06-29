package ar.edu.itba.simped.environment.geometry;

import ar.edu.itba.simped.core.Circle;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Segment;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Wall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryImplTest {

    @Test
    void rejectsNullCollections() {
        assertThatThrownBy(() -> new GeometryImpl(
                null, List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeometryImpl(
                List.of(), null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeometryImpl(
                List.of(), List.of(), null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeometryImpl(
                List.of(), List.of(), List.of(), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeometryImpl(
                List.of(), List.of(), List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesCollectionsAtConstruction() {
        List<Wall> walls = new ArrayList<>();
        walls.add(new Wall(new Vec2(0, 0), new Vec2(1, 0)));
        List<Location> locations = new ArrayList<>();
        locations.add(new Location("T1", new Circle(new Vec2(0, 0), 1), Optional.empty()));
        List<Exit> exits = new ArrayList<>();
        exits.add(new Exit("E1", new Segment(new Vec2(0, 0), new Vec2(2, 0)),
                java.util.OptionalDouble.empty()));

        GeometryImpl geom = new GeometryImpl(walls, locations, exits, List.of(), List.of());

        walls.add(new Wall(new Vec2(9, 9), new Vec2(9, 8)));
        locations.clear();
        exits.clear();

        assertThat(geom.walls()).hasSize(1);
        assertThat(geom.locations()).hasSize(1);
        assertThat(geom.exits()).hasSize(1);
    }

    @Test
    void exposesAllCollectionsThroughPort() {
        Wall w = new Wall(new Vec2(0, 0), new Vec2(1, 0));
        Location l = new Location("T1", new Circle(new Vec2(0, 0), 1), Optional.empty());
        Exit e = new Exit("E1", new Segment(new Vec2(0, 0), new Vec2(2, 0)),
                java.util.OptionalDouble.empty());

        GeometryImpl geom = new GeometryImpl(
                List.of(w),
                List.of(l),
                List.of(e),
                List.<GeneratorZone>of(),
                List.<ServerZone>of());

        assertThat(geom.walls()).containsExactly(w);
        assertThat(geom.locations()).containsExactly(l);
        assertThat(geom.exits()).containsExactly(e);
        assertThat(geom.generatorZones()).isEmpty();
        assertThat(geom.serverZones()).isEmpty();
    }

    @Test
    void returnedCollectionsAreImmutable() {
        Rectangle area = new Rectangle(new Vec2(0, 0), new Vec2(2, 2));
        assertThat(area).isNotNull();
        GeometryImpl geom = new GeometryImpl(
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> geom.walls().add(new Wall(new Vec2(0, 0), new Vec2(1, 0))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
