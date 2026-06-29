package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.LegacyExtras;
import ar.edu.itba.simped.core.SimulationParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Estructura intermedia interna producida por {@link FormatALoader} o
 * {@link FormatBLoader} con los parámetros del escenario en forma
 * uniforme, lista para ser consumida por el {@link GeometryAssembler}
 * y el {@link PlanTemplatesBuilder}.
 */
public record RawParams(
        SimulationParameters simParams,
        Map<String, GeneratorRawParams> generatorParamsByBlock,
        Map<String, ServerSpec> serverSpecsByBase,
        Map<String, List<RawPlanStep>> planTemplatesByName,
        Optional<LegacyExtras> legacy) {

    public RawParams {
        if (simParams == null
                || generatorParamsByBlock == null
                || serverSpecsByBase == null
                || planTemplatesByName == null
                || legacy == null) {
            throw new IllegalArgumentException("RawParams fields must be non-null (use empty collections / Optional.empty())");
        }
        generatorParamsByBlock = Map.copyOf(generatorParamsByBlock);
        serverSpecsByBase = Map.copyOf(serverSpecsByBase);
        planTemplatesByName = Map.copyOf(planTemplatesByName);
    }
}
