package ar.edu.itba.simped.core;

import java.util.Optional;

/**
 * Sub-componente 5.1.2 del contract v4.
 *
 * <p>Punto físico que un agente puede tener como task (góndola,
 * waypoint, etc.). Origen: filas de {@code TARGETS.csv} con
 * {@code figure_type ∈ {CIRCLE, RECTANGLE}}.</p>
 *
 * @param blockName    nombre del block en el DXF/CSV (clave de join).
 * @param shape        figura geométrica planar (Circle o Rectangle).
 * @param z            planta a la que pertenece el target (0 = planta baja).
 * @param dwellTime    distribución de tiempo de atención por visita
 *                     ({@code empty()} si no se especifica). Los CSV
 *                     actuales no lo pueblan; reservado para extensiones.
 */
public record Location(String blockName, Shape shape, double z, Optional<Distribution> dwellTime) {

    public Location {
        if (blockName == null || blockName.isBlank()) {
            throw new IllegalArgumentException("Location requires a non-blank blockName");
        }
        if (shape == null) {
            throw new IllegalArgumentException("Location requires a non-null shape");
        }
        if (dwellTime == null) {
            throw new IllegalArgumentException("dwellTime must be Optional.empty(), not null");
        }
    }

    /** Target en la planta baja ({@code z = 0}). */
    public Location(String blockName, Shape shape, Optional<Distribution> dwellTime) {
        this(blockName, shape, 0.0, dwellTime);
    }

    public Vec2 position() {
        return shape.centroid();
    }

    /** Posición 3D (centroide de la figura en su planta). */
    public Vec3 position3D() {
        return shape.centroid().withZ(z);
    }
}
