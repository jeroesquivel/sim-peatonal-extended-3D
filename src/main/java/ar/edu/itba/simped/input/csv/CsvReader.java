package ar.edu.itba.simped.input.csv;

import ar.edu.itba.simped.core.validation.ScenarioValidationException;
import ar.edu.itba.simped.core.validation.ValidationCode;
import ar.edu.itba.simped.core.validation.ValidationError;
import ar.edu.itba.simped.input.ErrorAccumulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base abstracta para readers de CSV. Soporta row-level
 * catch-and-continue: una fila mal formada agrega V2 al accumulator
 * y la lectura sigue con las buenas.
 *
 * <p>Dos firmas:
 * <ul>
 *   <li>{@link #read(Path)}: conveniencia que tira
 *       {@link ScenarioValidationException} al final si hay errores.</li>
 *   <li>{@link #read(Path, ErrorAccumulator)}: acumula V1/V2 en el
 *       caller's accumulator; devuelve filas buenas.</li>
 * </ul>
 */
public abstract class CsvReader<T> {

    public final List<T> read(Path path) {
        ErrorAccumulator acc = new ErrorAccumulator();
        List<T> rows = read(path, acc);
        acc.throwIfAny();
        return rows;
    }

    public final List<T> read(Path path, ErrorAccumulator acc) {
        List<T> rows = new ArrayList<>();
        int dataLines = 0;
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) {
                if (!allowEmpty()) {
                    acc.add(emptyLayerError(path));
                }
                return rows;
            }
            int lineNumber = 1;
            String line;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                dataLines++;
                List<String> tokens = CsvLine.parse(line);
                try {
                    rows.add(parseRow(tokens, lineNumber, path));
                } catch (ScenarioValidationException e) {
                    acc.addAll(e.errors());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
        if (dataLines == 0 && !allowEmpty()) {
            acc.add(emptyLayerError(path));
        }
        return rows;
    }

    protected abstract T parseRow(List<String> tokens, int lineNumber, Path path);

    protected abstract String layerName();

    /**
     * Si {@code true}, una layer sin filas de datos NO es error (V1).
     * Default {@code false}. Lo overridean las layers opcionales —p.ej.
     * {@code TARGETS} en escenarios de tránsito puro (generador→server→salida
     * sin waypoints intermedios).
     */
    protected boolean allowEmpty() {
        return false;
    }

    protected ScenarioValidationException unsupportedEntityException(Path path, int lineNumber, String detail) {
        return new ScenarioValidationException(List.of(
                new ValidationError(ValidationCode.V2,
                        path.getFileName() + ":" + lineNumber,
                        "layer " + layerName() + ": " + detail)));
    }

    private ValidationError emptyLayerError(Path path) {
        return new ValidationError(ValidationCode.V1,
                path.getFileName() == null ? path.toString() : path.getFileName().toString(),
                "layer " + layerName() + " is empty (no data rows)");
    }
}
