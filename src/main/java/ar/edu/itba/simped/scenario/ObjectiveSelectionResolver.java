package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.PlanStep;
import ar.edu.itba.simped.core.TaskStep;
import ar.edu.itba.simped.core.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Resuelve, por-agente, a cuál(es) candidato(s) de un {@link PlanStep} va el
 * agente, según su selección y cantidad. La elección parte de la posición
 * {@code from} (importa para {@code CLOSEST}) y es determinista dado el
 * {@code rng}.
 */
final class ObjectiveSelectionResolver {

    private ObjectiveSelectionResolver() {
    }

    static List<TaskStep> resolve(PlanStep step, Vec2 from, SplittableRandom rng) {
        List<TaskStep> candidates = step.candidates();
        if (step.selection() == ObjectiveSelection.ALL) {
            return candidates;   // todos, en el orden del escenario
        }
        int n = step.quantity()
                .map(d -> (int) Math.round(d.sample(rng)))
                .orElse(1);
        n = Math.max(0, Math.min(n, candidates.size()));
        if (n == 0) {
            return List.of();
        }
        return switch (step.selection()) {
            case CLOSEST -> closest(candidates, from, n);
            case RANDOM -> randomPick(candidates, rng, n);
            case ALL -> candidates;   // inalcanzable (cubierto arriba)
        };
    }

    /** Los {@code n} más cercanos por vecino-más-próximo encadenado desde {@code from}. */
    private static List<TaskStep> closest(List<TaskStep> candidates, Vec2 from, int n) {
        List<TaskStep> remaining = new ArrayList<>(candidates);
        List<TaskStep> out = new ArrayList<>(n);
        Vec2 cursor = from;
        for (int k = 0; k < n; k++) {
            int best = 0;
            double bestDist = cursor.distanceTo(remaining.get(0).target());
            for (int j = 1; j < remaining.size(); j++) {
                double d = cursor.distanceTo(remaining.get(j).target());
                if (d < bestDist) {
                    bestDist = d;
                    best = j;
                }
            }
            TaskStep pick = remaining.remove(best);
            out.add(pick);
            cursor = pick.target();
        }
        return out;
    }

    /** {@code n} candidatos al azar sin reemplazo (Fisher-Yates parcial). */
    private static List<TaskStep> randomPick(List<TaskStep> candidates, SplittableRandom rng, int n) {
        List<TaskStep> pool = new ArrayList<>(candidates);
        for (int k = 0; k < n; k++) {
            int j = k + rng.nextInt(pool.size() - k);
            TaskStep tmp = pool.get(k);
            pool.set(k, pool.get(j));
            pool.set(j, tmp);
        }
        return new ArrayList<>(pool.subList(0, n));
    }
}
