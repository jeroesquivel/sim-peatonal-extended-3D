package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.ServerType;

import java.util.OptionalDouble;

/**
 * Row interno de SERVER_PARAMS.csv (Formato A):
 * {@code block_name, type, server_time_param, green_duration, t_init}.
 *
 * <p>{@code blockName} debe corresponder a un {@code _SERVER} de
 * SERVERS.csv (V19 si no). {@code type} ∈ {QUEUE, SEMAPHORE, CLASSROOM}
 * (V15 si no). Según el tipo:</p>
 * <ul>
 *   <li>QUEUE: {@code serverTimeParam} = tiempo de servicio; green y t_init
 *       vacíos; una fila.</li>
 *   <li>SEMAPHORE: {@code serverTimeParam} = período del ciclo,
 *       {@code greenDuration} = duración del verde, {@code tInit} = offset;
 *       una fila.</li>
 *   <li>CLASSROOM: {@code serverTimeParam} = duración de la sesión, green
 *       vacío, {@code tInit} = inicio de cada sesión; una fila por sesión.</li>
 * </ul>
 */
public record ServerParamsRow(
        String blockName,
        ServerType type,
        double serverTimeParam,
        OptionalDouble greenDuration,
        OptionalDouble tInit) {
}
