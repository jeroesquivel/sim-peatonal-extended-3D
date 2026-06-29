package ar.edu.itba.simped.core;

import java.util.OptionalDouble;

/**
 * Campos extra que vienen del Formato B ({@code parameters.json}) y
 * que no tienen counterpart en Formato A. Quedan expuestos como
 * opcionales via {@code LoadedScenario.legacy()} para que el consumer
 * los use solo si están presentes.
 *
 * @param evacuateAt     instante (s) de inicio de la evacuación
 *                       (vacío si Formato A o si el JSON no lo provee).
 * @param blueprintName  nombre del plano (string libre).
 */
public record LegacyExtras(OptionalDouble evacuateAt, String blueprintName) {

    public LegacyExtras {
        if (evacuateAt == null) {
            throw new IllegalArgumentException("evacuateAt must be OptionalDouble.empty(), not null");
        }
        if (blueprintName == null) {
            throw new IllegalArgumentException("blueprintName required (use empty string if unknown)");
        }
    }
}
