package ar.edu.itba.simped.core;

import java.util.OptionalDouble;

/**
 * Sub-componente 5.1.3 del contract v4.
 *
 * <p>Salida del escenario. Origen: filas de {@code EXITS.csv}
 * (segmento + block_name).</p>
 *
 * @param blockName     nombre del block (ej. "NORMAL", "EMERGENCY").
 * @param segment       segmento físico de la salida.
 * @param maxFlowRate   flujo específico máximo [ped/m/s];
 *                      {@code empty()} si el formato no lo provee
 *                      (Formato A actual no tiene este campo).
 */
public record Exit(String blockName, Segment segment, OptionalDouble maxFlowRate) {

    public Exit {
        if (blockName == null || blockName.isBlank()) {
            throw new IllegalArgumentException("Exit requires a non-blank blockName");
        }
        if (segment == null) {
            throw new IllegalArgumentException("Exit requires a non-null segment");
        }
        if (maxFlowRate == null) {
            throw new IllegalArgumentException("maxFlowRate must be OptionalDouble.empty(), not null");
        }
    }

    public Vec2 position() {
        return segment.midpoint();
    }

    public double width() {
        return segment.length();
    }
}
