package ar.edu.itba.simped.core.ports;

/**
 * Marker para una entrada de la task list del Plan (sub-módulo 4.2 del
 * contract v4). Cada entrada referencia una de:
 * <ul>
 *   <li>Location (5.1.2)</li>
 *   <li>Server (5.5)</li>
 *   <li>Exit (5.1.3)</li>
 * </ul>
 *
 * <p>G2 (Plan) en conjunto con G3 (Geometry) y G0 (Servers) define los
 * subtipos concretos. Esta interface marker solo fija el tipo que cruza
 * I8 (Plan → SM).</p>
 */
public interface TaskTarget {
}
