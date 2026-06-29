package ar.edu.itba.simped.environment.servers.io;

import ar.edu.itba.simped.environment.servers.interfaces.EventSink;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Logs I13c events (service_complete / broadcast) to stdout.
 */
public final class ConsoleEventSink implements EventSink {

    private final DoubleSupplier clock;

    public ConsoleEventSink() {
        this(() -> Double.NaN);
    }

    public ConsoleEventSink(DoubleSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void serviceComplete(int agentId) {
        System.out.printf(Locale.US, "[t=%6.2f] I13c COMPLETE  agent=%-3d (service done)%n",
                clock.getAsDouble(), agentId);
    }
}
