package ar.edu.itba.simped.core;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Selector determinista de un target puntual dentro de un grupo de locations.
 *
 * <p>La StateMachine usa esta lógica en runtime, y el AgentAssembler la reutiliza
 * solo para previsualizar cuál será el target elegido y así encadenar el cursor
 * del resto del plan de forma consistente.</p>
 */
public final class LocationTargetSelector {

    private LocationTargetSelector() {
    }

    public static Vec2 choose(
            List<Vec2> candidates,
            ObjectiveSelection selection,
            Vec2 from,
            long seed,
            Collection<Vec2> excluded
    ) {
        int idx = chooseIndex(candidates, selection, from, seed, excluded);
        return idx < 0 ? null : candidates.get(idx);
    }

    /**
     * Igual que {@link #choose}, pero devuelve el <b>índice</b> del candidato
     * elegido (o {@code -1} si no hay disponible). Es la primitiva correcta cuando
     * los candidatos comparten {@code (x,y)} pero difieren en planta (aulas de PB
     * y P1 una sobre otra): el índice identifica la planta sin ambigüedad, mientras
     * que buscar por valor {@code Vec2} colapsaría los duplicados.
     */
    public static int chooseIndex(
            List<Vec2> candidates,
            ObjectiveSelection selection,
            Vec2 from,
            long seed,
            Collection<Vec2> excluded
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return -1;
        }
        List<Integer> available = new java.util.ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (excluded == null || !excluded.contains(candidates.get(i))) {
                available.add(i);
            }
        }
        if (available.isEmpty()) {
            return -1;
        }

        ObjectiveSelection safeSelection = selection != null ? selection : ObjectiveSelection.CLOSEST;
        return switch (safeSelection) {
            case RANDOM -> available.get(new SplittableRandom(seed).nextInt(available.size()));
            case CLOSEST, ALL -> {
                Vec2 anchor = from != null ? from : Vec2.ZERO;
                int best = available.get(0);
                double bestDist = anchor.distanceTo(candidates.get(best));
                for (int i : available) {
                    double d = anchor.distanceTo(candidates.get(i));
                    if (d < bestDist) {
                        bestDist = d;
                        best = i;
                    }
                }
                yield best;
            }
        };
    }
}
