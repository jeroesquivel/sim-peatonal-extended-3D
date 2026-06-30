package ar.edu.itba.simped.core;

import ar.edu.itba.simped.core.ports.TaskTarget;

import java.util.List;

/**
 * Entrada del plan de un agente (4.2.a). Implementación original del Grupo 2.
 */
public final class Task implements TaskTarget {

    private TaskStatus status;
    private final TaskType type;
    private final Vec2 target; //TODO: Swap with TargetLocation class when location works
    /** Planta del target (0 = planta baja). La SM la usa para el footTarget 3D. */
    private final double z;
    private final String targetRef;
    private final double dwellSeconds;
    private final Segment exitSegment;
    private final List<Vec2> locationCandidates;
    /**
     * Planta de cada candidato de un location-group (paralela a
     * {@link #locationCandidates}). Permite que un grupo abarque varias plantas
     * (p. ej. aulas en PB y P1): al resolver el candidato, {@link #z()} pasa a
     * devolver la planta del candidato elegido, no la del primero. {@code null}
     * en tasks de target único.
     */
    private final List<Double> candidateZs;
    private final ObjectiveSelection locationSelection;
    private final long locationSelectionSeed;
    private Vec2 resolvedLocationTarget;
    /** Planta del candidato resuelto (location-group multiplanta); null si no resuelto. */
    private Double resolvedZ;

    public Task(
            TaskType type,
            Vec2 target,
            String targetRef,
            double dwellSeconds,
            Segment exitSegment,
            List<Vec2> locationCandidates,
            ObjectiveSelection locationSelection,
            long locationSelectionSeed,
            double z
    ) {
        this(type, target, targetRef, dwellSeconds, exitSegment, locationCandidates,
                locationSelection, locationSelectionSeed, z, null);
    }

    public Task(
            TaskType type,
            Vec2 target,
            String targetRef,
            double dwellSeconds,
            Segment exitSegment,
            List<Vec2> locationCandidates,
            ObjectiveSelection locationSelection,
            long locationSelectionSeed,
            double z,
            List<Double> candidateZs
    ) {
        this.status = TaskStatus.PENDING;
        this.type = type;
        this.target = target;
        this.z = z;
        this.targetRef = targetRef;
        this.dwellSeconds = dwellSeconds;
        this.exitSegment = exitSegment;
        this.locationCandidates = locationCandidates == null ? List.of() : List.copyOf(locationCandidates);
        this.candidateZs = candidateZs == null ? null : List.copyOf(candidateZs);
        this.locationSelection = locationSelection;
        this.locationSelectionSeed = locationSelectionSeed;
    }

    public Task(
            TaskType type,
            Vec2 target,
            String targetRef,
            double dwellSeconds,
            Segment exitSegment,
            List<Vec2> locationCandidates,
            ObjectiveSelection locationSelection,
            long locationSelectionSeed
    ) {
        this(type, target, targetRef, dwellSeconds, exitSegment,
                locationCandidates, locationSelection, locationSelectionSeed, 0.0);
    }

    public Task(TaskType type, Vec2 target, String targetRef, double dwellSeconds) {
        this(type, target, targetRef, dwellSeconds, null, List.of(), null, 0L, 0.0);
    }

    public Task(TaskType type, Vec2 target, String targetRef, double dwellSeconds, Segment exitSegment) {
        this(type, target, targetRef, dwellSeconds, exitSegment, List.of(), null, 0L, 0.0);
    }

    public static Task location(Vec2 target, double dwellSeconds) {
        return new Task(TaskType.LOCATION, target, null, dwellSeconds);
    }

    public static Task location(Vec2 target) {
        return new Task(TaskType.LOCATION, target, null, 0.0);
    }

    /** Location con planta explícita del target. */
    public static Task location(Vec2 target, double dwellSeconds, double z) {
        return new Task(TaskType.LOCATION, target, null, dwellSeconds, null, List.of(), null, 0L, z);
    }

    public static Task server(Vec2 serverPosition, String serverRef) {
        return new Task(TaskType.SERVER, serverPosition, serverRef, 0.0);
    }

    /** Server con planta explícita del target. */
    public static Task server(Vec2 serverPosition, String serverRef, double z) {
        return new Task(TaskType.SERVER, serverPosition, serverRef, 0.0, null, List.of(), null, 0L, z);
    }

    public static Task exit(Vec2 exitPosition) {
        return new Task(TaskType.EXIT, exitPosition, null, 0.0);
    }

