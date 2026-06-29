package ar.edu.itba.simped.core.ports;

import ar.edu.itba.simped.core.LoadedScenario;

import java.nio.file.Path;

/**
 * Módulo 1 del contract v4 (UserInput).
 *
 * <p>Fuente externa de definición de escenario. Carga geometría y plan
 * templates antes del inicio de la simulación.</p>
 *
 * <p>Interfaces cubiertas:
 * <ul>
 *   <li>I1: provee definición de geometría a {@link Geometry}.</li>
 *   <li>I2: provee templates a {@link Plan}.</li>
 * </ul>
 * </p>
 *
 * <p>La impl real es {@code ScenarioLoaderImpl} (G3, en {@code input/}):
 * lee los 9 CSV del directorio (5 de geometría + 4 de parámetros) y
 * tira {@link ar.edu.itba.simped.core.validation.ScenarioValidationException}
 * agregada si hay errores.</p>
 */
public interface ScenarioLoader {

    LoadedScenario load(Path scenarioDir);
}
