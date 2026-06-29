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

    record ServerRow(String base, int id, Rectangle area) implements ServersCsvRow {
    }

    record QueueRow(String base, int id, int queueIndex, Segment segment) implements ServersCsvRow {
    }
}
