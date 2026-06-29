package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.PlanStep;
import ar.edu.itba.simped.core.TaskStep;
import ar.edu.itba.simped.core.TaskType;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cubre A4: selección CLOSEST/RANDOM/ALL + cantidad por PlanStep. */
class ObjectiveSelectionResolverTest {

    private static TaskStep loc(String name, double x, double y) {
        return new TaskStep(TaskType.LOCATION, new Vec2(x, y), name, Optional.empty());
    }

    private static final List<TaskStep> GONDOLAS = List.of(
            loc("G0", 0, 0),
            loc("G1", 10, 0),
            loc("G2", 3, 0),
            loc("G3", 100, 0));

    private static PlanStep step(ObjectiveSelection sel, Optional<Double> qty) {
        return new PlanStep(TaskType.LOCATION, "GONDOLA", sel,
                qty.map(q -> (ar.edu.itba.simped.core.Distribution) new Deterministic(q)),
                GONDOLAS);
    }

    @Test
    void closestPicksTheNearestToTheAgent() {
        // Desde (1,0): el más cercano es G0 (d=1), luego G2 (d=3 desde origen).
        List<TaskStep> r = ObjectiveSelectionResolver.resolve(
                step(ObjectiveSelection.CLOSEST, Optional.of(1.0)),
                new Vec2(1, 0), new SplittableRandom(1));
        assertEquals(1, r.size());
        assertEquals("G0", r.get(0).targetBlockName());
    }

    @Test
    void closestChainsByNearestNeighbour() {
        // Desde (1,0): G0(0,0) primero; luego desde (0,0) el más cercano es G2(3,0); luego G1(10,0).
        List<TaskStep> r = ObjectiveSelectionResolver.resolve(
                step(ObjectiveSelection.CLOSEST, Optional.of(3.0)),
                new Vec2(1, 0), new SplittableRandom(1));
        assertEquals(List.of("G0", "G2", "G1"),
                r.stream().map(TaskStep::targetBlockName).toList());
    }

    @Test
    void quantityIsClampedToCandidateCount() {
        List<TaskStep> r = ObjectiveSelectionResolver.resolve(
                step(ObjectiveSelection.CLOSEST, Optional.of(99.0)),
                new Vec2(0, 0), new SplittableRandom(1));
        assertEquals(4, r.size());
    }

    @Test
    void randomPicksRequestedCountWithoutReplacementAndIsDeterministic() {
        PlanStep s = new PlanStep(TaskType.LOCATION, "GONDOLA", ObjectiveSelection.RANDOM,
                Optional.of(new Uniform(2, 2)), GONDOLAS);
        List<TaskStep> a = ObjectiveSelectionResolver.resolve(s, new Vec2(0, 0), new SplittableRandom(42));
        List<TaskStep> b = ObjectiveSelectionResolver.resolve(s, new Vec2(0, 0), new SplittableRandom(42));
        assertEquals(2, a.size());
        assertEquals(2, a.stream().map(TaskStep::targetBlockName).distinct().count(), "sin reemplazo");
        assertEquals(a.stream().map(TaskStep::targetBlockName).toList(),
                b.stream().map(TaskStep::targetBlockName).toList(), "misma seed → mismo resultado");
    }

    @Test
    void defaultQuantityIsOneWhenAbsentAndSelectionNotAll() {
        List<TaskStep> r = ObjectiveSelectionResolver.resolve(
                step(ObjectiveSelection.CLOSEST, Optional.empty()),
                new Vec2(0, 0), new SplittableRandom(1));
        assertEquals(1, r.size());
    }

    @Test
    void allReturnsEveryCandidateInOrder() {
        List<TaskStep> r = ObjectiveSelectionResolver.resolve(
                step(ObjectiveSelection.ALL, Optional.empty()),
                new Vec2(50, 0), new SplittableRandom(1));
        assertEquals(List.of("G0", "G1", "G2", "G3"),
                r.stream().map(TaskStep::targetBlockName).toList());
    }

    @Test
    void exitSelectionRandomPicksExactlyOne() {
        List<TaskStep> exits = List.of(
                new TaskStep(TaskType.EXIT, new Vec2(0, 0), "E0", Optional.empty()),
                new TaskStep(TaskType.EXIT, new Vec2(5, 0), "E1", Optional.empty()));
        PlanStep s = new PlanStep(TaskType.EXIT, "ANY_EXIT", ObjectiveSelection.RANDOM,
                Optional.empty(), exits);
        List<TaskStep> r = ObjectiveSelectionResolver.resolve(s, new Vec2(0, 0), new SplittableRandom(7));
        assertEquals(1, r.size());
        assertTrue(r.get(0).targetBlockName().startsWith("E"));
    }
}
