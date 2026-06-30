package ar.edu.itba.simped.core;

import java.util.OptionalDouble;

/**
 * Sub-componente 5.1.3 del contract v4.
 *
 * <p>Salida del escenario. Origen: filas de {@code EXITS.csv}
 * (segmento + block_name).</p>
 *
 * @param blockName     nombre del block (ej. "NORMAL", "EMERGENCY").
 * @param segment       segmento físico de la salida (planar).
 * @param z             planta a la que pertenece la salida (0 = planta baja).
 * @param maxFlowRate   flujo específico máximo [ped/m/s];
 *                      {@code empty()} si el formato no lo provee
 *                      (Formato A actual no tiene este campo).
 */
public record Exit(String blockName, Segment segment, double z, OptionalDouble maxFlowRate) {

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

    /** Salida en la planta baja ({@code z = 0}). */
    public Exit(String blockName, Segment segment, OptionalDouble maxFlowRate) {
        this(blockName, segment, 0.0, maxFlowRate);
    }

    public Vec2 position() {
        return segment.midpoint();
    }

    /** Posición 3D (centro del segmento en su planta). */
    public Vec3 position3D() {
        return segment.midpoint().withZ(z);
    }

    public double width() {
        return segment.length();
    }
}
