package ar.edu.itba.simped.environment.servers.engine;

/**
 * Phase of a delegated agent inside the Servers module. Mirrors the TP4
 * client automaton, reframed as a delegation subprocess.
 */
public enum ServicePhase {
    /** Walking towards its assigned queue slot (QUEUE only). */
    WALKING_TO_SLOT,
    /** Physically standing at its queue slot (QUEUE only). */
    IN_QUEUE,
    /** Engaged: walking towards the service position. */
    WALKING_TO_SERVICE,
    /** Being served; will finish at {@code serviceEndTime}. */
    IN_SERVICE,
}
