package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.TaskType;

/**
 * Row interno de PLANS.csv (Formato A):
 * {@code template_name, step_order, target_type, target_block_name}.
 *
 * <p>{@code target_type} en CSV: {@code TARGET | SERVER | EXIT}.
 * Internamente mapeamos a {@link TaskType}: {@code TARGET → LOCATION},
 * {@code SERVER → SERVER}, {@code EXIT → EXIT} (V16 si valor distinto).</p>
 */
public record PlanStepRow(
        String templateName,
        int stepOrder,
        TaskType targetType,
        String targetBlockName) {
}
