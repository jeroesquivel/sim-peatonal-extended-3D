package ar.edu.itba.simped.core.ports;

/**
 * Marker para señales originadas en {@link Server} hacia {@link Sensors}
 * (I13c del contract v4). Caso esperado:
 * <ul>
 *   <li>service_complete (queue / semaphore / classroom)</li>
 * </ul>
 *
 * <p>G0 define los subtipos concretos. Esta interface marker solo fija el
 * tipo que cruza I13c.</p>
 */
public interface ServerSignal {
}
