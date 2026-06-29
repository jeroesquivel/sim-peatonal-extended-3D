package ar.edu.itba.simped.environment.servers.model;

/**
 * Server kind, per the interface contract (§ 5.5.a).
 */
public enum ServerType {
    /** Queue + single service spot (cashier). One client served at a time, time sampled from {@code Exp}. */
    QUEUE,
    /** Region. Releases every agent inside whenever {@code t_sim % t_mean == 0}. */
    SEMAPHORE,
    /** Region. Releases every agent inside at each {@code T_init[i] + T_mean}. */
    CLASSROOM,
}
