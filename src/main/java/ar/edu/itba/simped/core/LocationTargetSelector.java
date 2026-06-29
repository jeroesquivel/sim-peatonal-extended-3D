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
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Vec2> available = candidates.stream()
                .filter(candidate -> excluded == null || !excluded.contains(candidate))
                .toList();
        if (available.isEmpty()) {
            return null;
        }

        ObjectiveSelection safeSelection = selection != null ? selection : ObjectiveSelection.CLOSEST;
        return switch (safeSelection) {
            case RANDOM -> chooseRandom(available, seed);
            case CLOSEST, ALL -> chooseClosest(available, from);
        };
    }

    private static Vec2 chooseClosest(List<Vec2> candidates, Vec2 from) {
        Vec2 anchor = from != null ? from : Vec2.ZERO;
        return candidates.stream()
                .min(Comparator.comparingDouble(anchor::distanceTo))
                .orElse(candidates.get(0));
    }

    private static Vec2 chooseRandom(List<Vec2> candidates, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        return candidates.get(rng.nextInt(candidates.size()));
    }
}
