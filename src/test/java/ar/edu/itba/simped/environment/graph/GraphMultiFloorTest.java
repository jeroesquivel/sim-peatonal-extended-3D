package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.environment.geometry.GeometryImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMultiFloorTest {

    /** Dos plantas (z=0 y z=3): cada una una sala cuadrada 10×10, unidas por una escalera. */
    private GeometryImpl twoFloorBuilding() {
        List<ar.edu.itba.simped.core.Wall> walls = List.of(
                // planta 0
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 0), new Vec2(10, 0), 0.0),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 0), new Vec2(10, 10), 0.0),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 10), new Vec2(0, 10), 0.0),
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 10), new Vec2(0, 0), 0.0),
                // planta 1 (z=3)
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 0), new Vec2(10, 0), 3.0),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 0), new Vec2(10, 10), 3.0),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 10), new Vec2(0, 10), 3.0),
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 10), new Vec2(0, 0), 3.0));
        List<Stairs> stairs = List.of(
                new Stairs("MAIN", new Vec3(5, 1, 0.0), new Vec3(5, 3, 3.0), 2.0));
        return new GeometryImpl(walls, List.of(), List.of(), List.of(), List.of(), stairs);
    }

    @Test
    void buildsNodesOnBothFloors() {
        StubGraph g = StubGraph.fromGeometry(twoFloorBuilding());
        // Al menos malla en ambas plantas + 2 nodos de escalera.
        assertThat(g.nodeCount()).isGreaterThan(2);
    }

    @Test
    void sameFloorVisibleTargetReturnsTargetDirectly() {
        StubGraph g = StubGraph.fromGeometry(twoFloorBuilding());
        Vec3 target = new Vec3(8, 8, 0.0);
        Vec3 hop = g.nextVisibleHop(new Vec3(2, 2, 0.0), target);
        // Línea de vista directa en la misma planta → el hop es el target.
        assertThat(hop).isEqualTo(target);
    }

    @Test
    void crossFloorTargetRoutesWithinAgentFloorTowardStair() {
        StubGraph g = StubGraph.fromGeometry(twoFloorBuilding());
        Vec3 agent = new Vec3(2, 2, 0.0);
        Vec3 targetUpstairs = new Vec3(8, 8, 3.0);

        Vec3 hop = g.nextVisibleHop(agent, targetUpstairs);

        // El target está en otra planta: NO es visible, así que el hop nunca es el
        // target (no se teletransporta) y se queda en la planta del agente (z=0),
        // dirigiéndose hacia el pie de la escalera.
        assertThat(hop).isNotEqualTo(targetUpstairs);
        assertThat(hop.z()).isEqualTo(0.0);
    }
}
