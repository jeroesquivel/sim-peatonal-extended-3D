package ar.edu.itba.simped.core.validation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Excepción agregada lanzada por {@link
 * ar.edu.itba.simped.core.ports.ScenarioLoader#load} cuando la carga del
 * escenario encuentra uno o más errores. Acumula todos los errores
 * detectados en una sola pasada (catch-and-continue).
 */
public class ScenarioValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public ScenarioValidationException(List<ValidationError> errors) {
        super(formatMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }

    private static String formatMessage(List<ValidationError> errors) {
        if (errors.isEmpty()) {
            return "Scenario validation failed with no errors recorded (programming error).";
        }
        return "Scenario validation failed with " + errors.size() + " error(s):\n"
                + errors.stream()
                        .map(e -> "  - " + e)
                        .collect(Collectors.joining("\n"));
    }
}
