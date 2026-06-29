package ar.edu.itba.simped.environment.servers.io;

import ar.edu.itba.simped.environment.servers.interfaces.TargetSink;
import ar.edu.itba.simped.core.Vec2;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Logs I13b targets to stdout. A {@link DoubleSupplier} clock lets the runner
 * stamp each line with the current simulation time.
 */
public final class ConsoleTargetSink implements TargetSink {

    private final DoubleSupplier clock;

    public ConsoleTargetSink() {
        this(() -> Double.NaN);
    }

    public ConsoleTargetSink(DoubleSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void sendTarget(int agentId, Vec2 target) {
        System.out.printf(Locale.US, "[t=%6.2f] I13b target   agent=%-3d -> %s%n",
                clock.getAsDouble(), agentId, target);
    }
}
