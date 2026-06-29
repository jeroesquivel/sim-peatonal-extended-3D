package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.LegacyExtras;
import ar.edu.itba.simped.core.ObjectiveSelection;
import ar.edu.itba.simped.core.ServerParams;
import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.SimulationParameters;
import ar.edu.itba.simped.core.TaskType;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.input.json.DistributionConfig;
import ar.edu.itba.simped.input.json.GeneratorJson;
import ar.edu.itba.simped.input.json.ObjectiveGroupJson;
import ar.edu.itba.simped.input.json.ParametersJson;
import ar.edu.itba.simped.input.json.ParametersJsonReader;
import ar.edu.itba.simped.input.json.PlanJson;
import ar.edu.itba.simped.input.json.ServerJson;
import ar.edu.itba.simped.input.json.TargetJson;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Loader del Formato B — lee {@code parameters.json} y lo traduce a
 * {@link RawParams}.
 *
 * <p>Limitaciones vs Formato A:
 * <ul>
 *   <li>Formato B no tiene {@code dt} en JSON — se usa default
 *       {@code 0.05}.</li>
 *   <li>Server {@code type} no está en JSON — se setea
 *       {@link ServerType#CLASSROOM} como placeholder; el assembler
 *       aplica {@link CsvInferredServerTypeStrategy} usando las queues
 *       para decidir entre QUEUE y CLASSROOM. (Se puede declarar
 *       {@code "type"} explícito en el JSON para evitar la inferencia.)</li>
 *   <li>Plan templates: cada {@code objective_group} se traduce a
 *       {@link RawPlanStep.RawGroupStep} (toma TODOS los blocks
 *       matching); como exit final se agrega
 *       {@link RawPlanStep.RawAnyStep} (cualquier exit).
 *       {@code quantity_distribution} y {@code exit_selection} se
 *       ignoran.</li>
 * </ul>
 *
 * <p>Mapping del generador (iter 9):
 * <ul>
 *   <li>{@code mode} opcional en el JSON ({@code "flowrate"} o
 *       {@code "instant_occupation"}); se propaga a
 *       {@link GeneratorRawParams#mode()}. Inválido → V14.</li>
 *   <li>{@code rateOrCount} queda {@code empty()} en Formato B; el caudal
 *       efectivo lo deriva G6 en {@code App.effectiveFlowRatePerMin}
 *       desde {@code period + quantity_distribution} (commit {@code e5285e1}).</li>
 * </ul>
 *
 * <p>También expone dwell distributions por {@code blockName} de
 * Location vía {@link TargetDwellsAccumulator}, para que el assembler
 * complete los Location records.</p>
 */
public final class FormatBLoader {

    private static final double DEFAULT_DT = 0.05;

    private FormatBLoader() {
    }

    public static RawParams load(Path scenarioDir, ErrorAccumulator acc, TargetDwellsAccumulator dwellsOut) {
        Path jsonPath = scenarioDir.resolve("parameters.json");
        ParametersJson json = new ParametersJsonReader().read(jsonPath);

        SimulationParameters simParams = buildSimParams(json, acc);
        Map<String, GeneratorRawParams> generators = buildGenerators(json, acc);
        Map<String, ServerSpec> servers = buildServers(json, acc);
        Map<String, List<RawPlanStep>> plans = buildPlanTemplates(json, acc);
        LegacyExtras legacy = new LegacyExtras(
                json.evacuateAt() == null ? OptionalDouble.empty() : OptionalDouble.of(json.evacuateAt()),
                json.blueprintName() == null ? "" : json.blueprintName());

        if (json.targets() != null) {
            for (TargetJson t : json.targets()) {
                resolveDist(t.attendingTimeDistribution(),
                        "targets[" + t.blockName() + "].attending_time_distribution", acc)
                        .ifPresent(d -> dwellsOut.put(t.blockName(), d));
            }
        }

        return new RawParams(simParams, generators, servers, plans, Optional.of(legacy));
    }

    /** Accumulator out-param para dwell distributions. */
    public static final class TargetDwellsAccumulator {
        private final Map<String, Distribution> map = new LinkedHashMap<>();

        public void put(String blockName, Distribution d) {
            map.put(blockName, d);
        }

        public Map<String, Distribution> asMap() {
            return Map.copyOf(map);
        }
    }

    private static SimulationParameters buildSimParams(ParametersJson json, ErrorAccumulator acc) {
        try {
            return new SimulationParameters(DEFAULT_DT, json.outputDeltaTime(), json.maxTime());
        } catch (IllegalArgumentException e) {
            acc.add(ValidationCode.V13, "parameters.json", e.getMessage());
            return new SimulationParameters(DEFAULT_DT, 0.1, 1.0);
        }
    }

    private static Map<String, GeneratorRawParams> buildGenerators(ParametersJson json, ErrorAccumulator acc) {
        Map<String, GeneratorRawParams> out = new LinkedHashMap<>();
        if (json.agentsGenerators() == null) {
            return out;
        }
        for (int i = 0; i < json.agentsGenerators().size(); i++) {
            GeneratorJson g = json.agentsGenerators().get(i);
            String loc = "agents_generators[" + i + "]";

            Optional<Distribution> qty = resolveDist(
                    g.generation() == null ? null : g.generation().quantityDistribution(),
                    loc + ".generation.quantity_distribution", acc);
            Optional<Distribution> minR = g.agents() == null ? Optional.empty()
                    : resolveDist(g.agents().minRadiusDistribution(),
                            loc + ".agents.min_radius_distribution", acc);
            Optional<Distribution> maxR = g.agents() == null ? Optional.empty()
                    : resolveDist(g.agents().maxRadiusDistribution(),
                            loc + ".agents.max_radius_distribution", acc);

            // mode: opcional en JSON. Si está, debe ser "flowrate" o "instant_occupation"
            // (mismo conjunto que Formato A). Otro → V14. Se propaga a App, que decide
            // BATCH vs CALM según el valor.
            Optional<String> mode = Optional.empty();
            if (g.mode() != null && !g.mode().isBlank()) {
                String normalized = g.mode().trim().toLowerCase();
                if ("flowrate".equals(normalized) || "instant_occupation".equals(normalized)) {
                    mode = Optional.of(normalized);
                } else {
                    acc.add(ValidationCode.V14, loc + ".mode",
                            "mode must be 'flowrate' or 'instant_occupation', got '" + g.mode() + "'");
                }
            }

            // rateOrCount queda empty en Formato B: App.effectiveFlowRatePerMin lo deriva
            // de period + quantity_distribution (commit e5285e1, G6).

            try {
                out.put(g.blockName(), new GeneratorRawParams(
                        g.blockName(),  // agentType = blockName por convención iter 1
                        g.plan(),
                        mode,
                        OptionalDouble.empty(),
                        OptionalDouble.of(g.activeTime()),
                        OptionalDouble.of(g.inactiveTime()),
                        g.generation() == null
                                ? OptionalDouble.empty()
                                : OptionalDouble.of(g.generation().period()),
                        qty, minR, maxR,
                        g.agents() == null
                                ? OptionalDouble.empty()
                                : OptionalDouble.of(g.agents().maxVelocity())));
            } catch (IllegalArgumentException e) {
                acc.add(ValidationCode.V11, loc, e.getMessage());
            }
        }
        return out;
    }

    private static Map<String, ServerSpec> buildServers(ParametersJson json, ErrorAccumulator acc) {
        Map<String, ServerSpec> out = new LinkedHashMap<>();
        if (json.servers() == null) {
            return out;
        }
        for (int i = 0; i < json.servers().size(); i++) {
            ServerJson s = json.servers().get(i);
            String loc = "servers[" + i + "]";

            Optional<Distribution> serviceTime = resolveDist(s.attendingTimeDistribution(),
                    loc + ".attending_time_distribution", acc);
            try {
                ServerParams params = new ServerParams(
                        serviceTime,
                        OptionalDouble.of(s.maxCapacity()),
                        s.startTime() == null ? OptionalDouble.empty() : OptionalDouble.of(s.startTime()),
                        s.inactiveTime() == null ? OptionalDouble.empty() : OptionalDouble.of(s.inactiveTime()),
                        s.startTime() == null ? List.of() : List.of(s.startTime()),
                        s.greenDuration() == null ? OptionalDouble.empty() : OptionalDouble.of(s.greenDuration()));
                // Si el escenario declara 'type' lo respetamos (queue/semaphore/
                // classroom); si no, placeholder CLASSROOM e inferencia por queues
                // en el assembler (CsvInferredServerTypeStrategy).
                ServerSpec spec = parseServerType(s.type())
                        .map(t -> new ServerSpec(t, params))
                        .orElseGet(() -> new ServerSpec(ServerType.CLASSROOM, params, false));
                out.put(s.blockName(), spec);
            } catch (IllegalArgumentException e) {
                acc.add(ValidationCode.V11, loc, e.getMessage());
            }
        }
        return out;
    }

    private static Map<String, List<RawPlanStep>> buildPlanTemplates(ParametersJson json, ErrorAccumulator acc) {
        Map<String, List<RawPlanStep>> out = new LinkedHashMap<>();
        if (json.plans() == null) {
            return out;
        }
        for (PlanJson p : json.plans()) {
            List<RawPlanStep> steps = new ArrayList<>();
            if (p.objectiveGroups() != null) {
                for (int i = 0; i < p.objectiveGroups().size(); i++) {
                    ObjectiveGroupJson g = p.objectiveGroups().get(i);
                    TaskType type = layerToTaskType(g.layer());
                    if (type == null) {
                        continue;
                    }
                    // Selección y cantidad por objetivo. Default ALL (visita
                    // todos) si el escenario no especifica selección.
                    ObjectiveSelection sel =
                            ObjectiveSelection.fromString(g.objectiveSelection(), ObjectiveSelection.ALL);
                    // quantity_distribution es opcional: ausente → cantidad 1
                    // (resuelto por-agente). Solo lo validamos si viene.
                    Optional<Distribution> qty = Optional.empty();
                    if (g.quantityDistribution() != null) {
                        String loc = "plan[" + p.name()
                                + "].objective_groups[" + i + "].quantity_distribution";
                        qty = resolveDist(g.quantityDistribution(), loc, acc);
                    }
                    steps.add(new RawPlanStep.RawGroupStep(type, g.blockName(), sel, qty));
                }
            }
            // Exit final: una sola salida según exit_selection (default RANDOM;
            // visitar TODAS las salidas nunca es correcto).
            ObjectiveSelection exitSel =
                    ObjectiveSelection.fromString(p.exitSelection(), ObjectiveSelection.RANDOM);
            steps.add(new RawPlanStep.RawAnyStep(TaskType.EXIT, exitSel));
            out.put(p.name(), steps);
        }
        return out;
    }

    /** Mapea el campo {@code type} del server JSON a {@link ServerType}.
     *  {@code null}/desconocido → vacío (se infiere). */
    private static Optional<ServerType> parseServerType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return switch (raw.trim().toUpperCase()) {
            case "QUEUE" -> Optional.of(ServerType.QUEUE);
            case "SEMAPHORE" -> Optional.of(ServerType.SEMAPHORE);
            case "CLASSROOM" -> Optional.of(ServerType.CLASSROOM);
            default -> Optional.empty();
        };
    }

    private static TaskType layerToTaskType(String layer) {
        if (layer == null) {
            return null;
        }
        return switch (layer) {
            case "TARGETS" -> TaskType.LOCATION;
            case "SERVERS" -> TaskType.SERVER;
            default -> null;
        };
    }

    private static Optional<Distribution> resolveDist(DistributionConfig cfg, String loc, ErrorAccumulator acc) {
        return DistributionResolver.resolve(
                cfg == null ? null : cfg.type(),
                cfg == null ? null : cfg.min(),
                cfg == null ? null : cfg.max(),
                cfg == null ? null : cfg.mean(),
                cfg == null ? null : cfg.std(),
                loc, acc);
    }
}
