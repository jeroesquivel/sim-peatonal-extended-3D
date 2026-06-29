package ar.edu.itba.simped.core;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Parámetros de comportamiento de un {@link ServerZone}, leídos desde
 * {@code SERVER_PARAMS.csv} (block_name, type, server_time_param, t_init).
 *
 * <p>{@code serviceTime} lleva el {@code server_time_param} (t_mean). En
 * Formato A se modela como {@link Deterministic} con el valor crudo y G0 lo
 * interpreta según el tipo (Exp por cliente para queue, release colectivo
 * cada t_mean para semaphore, release en t_init+t_mean para classroom).</p>
 *
 * <p>{@code sessionStarts} lleva los {@code t_init} de cada sesión de un
 * classroom (una fila por sesión en el CSV, agrupadas por block_name).
 * Queda vacío para queue/semaphore.</p>
 *
 * <p>{@code greenDuration} es la duración del verde del ciclo de un
 * {@code semaphore} (columna {@code green_duration} del CSV); queda vacío para
 * queue/classroom. Para semaphore: {@code server_time_param} = período del
 * ciclo, {@code greenDuration} = verde, {@code t_init} = offset (en
 * {@code startTime}).</p>
 *
 * <p>{@code capacity} e {@code inactiveTime} quedan para Formato B /
 * futuras extensiones; {@code startTime} replica el primer {@code t_init}
 * por conveniencia.</p>
 */
public record ServerParams(
        Optional<Distribution> serviceTime,
        OptionalDouble capacity,
        OptionalDouble startTime,
        OptionalDouble inactiveTime,
        List<Double> sessionStarts,
        OptionalDouble greenDuration) {

    public ServerParams {
        if (serviceTime == null) {
            throw new IllegalArgumentException("serviceTime must be Optional.empty(), not null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("capacity must be OptionalDouble.empty(), not null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must be OptionalDouble.empty(), not null");
        }
        if (inactiveTime == null) {
            throw new IllegalArgumentException("inactiveTime must be OptionalDouble.empty(), not null");
        }
        if (sessionStarts == null) {
            throw new IllegalArgumentException("sessionStarts must be an empty list, not null");
        }
        if (greenDuration == null) {
            throw new IllegalArgumentException("greenDuration must be OptionalDouble.empty(), not null");
        }
        sessionStarts = List.copyOf(sessionStarts);
    }

    public static ServerParams empty() {
        return new ServerParams(
                Optional.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                List.of(),
                OptionalDouble.empty());
    }
}
