package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.TaskType;

import java.util.Optional;

/**
 * Step intermedio de un plan template antes de resolverse a un
 * {@link ar.edu.itba.simped.core.PlanStep} (con candidatos resueltos por
 * {@link PlanTemplatesBuilder}).
 *
 * <p>Tres variantes:
 * <ul>
 *   <li>{@link RawSingleStep} — apunta a un {@code block_name}
 *       específico (Formato A: una row de {@code PLANS.csv}).</li>
 *   <li>{@link RawGroupStep} — apunta a los blocks que matchean
 *       el {@code groupBlockName} en la layer del type, con su regla de
 *       selección y cantidad (Formato B: una entry de
 *       {@code objective_groups[]}).</li>
 *   <li>{@link RawAnyStep} — apunta a los blocks de la layer con una regla
 *       de selección (Formato B: exit final / {@code exit_selection}).</li>
 * </ul>
 */
public sealed interface RawPlanStep
        permits RawPlanStep.RawSingleStep, RawPlanStep.RawGroupStep, RawPlanStep.RawAnyStep {

    TaskType type();

    record RawSingleStep(TaskType type, String targetBlockName) implements RawPlanStep {
        public RawSingleStep {
            if (type == null) {
                throw new IllegalArgumentException("type required");
            }
            if (targetBlockName == null || targetBlockName.isBlank()) {
                throw new IllegalArgumentException("targetBlockName required");
            }
        }
    }

    record RawGroupStep(
            TaskType type,
            String groupBlockName,
            ObjectiveSelection selection,
            Optional<Distribution> quantity) implements RawPlanStep {
        public RawGroupStep {
            if (type == null) {
                throw new IllegalArgumentException("type required");
            }
            if (groupBlockName == null || groupBlockName.isBlank()) {
                throw new IllegalArgumentException("groupBlockName required");
            }
            if (selection == null) {
                throw new IllegalArgumentException("selection required");
            }
            if (quantity == null) {
                throw new IllegalArgumentException("quantity must be Optional.empty(), not null");
            }
        }
    }

    record RawAnyStep(TaskType type, ObjectiveSelection selection) implements RawPlanStep {
        public RawAnyStep {
            if (type == null) {
                throw new IllegalArgumentException("type required");
            }
            if (selection == null) {
                throw new IllegalArgumentException("selection required");
            }
        }
    }
}
