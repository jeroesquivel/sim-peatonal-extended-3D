package ar.edu.itba.simped.environment.neighbors;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.NeighborType;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.environment.geometry.GeometryImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del CIM por planta (paso 5, D8): paridad con el caso 2D, independencia
 * por planta, paredes/vértices como vecinos por planta, y el puente de escalera.
 */
class FloorAwareNeighborsIndexTest {

    private static final double RMAX = 2.0;

    private static AgentState agent(int id, double x, double y, double z) {
        AgentState a = new AgentState(id, "ped");
        a.setPosition(x, y, z);
        a.setRadius(0.25);
        return a;
    }

    /** Caja cuadrada de lado 10 en la planta z (4 paredes). */
    private static List<ar.edu.itba.simped.core.Wall> box(double z) {
        return List.of(
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 0), new Vec2(10, 0), z),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 0), new Vec2(10, 10), z),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 10), new Vec2(0, 10), z),
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 10), new Vec2(0, 0), z));
    }

    private static Geometry geometry(List<ar.edu.itba.simped.core.Wall> walls, List<Stairs> stairs) {
        return new GeometryImpl(walls, List.<Location>of(), List.<Exit>of(),
                List.<GeneratorZone>of(), List.<ServerZone>of(), stairs);
    }

    // ── Paridad con el CIM 2D ────────────────────────────────────────────────

    @Test
    void singleFloorMatchesPlainCim() {
        List<ar.edu.itba.simped.core.Wall> walls = box(0.0);
        Geometry geom = geometry(walls, List.of());

        FloorAwareNeighborsIndex floorAware = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);
        // CIM 2D directo con la misma lista global (mismo orden de ids).
        CimNeighborsIndex plain = new CimNeighborsIndex(
                FloorAwareNeighborsIndex.globalWalls(geom), RMAX);

        AgentState a = agent(1, 5, 5, 0.0);
        AgentState b = agent(2, 6, 5, 0.0);
        AgentState wallProbe = agent(3, 0.5, 5, 0.0); // pegado a la pared izquierda
        for (AgentState s : List.of(a, b, wallProbe)) {
            floorAware.update(s);
            plain.update(s);
        }

        assertThat(floorAware.neighborsOf(a, RMAX))
                .usingRecursiveComparison()
                .isEqualTo(plain.neighborsOf(a, RMAX));
        assertThat(floorAware.neighborsOf(wallProbe, RMAX))
                .usingRecursiveComparison()
                .isEqualTo(plain.neighborsOf(wallProbe, RMAX));
    }

    // ── Independencia por planta ─────────────────────────────────────────────

    @Test
    void agentsOnDifferentFloorsDoNotSeeEachOther() {
        List<ar.edu.itba.simped.core.Wall> walls = new java.util.ArrayList<>();
        walls.addAll(box(0.0));
        walls.addAll(box(3.0));
        Geometry geom = geometry(walls, List.of());
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);

        AgentState ground = agent(1, 5, 5, 0.0);
        AgentState upstairs = agent(2, 5, 5, 3.0); // mismo (x,y), otra planta
        idx.update(ground);
        idx.update(upstairs);

        assertThat(idx.neighborsOf(ground, RMAX))
                .noneMatch(n -> n.type() == NeighborType.AGENT && n.id() == 2);
        assertThat(idx.neighborsOf(upstairs, RMAX))
                .noneMatch(n -> n.type() == NeighborType.AGENT && n.id() == 1);
    }

    @Test
    void agentsOnSameFloorSeeEachOther() {
        List<ar.edu.itba.simped.core.Wall> walls = new java.util.ArrayList<>();
        walls.addAll(box(0.0));
        walls.addAll(box(3.0));
        Geometry geom = geometry(walls, List.of());
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);

        AgentState a = agent(1, 5, 5, 3.0);
        AgentState b = agent(2, 6, 5, 3.0);
        idx.update(a);
        idx.update(b);

        assertThat(idx.neighborsOf(a, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 2);
    }

    // ── Paredes / vértices como vecinos, por planta (id global resoluble) ─────

    @Test
    void wallNeighborIdResolvesInGlobalListAndIsFromOwnFloor() {
        List<ar.edu.itba.simped.core.Wall> walls = new java.util.ArrayList<>();
        walls.addAll(box(0.0));   // ids globales 0..3
        walls.addAll(box(3.0));   // ids globales 4..7
        Geometry geom = geometry(walls, List.of());
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);
        List<Wall> global = idx.walls();

        AgentState onTop = agent(1, 0.5, 5, 3.0); // pegado a la pared izquierda de la planta 3
        idx.update(onTop);

        List<Neighbor> ns = idx.neighborsOf(onTop, RMAX);
        Vec2 pos = new Vec2(onTop.x(), onTop.y());
        assertThat(ns).anyMatch(n -> n.type() == NeighborType.WALL);
        for (Neighbor n : ns) {
            if (n.type() != NeighborType.WALL) continue;
            // El id global resuelve y la distancia coincide con la geometría.
            Wall w = global.get(n.id());
            assertThat(w.distanceTo(pos)).isCloseTo(n.distance(), org.assertj.core.api.Assertions.within(1e-9));
            // Es una pared de la planta 3 (ids 4..7), no de la planta baja.
            assertThat(n.id()).isGreaterThanOrEqualTo(4);
        }
    }

    @Test
    void wallVertexIsClosestPointWhenBeyondSegmentEnd() {
        // Un agente más allá del extremo de la pared inferior detecta el VÉRTICE
        // (esquina) como punto más cercano (clamp t->1 en Wall.closestPointTo).
        List<ar.edu.itba.simped.core.Wall> walls = box(0.0);
        Geometry geom = geometry(walls, List.of());
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);
        List<Wall> global = idx.walls();

        AgentState corner = agent(1, 10.8, 0.3, 0.0); // pasando la esquina (10,0)
        idx.update(corner);

        List<Neighbor> ns = idx.neighborsOf(corner, RMAX);
        Vec2 pos = new Vec2(corner.x(), corner.y());
        Neighbor bottom = ns.stream()
                .filter(n -> n.type() == NeighborType.WALL)
                .filter(n -> global.get(n.id()).p1().equals(new Vec2(0, 0)))
                .findFirst().orElseThrow();
        Vec2 closest = global.get(bottom.id()).closestPointTo(pos);
        assertThat(closest).isEqualTo(new Vec2(10, 0)); // el vértice, no un punto interior
    }

    // ── Puente de escalera ───────────────────────────────────────────────────

    private static Geometry twoFloorWithStair() {
        List<ar.edu.itba.simped.core.Wall> walls = new java.util.ArrayList<>();
        walls.addAll(box(0.0));
        walls.addAll(box(3.0));
        // Escalera del pie (5,5,0) al tope (5,9,3), ancho 2.
        Stairs stair = new Stairs("MAIN", new Vec3(5, 5, 0), new Vec3(5, 9, 3), 2.0);
        return geometry(walls, List.of(stair));
    }

    @Test
    void twoAgentsOnSameStairSeeEachOther() {
        Geometry geom = twoFloorWithStair();
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);

        // Ambos sobre el eje de la escalera, a distinta altura (z entre plantas).
        AgentState lower = agent(1, 5, 6.0, 0.75);
        AgentState upper = agent(2, 5, 6.8, 1.35);
        idx.update(lower);
        idx.update(upper);

        assertThat(idx.neighborsOf(lower, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 2);
        assertThat(idx.neighborsOf(upper, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 1);
    }

    @Test
    void stairAgentAndFootLandingAgentSeeEachOther() {
        Geometry geom = twoFloorWithStair();
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);

        AgentState onStair = agent(1, 5, 5.4, 0.3);   // recién subiendo
        AgentState atFoot = agent(2, 5, 4.8, 0.0);    // en la planta baja, junto al pie
        idx.update(onStair);
        idx.update(atFoot);

        assertThat(idx.neighborsOf(onStair, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 2);
        assertThat(idx.neighborsOf(atFoot, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 1);
    }

    @Test
    void stairAgentDoesNotSeeFarFloorAgent() {
        Geometry geom = twoFloorWithStair();
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);

        AgentState onStair = agent(1, 5, 6.0, 0.75);
        AgentState farGround = agent(2, 1, 1, 0.0); // lejos en la planta baja
        idx.update(onStair);
        idx.update(farGround);

        assertThat(idx.neighborsOf(onStair, RMAX))
                .noneMatch(n -> n.type() == NeighborType.AGENT && n.id() == 2);
    }

    @Test
    void agentMovingUpStairTransfersBetweenContainers() {
        Geometry geom = twoFloorWithStair();
        FloorAwareNeighborsIndex idx = FloorAwareNeighborsIndex.fromGeometry(geom, RMAX);

        // En el pie (planta baja): lo ve un agente de planta baja.
        AgentState mover = agent(1, 5, 5.0, 0.0);
        AgentState ground = agent(2, 5, 4.5, 0.0);
        AgentState top = agent(3, 5, 9.0, 3.0);
        idx.update(mover);
        idx.update(ground);
        idx.update(top);
        assertThat(idx.neighborsOf(ground, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 1);

        // Llega al tope (planta 3): deja de estar en la grilla baja y lo ve el de arriba.
        mover.setPosition(5, 9.0, 3.0);
        idx.update(mover);
        assertThat(idx.neighborsOf(top, RMAX))
                .anyMatch(n -> n.type() == NeighborType.AGENT && n.id() == 1);
        assertThat(idx.neighborsOf(ground, RMAX))
                .noneMatch(n -> n.type() == NeighborType.AGENT && n.id() == 1);
    }
}
