package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.SimulationParameters;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.input.ErrorAccumulator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reader del Formato A {@code SIM_PARAMS.csv}: {@code key, value}.
 * Keys aceptadas: {@code dt}, {@code dt_out}, {@code t_total}. Unknown
 * keys disparan V13. Missing keys disparan V13 también.
 */
public final class SimParamsCsvReader extends CsvReader<SimParamRow> {

    private static final Set<String> VALID_KEYS = Set.of("dt", "dt_out", "t_total");

    @Override
    protected SimParamRow parseRow(List<String> tokens, int lineNumber, Path path) {
        if (tokens.size() != 2) {
            throw unsupportedEntityException(path, lineNumber,
                    "expected 2 columns (key, value), got " + tokens.size());
        }
        return new SimParamRow(tokens.get(0), tokens.get(1));
    }

    @Override
    protected String layerName() {
        return "SIM_PARAMS";
    }

    /**
     * Construye {@link SimulationParameters} a partir de las filas
     * leídas. Acumula V13 para keys desconocidas o faltantes.
     */
    public Optional<SimulationParameters> toSimulationParameters(
            List<SimParamRow> rows, Path source, ErrorAccumulator acc) {
        Map<String, String> kv = new HashMap<>();
        String loc = source.getFileName().toString();
        for (SimParamRow row : rows) {
            if (!VALID_KEYS.contains(row.key())) {
                acc.add(ValidationCode.V13, loc,
                        "unknown key '" + row.key() + "'; valid: " + VALID_KEYS);
                continue;
            }
            kv.put(row.key(), row.value());
        }
        Double dt = parseRequired(kv, "dt", loc, acc);
        Double dtOut = parseRequired(kv, "dt_out", loc, acc);
        Double tTotal = parseRequired(kv, "t_total", loc, acc);
        if (dt == null || dtOut == null || tTotal == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SimulationParameters(dt, dtOut, tTotal));
        } catch (IllegalArgumentException e) {
            acc.add(ValidationCode.V13, loc, e.getMessage());
            return Optional.empty();
        }
    }

    private Double parseRequired(Map<String, String> kv, String key, String loc, ErrorAccumulator acc) {
        String v = kv.get(key);
        if (v == null) {
            acc.add(ValidationCode.V13, loc, "missing required key '" + key + "'");
            return null;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            acc.add(ValidationCode.V13, loc,
                    "key '" + key + "' value '" + v + "' is not a number");
            return null;
        }
    }
}
