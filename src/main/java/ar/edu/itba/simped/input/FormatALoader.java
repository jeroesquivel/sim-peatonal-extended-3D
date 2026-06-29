package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.ServerParams;
import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.SimulationParameters;
import ar.edu.itba.simped.input.csv.GeneratorParamsCsvReader;
import ar.edu.itba.simped.input.csv.GeneratorParamsRow;
import ar.edu.itba.simped.input.csv.ParsedServerName;
import ar.edu.itba.simped.input.csv.PlanStepRow;
import ar.edu.itba.simped.input.csv.PlansCsvReader;
import ar.edu.itba.simped.input.csv.ServerKind;
import ar.edu.itba.simped.input.csv.ServerParamsCsvReader;
import ar.edu.itba.simped.input.csv.ServerParamsRow;
import ar.edu.itba.simped.input.csv.ServersBlockNameParser;
import ar.edu.itba.simped.input.csv.SimParamRow;
import ar.edu.itba.simped.input.csv.SimParamsCsvReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Lee los 4 CSV de parámetros del Formato A (SIM_PARAMS,
 * GENERATOR_PARAMS, SERVER_PARAMS, PLANS) y los empaqueta en
 * {@link RawParams}.
 */
public final class FormatALoader {

    private FormatALoader() {
    }

    public static RawParams load(Path scenarioDir, ErrorAccumulator acc) {
        SimulationParameters simParams = readSimParams(scenarioDir, acc);
        Map<String, GeneratorRawParams> generators = readGenerators(scenarioDir, acc);
        Map<String, ServerSpec> servers = readServers(scenarioDir, acc);
        Map<String, List<RawPlanStep>> plans = readPlans(scenarioDir, acc);
        return new RawParams(simParams, generators, servers, plans, Optional.empty());
    }

    private static SimulationParameters readSimParams(Path dir, ErrorAccumulator acc) {
        Path path = dir.resolve("SIM_PARAMS.csv");
        SimParamsCsvReader reader = new SimParamsCsvReader();
        List<SimParamRow> rows = reader.read(path, acc);
        return reader.toSimulationParameters(rows, path, acc).orElse(fallbackSimParams());
    }

    private static SimulationParameters fallbackSimParams() {
        // Sentinel para no abortar la lectura del resto si SIM_PARAMS falla;
        // los errores ya quedaron en el accumulator y el caller va a tirar.
        return new SimulationParameters(0.1, 0.1, 1.0);
    }

    private static Map<String, GeneratorRawParams> readGenerators(Path dir, ErrorAccumulator acc) {
        List<GeneratorParamsRow> rows = new GeneratorParamsCsvReader()
                .read(dir.resolve("GENERATOR_PARAMS.csv"), acc);
        Map<String, GeneratorRawParams> out = new LinkedHashMap<>();
        for (GeneratorParamsRow row : rows) {
            out.put(row.blockName(), new GeneratorRawParams(
                    row.agentType(),
                    row.planTemplate(),
                    Optional.of(row.mode()),
                    OptionalDouble.of(row.rateOrCount()),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    OptionalDouble.empty()));
        }
        return out;
    }

    private static Map<String, ServerSpec> readServers(Path dir, ErrorAccumulator acc) {
        List<ServerParamsRow> rows = new ServerParamsCsvReader()
                .read(dir.resolve("SERVER_PARAMS.csv"), acc);
        // Agrupar por block base: classroom puede traer varias filas (una por
        // sesión). Aceptar block_name=BASE (e.g. CASHIER) o BASE_id_SERVER.
        Map<String, List<ServerParamsRow>> byKey = new LinkedHashMap<>();
        for (ServerParamsRow row : rows) {
            String key = ServersBlockNameParser.parse(row.blockName())
                    .filter(p -> p.kind() == ServerKind.SERVER)
                    .map(ParsedServerName::base)
                    .orElse(row.blockName());
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        Map<String, ServerSpec> out = new LinkedHashMap<>();
        byKey.forEach((key, group) -> {
            ServerParamsRow first = group.get(0);
            ServerType type = first.type();
            // server_time_param (t_mean) se modela como Deterministic con el
            // valor crudo; G0 lo interpreta según el tipo de server.
            Optional<Distribution> serviceTime =
                    Optional.of(new Deterministic(first.serverTimeParam()));
            List<Double> sessionStarts = group.stream()
                    .map(ServerParamsRow::tInit)
                    .filter(OptionalDouble::isPresent)
                    .map(OptionalDouble::getAsDouble)
                    .toList();
            OptionalDouble startTime = sessionStarts.isEmpty()
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(sessionStarts.get(0));
            // green_duration: solo lo usa semaphore (una fila), del primer row.
            OptionalDouble greenDuration = first.greenDuration();
            ServerParams params = new ServerParams(
                    serviceTime,
                    OptionalDouble.empty(),
                    startTime,
                    OptionalDouble.empty(),
                    sessionStarts,
                    greenDuration);
            out.put(key, new ServerSpec(type, params));
        });
        return out;
    }

    private static Map<String, List<RawPlanStep>> readPlans(Path dir, ErrorAccumulator acc) {
        PlansCsvReader reader = new PlansCsvReader();
        List<PlanStepRow> rows = reader.read(dir.resolve("PLANS.csv"), acc);
        Map<String, List<PlanStepRow>> grouped = reader.groupByTemplate(rows);
        Map<String, List<RawPlanStep>> out = new LinkedHashMap<>();
        grouped.forEach((name, steps) -> {
            List<RawPlanStep> raw = steps.stream()
                    .map(s -> (RawPlanStep) new RawPlanStep.RawSingleStep(s.targetType(), s.targetBlockName()))
                    .toList();
            out.put(name, raw);
        });
        return out;
    }
}
