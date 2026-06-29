package ar.edu.itba.simped.environment.servers.interfaces;

/**
 * Edge <strong>I13c</strong> (Servers → Sensors). The Servers module signals
 * two events for the three server types (queue, semaphore, classroom):
 * <ul>
 *   <li>{@link #arrivedAtPost}: the agent physically reached its assigned
 *       post; the SM switches it to QUEUEING.</li>
 *   <li>{@link #serviceComplete}: the delegated service finished; the Sensors
 *       module relays it to the StateMachine as a {@code TASK_COMPLETE}
 *       (I10a).</li>
 * </ul>
 *
 * <p>Implemented by whoever integrates the module (the Sensors group); kept as
 * an interface so Servers does not depend on any concrete Sensors class.</p>
 */
@FunctionalInterface
public interface EventSink {

    /** The delegated service of {@code agentId} finished. */
    void serviceComplete(int agentId);

    /**
     * {@code agentId} physically reached its assigned post: its queue slot
     * (within {@code arrivalThreshold}) or the waiting region of a
     * semaphore/classroom. Emitted at most once per delegation; queue advances
     * do not re-emit it.
     */
    default void arrivedAtPost(int agentId) {
    }
}
