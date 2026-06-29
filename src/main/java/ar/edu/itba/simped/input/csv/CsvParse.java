package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;

import java.nio.file.Path;
import java.util.List;

final class CsvParse {

    private CsvParse() {
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
