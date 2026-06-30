package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

final class CsvParse {

    private static final Logger LOG = Logger.getLogger(CsvParse.class.getName());

    private CsvParse() {
    }

    /**
     * Parsea la planta {@code z} de un elemento de geometría planar a partir de
     * sus dos columnas {@code z1}/{@code z2} (ver D3). Para un elemento plano
     * ambas deben coincidir; si difieren se emite un warning y se usa {@code z1}
     * (las escaleras —único caso con {@code z1 != z2}— van en STAIRS.csv).
     */
    static double parseFloorZ(String z1Token, String z2Token, Path path, int lineNumber, String layer) {
        double z1 = parseDouble(z1Token, path, lineNumber, layer);
        double z2 = parseDouble(z2Token, path, lineNumber, layer);
        if (Math.abs(z1 - z2) > 1e-9) {
            LOG.warning(String.format(
                    "layer %s %s:%d: elemento planar con z1=%.3f != z2=%.3f; usando z1. "
                            + "(Las escaleras se declaran en STAIRS.csv)",
                    layer, path.getFileName(), lineNumber, z1, z2));
        }
        return z1;
    }

    static double parseDouble(String token, Path path, int lineNumber, String layer) {
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new ScenarioValidationException(List.of(
                    new ValidationError(ValidationCode.V2,
                            path.getFileName() + ":" + lineNumber,
                            "layer " + layer + ": not a number: '" + token + "'")));
        }
    }

    static int parseInt(String token, Path path, int lineNumber, String layer) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new ScenarioValidationException(List.of(
                    new ValidationError(ValidationCode.V2,
                            path.getFileName() + ":" + lineNumber,
                            "layer " + layer + ": not an integer: '" + token + "'")));
        }
    }
}
