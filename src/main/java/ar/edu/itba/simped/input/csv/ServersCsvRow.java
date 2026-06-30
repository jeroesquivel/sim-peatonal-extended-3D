package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Segment;

/**
 * Row interno de SERVERS.csv. El grouping por (base, id) y el
 * armado del ServerZone se hace en el GeometryAssembler (Bloque H).
 */
public sealed interface ServersCsvRow permits ServersCsvRow.ServerRow, ServersCsvRow.QueueRow {

    String base();

    int id();

    /** Planta a la que pertenece la fila (0 = planta baja). */
    double z();

    record ServerRow(String base, int id, Rectangle area, double z) implements ServersCsvRow {
        public ServerRow(String base, int id, Rectangle area) {
            this(base, id, area, 0.0);
        }
    }

    record QueueRow(String base, int id, int queueIndex, Segment segment, double z) implements ServersCsvRow {
        public QueueRow(String base, int id, int queueIndex, Segment segment) {
            this(base, id, queueIndex, segment, 0.0);
        }
    }
}