    public static Task exit(Vec2 exitPosition, Segment exitSegment) {
        return new Task(TaskType.EXIT, exitPosition, null, 0.0, exitSegment);
    }

    /** Exit con planta explícita del target. */
    public static Task exit(Vec2 exitPosition, Segment exitSegment, double z) {
        return new Task(TaskType.EXIT, exitPosition, null, 0.0, exitSegment, List.of(), null, 0L, z);
    }

    public static Task locationGroup(
            String groupRef,
            List<Vec2> candidates,
            ObjectiveSelection selection,
            double dwellSeconds,
            long selectionSeed
    ) {
        return locationGroup(groupRef, candidates, selection, dwellSeconds, selectionSeed, 0.0);
    }

    public static Task locationGroup(
            String groupRef,
            List<Vec2> candidates,
            ObjectiveSelection selection,
            double dwellSeconds,
            long selectionSeed,
            double z
    ) {
        return locationGroup(groupRef, candidates, null, selection, dwellSeconds, selectionSeed, z);
    }

    /**
     * Location-group con la planta de cada candidato ({@code candidateZs} paralela
     * a {@code candidates}). Cuando el grupo abarca varias plantas, la {@code z}
     * efectiva ({@link #z()}) sigue al candidato resuelto. Si {@code candidateZs}
     * es null se usa la planta única {@code z} (comportamiento de una sola planta).
     */
    public static Task locationGroup(
            String groupRef,
            List<Vec2> candidates,
            List<Double> candidateZs,
            ObjectiveSelection selection,
            double dwellSeconds,
            long selectionSeed
    ) {
        double z = candidateZs == null || candidateZs.isEmpty() ? 0.0 : candidateZs.get(0);
        return locationGroup(groupRef, candidates, candidateZs, selection, dwellSeconds, selectionSeed, z);
    }

    private static Task locationGroup(
            String groupRef,
            List<Vec2> candidates,
            List<Double> candidateZs,
            ObjectiveSelection selection,
            double dwellSeconds,
            long selectionSeed,
            double z
    ) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("location group requires at least one candidate");
        }
        if (selection == null) {
            throw new IllegalArgumentException("location group requires a selection strategy");
        }
        return new Task(
                TaskType.LOCATION,
                candidates.get(0),
                groupRef,
                dwellSeconds,
                null,
                candidates,
                selection,
                selectionSeed,
                z,
                candidateZs
        );
    }

    public TaskType type() { return type; }
    public Vec2 target() { return resolvedLocationTarget != null ? resolvedLocationTarget : target; }
    /** Planta del target (0 = planta baja). En location-groups multiplanta, la del candidato resuelto. */
    public double z() { return resolvedZ != null ? resolvedZ : z; }
    public String targetRef() { return targetRef; }
    public double dwellSeconds() { return dwellSeconds; }
    public Segment exitSegment() { return exitSegment; }
    public List<Vec2> locationCandidates() { return locationCandidates; }
    public ObjectiveSelection locationSelection() { return locationSelection; }
    public long locationSelectionSeed() { return locationSelectionSeed; }
    public boolean hasLocationChoices() { return type == TaskType.LOCATION && !locationCandidates.isEmpty(); }

    /**
     * Fija el candidato elegido del location-group. Si el grupo trae planta por
     * candidato ({@code candidateZs}), también actualiza la planta efectiva para
     * que el footTarget viva en la planta del candidato resuelto (ruteo 3D).
     */
    public void resolveLocationTarget(Vec2 resolvedLocationTarget) {
        this.resolvedLocationTarget = resolvedLocationTarget;
        if (candidateZs != null) {
            int idx = locationCandidates.indexOf(resolvedLocationTarget);
            if (idx >= 0 && idx < candidateZs.size()) {
                this.resolvedZ = candidateZs.get(idx);
            }
        }
    }

    /**
     * Fija el candidato elegido por su <b>índice</b>. Es la forma correcta cuando
     * los candidatos comparten {@code (x,y)} y solo difieren en planta (aulas de
     * PB/P1 una sobre otra): el índice resuelve la planta sin ambigüedad.
     */
    public void resolveLocationTarget(int index) {
        if (index < 0 || index >= locationCandidates.size()) {
            return;
        }
        this.resolvedLocationTarget = locationCandidates.get(index);
        if (candidateZs != null && index < candidateZs.size()) {
            this.resolvedZ = candidateZs.get(index);
        }
    }
    public TaskStatus status() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
}
