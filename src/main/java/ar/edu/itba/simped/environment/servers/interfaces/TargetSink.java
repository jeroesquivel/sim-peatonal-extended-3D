package ar.edu.itba.simped.environment.servers.interfaces;

import ar.edu.itba.simped.core.Vec2;

/**
 * Edge <strong>I13b</strong> (Servers → PreOM). The Servers module pushes the
 * successive fine foot-targets of a delegated agent through this sink: queue
 * slot, queue advance, and service position.
 *
 * <p>Implemented by whoever integrates the module (the PreOM group); kept as an
 * interface so Servers does not depend on any concrete PreOM class.</p>
 */
@FunctionalInterface
public interface TargetSink {

    /** A new fine target {@code (xt,yt)} for the delegated agent {@code agentId}. */
    void sendTarget(int agentId, Vec2 target);
}
