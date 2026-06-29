package ar.edu.itba.simped.environment.servers.engine;

import ar.edu.itba.simped.environment.servers.model.ServerType;

/**
 * Mutable per-agent delegation state, owned by {@link ServersModule}.
 * Package-private: callers observe progress through the sinks, not this object.
 */
final class DelegatedAgent {

    final int agentId;
    final int serverId;
    final ServerType serverType;

    ServicePhase phase;
    int slotIdx;                       // meaningful for QUEUE phases
    double serviceEndTime = Double.NaN; // set when service starts
    boolean settled = false;            // reached its post once (arrivedAtPost emitted)

    DelegatedAgent(int agentId, int serverId, ServerType serverType, ServicePhase phase) {
        this.agentId = agentId;
        this.serverId = serverId;
        this.serverType = serverType;
        this.phase = phase;
    }
}
