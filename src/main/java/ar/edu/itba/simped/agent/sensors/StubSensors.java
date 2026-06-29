package ar.edu.itba.simped.agent.sensors;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Sensors;
import ar.edu.itba.simped.core.ports.ServerSignal;

/** STUB - implementado por G2. Reemplazar. */
public final class StubSensors implements Sensors {

    @Override
    public void sense() {
        // TODO T3
    }

    @Override
    public double distanceToTarget() {
        return Double.POSITIVE_INFINITY; // TODO G2
    }

    @Override
    public void setFootTarget(Vec2 footTarget) {
        // TODO G2
    }

    @Override
    public void onServerSignal(ServerSignal signal) {
        // TODO G2
    }
}
