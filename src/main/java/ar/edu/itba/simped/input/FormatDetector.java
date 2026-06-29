package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.validation.ValidationCode;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detecta el formato del directorio de escenario.
 *
 * <ul>
 *   <li>{@link Format#FORMAT_A} — monorepo canónico: 5 CSV de geometría
 *       + {@code SIM_PARAMS.csv} + {@code GENERATOR_PARAMS.csv} +
 *       {@code SERVER_PARAMS.csv} + {@code PLANS.csv}.</li>
 *   <li>{@link Format#FORMAT_B} — JSON heredado: 5 CSV de geometría +
 *       {@code parameters.json} único.</li>
 * </ul>
 *
 * <p>Si coexisten ambos, dispara V20 y asume A.</p>
 */
public final class FormatDetector {

    private FormatDetector() {
    }

    public enum Format { FORMAT_A, FORMAT_B }

    public static Format detect(Path dir, ErrorAccumulator acc) {
        boolean hasJson = Files.exists(dir.resolve("parameters.json"));
        boolean hasFormatA = Files.exists(dir.resolve("SIM_PARAMS.csv"))
                || Files.exists(dir.resolve("GENERATOR_PARAMS.csv"))
                || Files.exists(dir.resolve("SERVER_PARAMS.csv"))
                || Files.exists(dir.resolve("PLANS.csv"));

        if (hasJson && hasFormatA) {
            acc.add(ValidationCode.V20, dir.toString(),
                    "directory contains both parameters.json (Format B) and Format A CSVs; choose one");
            return Format.FORMAT_A;
        }
        if (hasJson) {
            return Format.FORMAT_B;
        }
        return Format.FORMAT_A;
    }
}
