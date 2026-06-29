package ar.edu.itba.simped.core;

import ar.edu.itba.simped.core.ports.Geometry;

import java.util.Map;
import java.util.Optional;

/**
 * Resultado de {@link ar.edu.itba.simped.core.ports.ScenarioLoader#load}.
 * Agrupa todo lo cargado desde el directorio de escenario: la
 * {@link Geometry} (read-only post-init), los parámetros de simulación,
 * los templates de plan resueltos a {@link Vec2} y los extras opcionales
 * del Formato B.
 *
 * <p>Consumido por G6 (SimulationLoop al init), G9 (PG por sus
 * generators + templates), G2 (templates), etc.</p>
 */
public record LoadedScenario(
        Geometry geometry,
        SimulationParameters simParams,
        Map<String, PlanTemplate> planTemplates,
        Optional<LegacyExtras> legacy) {

    public LoadedScenario {
        if (geometry == null) {
            throw new IllegalArgumentException("geometry required");
        }
        if (simParams == null) {
            throw new IllegalArgumentException("simParams required");
        }
        if (planTemplates == null) {
            throw new IllegalArgumentException("planTemplates required");
        }
        if (legacy == null) {
            throw new IllegalArgumentException("legacy must be Optional.empty(), not null");
        }
        planTemplates = Map.copyOf(planTemplates);
    }
}
