package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Gaussian;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.validation.ValidationCode;

import java.util.Optional;

/**
 * Convierte distribuciones parseadas (raw) a tipos {@link Distribution}
 * de domain, aplicando catch-and-continue para V10 (type desconocido) y
 * V11 (distribución mal formada).
 *
 * <p>Firma sobre primitivas (Double nullable) para no depender todavía
 * de {@code DistributionConfig} (que vive en {@code input/json/} y se
 * agrega en T32). El consumer (Format A/B loader) desestructura los
 * raw values y llama acá.</p>
 */
public final class DistributionResolver {

    private DistributionResolver() {
    }

    public static Optional<Distribution> resolve(
            String type, Double min, Double max, Double mean, Double std,
            String location, ErrorAccumulator acc) {

        if (type == null) {
            acc.add(ValidationCode.V10, location, "distribution missing 'type' field");
            return Optional.empty();
        }
        try {
            return switch (type) {
                case "UNIFORM" -> Optional.of(new Uniform(
                        requireField(min, "min", type),
                        requireField(max, "max", type)));
                case "GAUSSIAN" -> {
                    double m = requireField(mean, "mean", type);
                    double s = requireField(std, "std", type);
                    yield s == 0.0
                            ? Optional.of(new Deterministic(m))
                            : Optional.of(new Gaussian(m, s));
                }
                default -> {
                    acc.add(ValidationCode.V10, location,
                            "unsupported distribution type '" + type
                                    + "'; supported: UNIFORM, GAUSSIAN");
                    yield Optional.<Distribution>empty();
                }
            };
        } catch (IllegalArgumentException e) {
            acc.add(ValidationCode.V11, location, e.getMessage());
            return Optional.empty();
        }
    }

    private static double requireField(Double value, String field, String type) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "distribution[type=" + type + "] missing required field '" + field + "'");
        }
        return value;
    }
}
