package ar.edu.itba.simped.core.ports;

/**
 * Señales concretas de I13c (Servers → Sensors), definidas por G0 como indica
 * {@link ServerSignal}.
 *
 * <ul>
 *   <li>{@link #ARRIVED_AT_POST}: el agente llegó físicamente a su puesto
 *       asignado (slot de fila, región de semáforo o de classroom). Los
 *       Sensors lo relayan a la SM para pasar de WALKING/APPROACHING a
 *       QUEUEING — el agente recién ahí está "en la fila".</li>
 *   <li>{@link #SERVICE_COMPLETE}: terminó el servicio delegado; los Sensors
 *       lo relayan como task_complete (I10a) y el plan avanza.</li>
 * </ul>
 */
public enum StandardServerSignal implements ServerSignal {
    ARRIVED_AT_POST,
    SERVICE_COMPLETE
}
