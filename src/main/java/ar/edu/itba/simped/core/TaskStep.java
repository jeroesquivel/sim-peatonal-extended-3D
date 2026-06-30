package ar.edu.itba.simped.core;

import java.util.Optional;

/**
 * Step inmutable de un {@link PlanTemplate}. G3 resuelve cada
 * {@code target_block_name} del {@code PLANS.csv} a la posición
 * concreta (centroide de Location, posición de Server, midpoint de
 * Exit) cuando construye el template.
 *
 * <p>El consumer (G9 al spawnear) toma este step y construye su
 * propio {@code Task} de G2 (que vive en {@code agent/plan/}).</p>
 *
 * @param type            tipo del target.
 * @param target          posición planar ya resuelta.
 * @param z               planta del target (0 = planta baja).
 * @param targetBlockName nombre del block original (preservado para
 *                        debug y trazabilidad).
 * @param dwellDistribution distribución de dwell del step si aplica;
 *                          {@code empty()} si no.
 */
public record TaskStep(
        TaskType type,
        Vec2 target,
        double z,
        String targetBlockName,
        Optional<Distribution> dwellDistribution,
        Optional<Segment> exitSegment) {

    public TaskStep {
        if (type == null) {
            throw new IllegalArgumentException("TaskStep requires a non-null type");
        }
        if (target == null) {
            throw new IllegalArgumentException("TaskStep requires a non-null target");
        }
        if (targetBlockName == null || targetBlockName.isBlank()) {
            throw new IllegalArgumentException("TaskStep requires a non-blank targetBlockName");
        }
        if (dwellDistribution == null) {
            throw new IllegalArgumentException("dwellDistribution must be Optional.empty(), not null");
        }
        if (exitSegment == null) {
            throw new IllegalArgumentException("exitSegment must be Optional.empty(), not null");
        }
    }

    /** Posición 3D del target (planar + planta). */
    public Vec3 target3D() {
        return target.withZ(z);
    }

    public TaskStep(
            TaskType type,
            Vec2 target,
            double z,
            String targetBlockName,
            Optional<Distribution> dwellDistribution
    ) {
        this(type, target, z, targetBlockName, dwellDistribution, Optional.empty());
    }

    /** Conveniencia: target en planta baja ({@code z = 0}). */
    public TaskStep(
            TaskType type,
            Vec2 target,
            String targetBlockName,
            Optional<Distribution> dwellDistribution
    ) {
        this(type, target, 0.0, targetBlockName, dwellDistribution, Optional.empty());
    }
}
