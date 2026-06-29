package ar.edu.itba.simped.core;

import java.util.List;

/**
 * Plantilla de plan, identificada por nombre. G3 la carga desde
 * {@code PLANS.csv} / {@code parameters.json} y la expone vía
 * {@code LoadedScenario.planTemplates()}.
 *
 * <p>Cada {@link PlanStep} lleva el conjunto de candidatos + la regla de
 * selección ({@code CLOSEST}/{@code RANDOM}/{@code ALL}) y cantidad. El
 * {@code AgentAssembler}, al spawnear un agente, resuelve la selección
 * por-agente (la posición del agente importa para {@code CLOSEST}) y mapea los
 * {@link TaskStep}s elegidos a {@code Task} (G2).</p>
 */
public record PlanTemplate(String name, List<PlanStep> steps) {

    public PlanTemplate {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PlanTemplate requires a non-blank name");
        }
        if (steps == null) {
            throw new IllegalArgumentException("steps must be a list, not null");
        }
        steps = List.copyOf(steps);
    }
}
