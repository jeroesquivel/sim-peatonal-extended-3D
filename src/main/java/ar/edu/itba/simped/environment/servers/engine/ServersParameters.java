package ar.edu.itba.simped.environment.servers.engine;

/**
 * Tunable parameters of the Servers module. Per-server timing (service mean for
 * QUEUE, period for SEMAPHORE, schedule for CLASSROOM) is carried on each
 * {@link ar.edu.itba.simped.environment.servers.model.Server}'s
 * {@link ar.edu.itba.simped.environment.servers.model.ServerConfig}.
 *
 * @param slotSpacing        distance between consecutive queue slots [m]
 * @param arrivalThreshold   distance to consider an agent "arrived" at a
 *                           slot or at the service position [m]
 * @param meanServiceTime    representative service time used by the softmax
 *                           assigner as the load weight in
 *                           {@code cost = mean·L + alpha·d/v0} [s]
 */
public record ServersParameters(
        double slotSpacing,
        double arrivalThreshold,
        double meanServiceTime) {

    public ServersParameters {
        if (!(slotSpacing > 0.0)) {
            throw new IllegalArgumentException("slotSpacing must be positive");
        }
        if (!(arrivalThreshold > 0.0)) {
            throw new IllegalArgumentException("arrivalThreshold must be positive");
        }
        if (!(meanServiceTime > 0.0)) {
            throw new IllegalArgumentException("meanServiceTime must be positive");
        }
    }

    public static ServersParameters defaults() {
        // slotSpacing 1.0: the chair requires the distance between queue slots
        // to exceed the sum of two agents' radii or the bodies overlap when
        // lining up (mail 2026-06-04). Worst case today is CPM with
        // rmax 0.37 x 1.10 (QUEUEING multiplier) per agent ~= 0.81 m.
        // arrivalThreshold 0.5: G7 (CPM) asked for a 0.3-0.5 m tolerance when
        // attending the queue head, to absorb the numerical error of their
        // position snapping at the slot (mail 2026-06-04, approved by chair).
        return new ServersParameters(1.0, 0.5, 3.0);
    }
}
