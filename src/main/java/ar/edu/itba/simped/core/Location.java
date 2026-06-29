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
 * @param shape        figura geométrica (Circle o Rectangle).
 * @param dwellTime    distribución de tiempo de atención por visita
 *                     ({@code empty()} si no se especifica). Los CSV
 *                     actuales no lo pueblan; reservado para extensiones.
 */
public record Location(String blockName, Shape shape, Optional<Distribution> dwellTime) {

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

    public Vec2 position() {
        return shape.centroid();
    }
}
