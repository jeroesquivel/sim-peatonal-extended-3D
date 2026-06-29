package ar.edu.itba.simped.core;

/**
 * Tipo de Server (5.5.a del contract v4). Determina el lifecycle que
 * Servers (G0) aplica durante una delegación.
 *
 * <ul>
 *   <li>{@link #QUEUE} — cola FIFO + un puesto de servicio; servicio
 *       individual con {@code Exp(t_mean)} por agente.</li>
 *   <li>{@link #SEMAPHORE} — recinto colectivo; libera a todos los
 *       agentes que estén adentro cada {@code t_mean} segundos.</li>
 *   <li>{@link #CLASSROOM} — recinto colectivo con sesiones; libera a
 *       todos los agentes presentes en {@code t_init[i] + t_mean},
 *       sin importar cuándo llegaron.</li>
 * </ul>
 */
public enum ServerType {
    QUEUE,
    SEMAPHORE,
    CLASSROOM
}
