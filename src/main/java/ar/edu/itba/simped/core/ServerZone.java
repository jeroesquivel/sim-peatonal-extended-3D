package ar.edu.itba.simped.core;

import java.util.List;

/**
 * Sub-componente 5.1.5 del contract v4.
 *
 * <p>Posición y geometría de un Server. Origen: agrupamiento de filas
 * de {@code SERVERS.csv} por {@code (baseName, id)} (parser de sufijo
 * en G3): el rectángulo del {@code _SERVER} más los segmentos de las
 * {@code _QUEUEnnn} asociadas.</p>
 *
 * <p>Consumido por G0 (Servers, I20) y por G8 (Graph) para
 * decidir nodos de aproximación frente a servers.</p>
 *
 * @param baseName  prefijo común del block (ej. "CASHIER", "PRESENTATION").
 * @param id        identificador secuencial dentro del base.
 * @param area      rectángulo del area de atención.
 * @param queues    segmentos ordenados de las queues (puede estar vacío).
 * @param type      inferido por G3 si Formato A (presencia de queue → QUEUE,
 *                  ausencia → CLASSROOM, BROADCAST nunca se infiere) o
 *                  declarado explícitamente en Formato A
 *                  ({@code SERVER_PARAMS.type}) o B.
 * @param params    parámetros adicionales (service time, capacity, etc.).
 */
public record ServerZone(
        String baseName,
        int id,
        Rectangle area,
        List<Segment> queues,
        ServerType type,
        ServerParams params) {

    public ServerZone {
        if (baseName == null || baseName.isBlank()) {
            throw new IllegalArgumentException("ServerZone requires a non-blank baseName");
        }
        if (id < 1) {
            throw new IllegalArgumentException("ServerZone id must be >= 1, got " + id);
        }
        if (area == null || type == null || params == null) {
            throw new IllegalArgumentException("ServerZone area/type/params must be non-null");
        }
        if (queues == null) {
            throw new IllegalArgumentException("queues must be a list, not null");
        }
        queues = List.copyOf(queues);
    }

    public Vec2 position() {
        return area.centroid();
    }
}
