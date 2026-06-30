package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Exit;
import ar.edu.itba.simped.core.Location;
import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.PlanStep;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.ServerZone;
import ar.edu.itba.simped.core.TaskStep;
import ar.edu.itba.simped.core.TaskType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.environment.geometry.GeometryImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Construye {@code Map<String, PlanTemplate>} resolviendo cada
 * {@link RawPlanStep} a un {@link PlanStep} con sus candidatos
 * ({@link Vec2} ya calculado) + la regla de selección. La elección concreta
 * (CLOSEST/RANDOM + cantidad) se hace por-agente en el {@code AgentAssembler}.
 *
 * <p>Resolución:
 * <ul>
 *   <li>{@link RawPlanStep.RawSingleStep} → PlanStep de 1 candidato (V17 si el
 *       block no existe en la layer del type).</li>
 *   <li>{@link RawPlanStep.RawGroupStep} → PlanStep con N candidatos (uno por
 *       block matching) + selección/cantidad (V17 si no hay matches).</li>
 *   <li>{@link RawPlanStep.RawAnyStep} → PlanStep con todos los blocks de la
 *       layer + selección (V17 si la layer está vacía).</li>
 * </ul>
 *
 * <p>Si una template queda sin steps resolvibles → V9.</p>
 */
public final class PlanTemplatesBuilder {

    private PlanTemplatesBuilder() {
    }

