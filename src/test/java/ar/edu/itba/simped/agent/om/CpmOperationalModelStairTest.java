package ar.edu.itba.simped.agent.om;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.Neighbor;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.Stairs;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.Vec3;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.environment.geometry.GeometryImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests de la física 3D del CPM (paso 6, D9): velocidad reducida e interpolación
 * de z en la escalera, y anti-tunneling con las paredes de la planta actual.
 */
class CpmOperationalModelStairTest {

    private static final AgentProfile PROFILE = CpmParameters.baglietoParisiSet1(); // vd=1.55, rmax=0.32
    private static final double SPEED_FACTOR = 0.5;

    private static AgentState walking(int id, double x, double y, double z) {
        AgentState a = new AgentState(id, "ped");
        a.setProfile(PROFILE);
        a.setPosition(x, y, z);
        a.setRadius(PROFILE.rmax()); // frac=1 → magnitud deseada = vd (sin transitorio de radio)
        a.setState(BehaviorState.WALKING);
        return a;
    }

    private static List<ar.edu.itba.simped.core.Wall> box(double z) {
        return new ArrayList<>(List.of(
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 0), new Vec2(10, 0), z),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 0), new Vec2(10, 10), z),
                new ar.edu.itba.simped.core.Wall(new Vec2(10, 10), new Vec2(0, 10), z),
                new ar.edu.itba.simped.core.Wall(new Vec2(0, 10), new Vec2(0, 0), z)));
    }

    private static Geometry geom(List<ar.edu.itba.simped.core.Wall> walls, List<Stairs> stairs) {
        return new GeometryImpl(walls, List.<Location>of(), List.<Exit>of(),
                List.<GeneratorZone>of(), List.<ServerZone>of(), stairs);
    }

    /** Geometría con dos plantas y una escalera vertical (en y) del pie (5,5,0) al tope (5,9,3). */
    private static Geometry twoFloorWithStair() {
        List<ar.edu.itba.simped.core.Wall> walls = box(0.0);
        walls.addAll(box(3.0));
        Stairs stair = new Stairs("MAIN", new Vec3(5, 5, 0), new Vec3(5, 9, 3), 2.0, SPEED_FACTOR);
        return geom(walls, List.of(stair));
    }

    @Test
    void speedIsReducedOnStair() {
        CpmOperationalModel om = CpmOperationalModel.fromGeometry(twoFloorWithStair());
        double dt = 0.1;
        List<Neighbor> none = List.of();

        // Agente en planta plana, lejos de paredes: avanza a vd pleno.
        AgentState onFloor = walking(1, 3, 3, 0.0);
        om.integrate(onFloor, new Vec3(8, 3, 0), BehaviorState.WALKING, none, dt);
        double floorSpeed = Math.hypot(onFloor.vx(), onFloor.vy());

        // Agente sobre la escalera (z entre plantas), avanzando hacia el tope.
        AgentState onStair = walking(2, 5, 7, 1.5);
        om.integrate(onStair, new Vec3(5, 9, 3), BehaviorState.WALKING, none, dt);
        double stairSpeed = Math.hypot(onStair.vx(), onStair.vy());

        assertThat(floorSpeed).isCloseTo(PROFILE.vd(), within(1e-3));
        assertThat(stairSpeed).isCloseTo(PROFILE.vd() * SPEED_FACTOR, within(1e-3));
    }

    @Test
    void zInterpolatesWhileClimbingStair() {
        CpmOperationalModel om = CpmOperationalModel.fromGeometry(twoFloorWithStair());
        // Pie (5,5,0) → tope (5,9,3), largo planar 4. En (5,6) progreso 0.25 → z=0.75.
        AgentState a = walking(1, 5, 6, 0.75);
        double zBefore = a.z();

        om.integrate(a, new Vec3(5, 9, 3), BehaviorState.WALKING, List.of(), 0.1);

        // La z sigue al avance planar: z == zAt(x,y) y aumentó al subir.
        double expectedZ = 0.0 + (3.0 - 0.0) * ((a.y() - 5.0) / 4.0);
        assertThat(a.z()).isCloseTo(expectedZ, within(1e-9));
        assertThat(a.z()).isGreaterThan(zBefore);
        assertThat(a.y()).isGreaterThan(6.0); // efectivamente subió
    }

    @Test
    void antiTunnelingUsesCurrentFloorWalls() {
        // Planta 3 tiene un muro interior (5,0)-(5,10) que la planta 0 NO tiene.
        List<ar.edu.itba.simped.core.Wall> walls = box(0.0);
        walls.addAll(box(3.0));
        walls.add(new ar.edu.itba.simped.core.Wall(new Vec2(5, 0), new Vec2(5, 10), 3.0));
        CpmOperationalModel om = CpmOperationalModel.fromGeometry(geom(walls, List.of()));

        double dt = 0.5; // paso grande: el desplazamiento propuesto cruzaría x=5
        List<Neighbor> none = List.of();

        // En planta 0 (sin muro interior) el agente cruza x=5.
        AgentState ground = walking(1, 4.9, 5, 0.0);
        om.integrate(ground, new Vec3(8, 5, 0), BehaviorState.WALKING, none, dt);
        assertThat(ground.x()).isGreaterThan(5.0);

        // En planta 3 (con muro interior) el mismo movimiento queda bloqueado:
        // no atraviesa x=5 (desliza a lo largo del muro).
        AgentState upstairs = walking(2, 4.9, 5, 3.0);
        om.integrate(upstairs, new Vec3(8, 5, 3), BehaviorState.WALKING, none, dt);
        assertThat(upstairs.x()).isLessThan(5.0);
    }

    @Test
    void flatFloorAgentKeepsZ() {
        CpmOperationalModel om = CpmOperationalModel.fromGeometry(twoFloorWithStair());
        AgentState a = walking(1, 3, 3, 0.0);
        om.integrate(a, new Vec3(8, 3, 0), BehaviorState.WALKING, List.of(), 0.1);
        assertThat(a.z()).isEqualTo(0.0); // no está en escalera → z intacta
    }
}
