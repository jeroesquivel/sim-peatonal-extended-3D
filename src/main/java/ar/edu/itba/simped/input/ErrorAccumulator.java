package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Acumulador de errores de validación usado a lo largo del pipeline
 * del {@link ScenarioLoaderImpl}. Implementa el patrón
 * catch-and-continue: cada paso del pipeline agrega errores sin abortar;
 * al final, si hay errores se tira un {@link ScenarioValidationException}
 * agregada.
 */
public final class ErrorAccumulator {

    private final List<ValidationError> errors = new ArrayList<>();

    public void add(ValidationError error) {
        errors.add(error);
    }

    public void add(ValidationCode code, String location, String detail) {
        errors.add(new ValidationError(code, location, detail));
    }

    public void addAll(Collection<ValidationError> es) {
        errors.addAll(es);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int size() {
        return errors.size();
    }

    public List<ValidationError> errors() {
        return List.copyOf(errors);
    }

    public void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new ScenarioValidationException(errors);
        }
    }
}