    public static Map<String, PlanTemplate> build(
            Map<String, List<RawPlanStep>> rawTemplates,
            GeometryImpl geometry,
            ErrorAccumulator acc) {

        Map<String, PlanTemplate> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<RawPlanStep>> entry : rawTemplates.entrySet()) {
            String templateName = entry.getKey();
            List<PlanStep> steps = new ArrayList<>();
            for (int i = 0; i < entry.getValue().size(); i++) {
                RawPlanStep raw = entry.getValue().get(i);
                String loc = "plan[" + templateName + "].steps[" + i + "]";
                expand(raw, geometry, loc, acc).ifPresent(steps::add);
            }
            if (steps.isEmpty()) {
                acc.add(ValidationCode.V9, "plan[" + templateName + "]",
                        "plan template has no resolvable steps");
                continue;
            }
            try {
                out.put(templateName, new PlanTemplate(templateName, steps));
            } catch (IllegalArgumentException e) {
                acc.add(ValidationCode.V9, "plan[" + templateName + "]", e.getMessage());
            }
        }
        return out;
    }

    private static Optional<PlanStep> expand(RawPlanStep raw, GeometryImpl g, String loc, ErrorAccumulator acc) {
        return switch (raw) {
            case RawPlanStep.RawSingleStep s -> expandSingle(s, g, loc, acc);
            case RawPlanStep.RawGroupStep gr -> expandGroup(gr, g, loc, acc);
            case RawPlanStep.RawAnyStep a -> expandAny(a, g, loc, acc);
        };
    }

    private static Optional<PlanStep> expandSingle(
            RawPlanStep.RawSingleStep s, GeometryImpl g, String loc, ErrorAccumulator acc) {
        // SERVER se referencia por GRUPO lógico (baseName). Normalizamos el ref al
        // baseName aunque el plan use el nombre completo (CASHIER_1_SERVER → CASHIER),
        // para que la SM delegue al grupo y el módulo reparta entre los miembros.
        if (s.type() == TaskType.SERVER) {
            String group = serverGroupOf(s.targetBlockName(), g);
            if (group == null) {
                acc.add(ValidationCode.V17, loc,
                        "target_block_name '" + s.targetBlockName() + "' not found for type SERVER");
                return Optional.empty();
            }
            return Optional.of(PlanStep.fixed(
                    new TaskStep(TaskType.SERVER, serverGroupPosition(group, g), serverGroupZ(group, g),
                            group, Optional.empty())));
        }
        if (s.type() == TaskType.EXIT) {
            Exit exit = g.exits().stream()
                    .filter(e -> e.blockName().equals(s.targetBlockName()))
                    .findFirst()
                    .orElse(null);
            if (exit == null) {
                acc.add(ValidationCode.V17, loc,
                        "target_block_name '" + s.targetBlockName() + "' not found for type EXIT");
                return Optional.empty();
            }
            return Optional.of(PlanStep.fixed(new TaskStep(
                    TaskType.EXIT,
                    exit.position(),
                    exit.z(),
                    exit.blockName(),
                    Optional.empty(),
                    Optional.of(exit.segment()))));
        }
        // LOCATION
        Location location = g.locations().stream()
                .filter(l -> l.blockName().equals(s.targetBlockName()))
                .findFirst()
                .orElse(null);
        if (location == null) {
            acc.add(ValidationCode.V17, loc,
                    "target_block_name '" + s.targetBlockName() + "' not found for type " + s.type());
            return Optional.empty();
        }
        return Optional.of(PlanStep.fixed(new TaskStep(
                TaskType.LOCATION, location.position(), location.z(),
                location.blockName(), Optional.empty())));
    }

    private static Optional<PlanStep> expandGroup(
            RawPlanStep.RawGroupStep gr, GeometryImpl g, String loc, ErrorAccumulator acc) {
        List<TaskStep> candidates = switch (gr.type()) {
            case LOCATION -> g.locations().stream()
                    .filter(l -> l.blockName().equals(gr.groupBlockName()))
                    .map(l -> new TaskStep(TaskType.LOCATION, l.position(), l.z(), l.blockName(), l.dwellTime()))
                    .toList();
            // SERVER: un único candidato = el grupo lógico (no uno por miembro);
            // la SM delega al grupo y el módulo reparte (softmax).
            case SERVER -> g.serverZones().stream().anyMatch(s -> s.baseName().equals(gr.groupBlockName()))
                    ? List.of(new TaskStep(TaskType.SERVER,
                            serverGroupPosition(gr.groupBlockName(), g), serverGroupZ(gr.groupBlockName(), g),
                            gr.groupBlockName(), Optional.empty()))
                    : List.<TaskStep>of();
            case EXIT -> g.exits().stream()
                    .filter(e -> e.blockName().equals(gr.groupBlockName()))
                    .map(e -> new TaskStep(TaskType.EXIT, e.position(), e.z(), e.blockName(),
                            Optional.empty(), Optional.of(e.segment())))
                    .toList();
        };
        if (candidates.isEmpty()) {
            acc.add(ValidationCode.V17, loc,
                    "group block_name '" + gr.groupBlockName() + "' has no matches for type " + gr.type());
            return Optional.empty();
        }
        return Optional.of(new PlanStep(gr.type(), gr.groupBlockName(), gr.selection(), gr.quantity(), candidates));
    }

    private static Optional<PlanStep> expandAny(
            RawPlanStep.RawAnyStep a, GeometryImpl g, String loc, ErrorAccumulator acc) {
        List<TaskStep> candidates = switch (a.type()) {
            case LOCATION -> g.locations().stream()
                    .map(l -> new TaskStep(TaskType.LOCATION, l.position(), l.z(), l.blockName(), l.dwellTime()))
                    .toList();
            // SERVER: un candidato por GRUPO lógico distinto (baseName).
            case SERVER -> g.serverZones().stream()
                    .map(ServerZone::baseName)
                    .distinct()
                    .map(bn -> new TaskStep(TaskType.SERVER, serverGroupPosition(bn, g), serverGroupZ(bn, g),
                            bn, Optional.empty()))
                    .toList();
            case EXIT -> g.exits().stream()
                    .map(e -> new TaskStep(TaskType.EXIT, e.position(), e.z(), e.blockName(),
                            Optional.empty(), Optional.of(e.segment())))
                    .toList();
        };
        if (candidates.isEmpty()) {
            acc.add(ValidationCode.V17, loc,
                    "no blocks of type " + a.type() + " in scenario (RawAnyStep)");
            return Optional.empty();
        }
        return Optional.of(new PlanStep(a.type(), "ANY_" + a.type(), a.selection(), Optional.empty(), candidates));
    }

    private static boolean matchesServer(ServerZone s, String blockName) {
        // PLANS.csv puede referenciar "CASHIER_1_SERVER" (full suffix) o "CASHIER" (base).
        String full = s.baseName() + "_" + s.id() + "_SERVER";
        return full.equals(blockName) || s.baseName().equals(blockName);
    }

    /** baseName del grupo lógico al que pertenece un ref de SERVER (acepta full
     *  name o base), o {@code null} si no matchea ningún server del escenario. */
    private static String serverGroupOf(String blockName, GeometryImpl g) {
        return g.serverZones().stream()
                .filter(s -> matchesServer(s, blockName))
                .map(ServerZone::baseName)
                .findFirst()
                .orElse(null);
    }

    /** Planta del grupo de servers (la del primer miembro; comparten planta). */
    private static double serverGroupZ(String baseName, GeometryImpl g) {
        return g.serverZones().stream()
                .filter(s -> s.baseName().equals(baseName))
                .mapToDouble(ServerZone::z)
                .findFirst()
                .orElse(0.0);
    }

    /** Posición representativa del grupo: centroide de las posiciones de sus
     *  miembros. (La SM usa el centroide del adapter como foot-target; esto es el
     *  fallback que viaja en el TaskStep.) */
    private static Vec2 serverGroupPosition(String baseName, GeometryImpl g) {
        List<Vec2> pts = g.serverZones().stream()
                .filter(s -> s.baseName().equals(baseName))
                .map(ServerZone::position)
                .toList();
        if (pts.isEmpty()) {
            return null;
        }
        double sx = 0;
        double sy = 0;
        for (Vec2 p : pts) {
            sx += p.x();
            sy += p.y();
        }
        return new Vec2(sx / pts.size(), sy / pts.size());
    }
}
