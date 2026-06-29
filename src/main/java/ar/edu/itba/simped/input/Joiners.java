package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.input.csv.GeneratorsCsvRow;
import ar.edu.itba.simped.input.csv.ServersCsvRow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cruza {@link RawParams} con la geometría leída para detectar
 * referencias huérfanas:
 *
 * <ul>
 *   <li>V4: bloque en {@code GENERATOR_PARAMS.csv} o
 *       {@code agents_generators[]} sin counterpart en
 *       {@code GENERATORS.csv}.</li>
 *   <li>V5 / V19: bloque de server en params sin counterpart en
 *       {@code SERVERS.csv}. V5 para Formato B
 *       ({@code servers[]}), V19 para Formato A
 *       ({@code SERVER_PARAMS.csv}).</li>
 *   <li>V8 / V18: {@code plan_template} referenciado por un generator
 *       que no existe en los templates resueltos. V8 para Formato B,
 *       V18 para Formato A.</li>
 * </ul>
 *
 * <p>El opuesto (CSV-side block sin counterpart en params) lo maneja
 * el {@link GeometryAssembler} silenciosamente (skip).</p>
 */
public final class Joiners {

    private Joiners() {
    }

    public static void validate(
            RawParams params,
            List<GeneratorsCsvRow> generatorRows,
            List<ServersCsvRow> serverRows,
            FormatDetector.Format format,
            ErrorAccumulator acc) {

        validateGenerators(params, generatorRows, acc);
        validateServers(params, serverRows, format, acc);
        validatePlanReferences(params, format, acc);
    }

    private static void validateGenerators(
            RawParams params, List<GeneratorsCsvRow> rows, ErrorAccumulator acc) {
        Set<String> csvBlocks = new LinkedHashSet<>();
        for (GeneratorsCsvRow r : rows) {
            csvBlocks.add(r.blockName());
        }
        for (String paramBlock : params.generatorParamsByBlock().keySet()) {
            if (!csvBlocks.contains(paramBlock)) {
                acc.add(ValidationCode.V4,
                        "generator_params[" + paramBlock + "]",
                        "no GENERATORS.csv block matches block_name '" + paramBlock + "'");
            }
        }
    }

    private static void validateServers(
            RawParams params,
            List<ServersCsvRow> rows,
            FormatDetector.Format format,
            ErrorAccumulator acc) {
        Set<String> csvBases = new LinkedHashSet<>();
        for (ServersCsvRow r : rows) {
            if (r instanceof ServersCsvRow.ServerRow) {
                csvBases.add(r.base());
            }
        }
        ValidationCode code = format == FormatDetector.Format.FORMAT_A
                ? ValidationCode.V19
                : ValidationCode.V5;
        for (String paramBase : params.serverSpecsByBase().keySet()) {
            if (!csvBases.contains(paramBase)) {
                acc.add(code,
                        "server_params[" + paramBase + "]",
                        "no SERVERS.csv _SERVER counterpart for base '" + paramBase + "'");
            }
        }
    }

    private static void validatePlanReferences(
            RawParams params, FormatDetector.Format format, ErrorAccumulator acc) {
        Set<String> templateNames = params.planTemplatesByName().keySet();
        ValidationCode code = format == FormatDetector.Format.FORMAT_A
                ? ValidationCode.V18
                : ValidationCode.V8;
        for (Map.Entry<String, GeneratorRawParams> e : params.generatorParamsByBlock().entrySet()) {
            // plan_template puede ser un pool separado por '|' (el generador
            // elige uno al azar por agente). Validamos cada nombre del pool.
            for (String planRef : e.getValue().planTemplateName().split("\\|")) {
                if (!templateNames.contains(planRef.trim())) {
                    acc.add(code,
                            "generator_params[" + e.getKey() + "].plan_template",
                            "plan_template '" + planRef.trim() + "' not defined");
                }
            }
        }
    }
}
