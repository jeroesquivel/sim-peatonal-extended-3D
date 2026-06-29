package ar.edu.itba.simped.core;

import java.util.List;
import java.util.Optional;

/**
 * Paso de un {@link PlanTemplate} antes de la resolución por-agente.
 *
 * <p>Lleva el conjunto de {@code candidates} (todos los blocks que matchean) y
 * la regla de selección. El agente, al construir su plan, elige cuáles visitar
 * según {@link #selection} y {@link #quantity} (ver {@code AgentAssembler}):
 * <ul>
 *   <li>Un paso "fijo" (Formato A, o un SERVER que ya colapsa a su grupo) es
 *       un PlanStep con un solo candidato y {@code selection = ALL}.</li>
 *   <li>Un {@code objective_group} (Formato B) lleva varios candidatos y
 *       {@code CLOSEST}/{@code RANDOM} + una distribución de cantidad.</li>
 * </ul>
 *
 * @param type       tipo de los candidatos (todos del mismo type).
 * @param blockName  nombre del block original (trazabilidad).
 * @param selection  cómo elegir entre los candidatos.
 * @param quantity   cuántos visitar; {@code empty()} → 1 si la selección no es
 *                   ALL, o todos si es ALL.
 * @param candidates targets resueltos que matchean (no vacío).
 */
public record PlanStep(
        TaskType type,
        String blockName,
        ObjectiveSelection selection,
        Optional<Distribution> quantity,
        List<TaskStep> candidates) {

    public PlanStep {
        if (type == null) {
            throw new IllegalArgumentException("PlanStep requires a non-null type");
        }
        if (blockName == null || blockName.isBlank()) {
            throw new IllegalArgumentException("PlanStep requires a non-blank blockName");
        }
        if (selection == null) {
            throw new IllegalArgumentException("PlanStep requires a non-null selection");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("quantity must be Optional.empty(), not null");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("PlanStep requires at least one candidate");
        }
        candidates = List.copyOf(candidates);
    }

    /** Atajo para un paso de un solo candidato (Formato A / SERVER de grupo). */
    public static PlanStep fixed(TaskStep step) {
        return new PlanStep(step.type(), step.targetBlockName(),
                ObjectiveSelection.ALL, Optional.empty(), List.of(step));
    }
}
