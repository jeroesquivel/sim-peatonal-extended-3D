package ar.edu.itba.simped.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StairsTest {

    @Test
    void pointAtInterpolatesZAlongTheRun() {
        // Pie en (10,5,0), tope en (10,9,3): tramo planar de largo 4, sube 3 plantas.
        Stairs s = new Stairs("MAIN", new Vec3(10, 5, 0), new Vec3(10, 9, 3), 2.0);

        assertThat(s.pointAt(0.0)).isEqualTo(new Vec3(10, 5, 0));
        assertThat(s.pointAt(1.0)).isEqualTo(new Vec3(10, 9, 3));

        Vec3 mid = s.pointAt(0.5);
        assertThat(mid.x()).isEqualTo(10.0, within(1e-9));
        assertThat(mid.y()).isEqualTo(7.0, within(1e-9));
        assertThat(mid.z()).isEqualTo(1.5, within(1e-9));
    }

    @Test
    void pointAtClampsProgressToUnitInterval() {
        Stairs s = new Stairs("MAIN", new Vec3(0, 0, 0), new Vec3(0, 10, 3), 2.0);
        assertThat(s.pointAt(-1.0)).isEqualTo(s.foot());
        assertThat(s.pointAt(2.0)).isEqualTo(s.top());
    }

    @Test
    void horizontalLengthIgnoresZ() {
        Stairs s = new Stairs("MAIN", new Vec3(0, 0, 0), new Vec3(3, 4, 10), 2.0);
        assertThat(s.horizontalLength()).isEqualTo(5.0, within(1e-9));
        assertThat(s.axisXy().a()).isEqualTo(new Vec2(0, 0));
        assertThat(s.axisXy().b()).isEqualTo(new Vec2(3, 4));
    }

    @Test
    void defaultSpeedFactorIsApplied() {
        Stairs s = new Stairs("MAIN", new Vec3(0, 0, 0), new Vec3(0, 4, 3), 2.0);
        assertThat(s.speedFactor()).isEqualTo(Stairs.DEFAULT_SPEED_FACTOR);
    }

    @Test
    void rejectsStairsConnectingSameFloor() {
        assertThatThrownBy(() -> new Stairs("FLAT", new Vec3(0, 0, 1), new Vec3(0, 4, 1), 2.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different floors");
    }

    @Test
    void rejectsNonPositiveWidthAndOutOfRangeSpeedFactor() {
        assertThatThrownBy(() -> new Stairs("S", new Vec3(0, 0, 0), new Vec3(0, 4, 3), 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stairs("S", new Vec3(0, 0, 0), new Vec3(0, 4, 3), 2.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stairs("S", new Vec3(0, 0, 0), new Vec3(0, 4, 3), 2.0, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
