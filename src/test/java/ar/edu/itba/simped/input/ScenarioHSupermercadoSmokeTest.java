package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Smoke test E2E del escenario h — Supermercado (consumido por G3).
 * Carga {@code scenarios/h-supermercado/} con Formato B (DXF + 5 CSVs
 * + parameters.json) y verifica volumen + plan template.
 */
class ScenarioHSupermercadoSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/h-supermercado");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"))
                && Files.isRegularFile(SCENARIO_DIR.resolve("WALLS.csv"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsHSupermercadoEndToEnd() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

            assertThat(scenario.geometry().walls()).hasSize(6);
            // 5 GONDOLAs de 4×0.8 m² > 2 m² → gridificadas. height < spacing → 1 fila
            // de 4 pts c/u = 20 puntos en total.
            assertThat(scenario.geometry().locations()).hasSize(20);
            assertThat(scenario.geometry().exits()).hasSize(2);
            assertThat(scenario.geometry().generatorZones()).hasSize(1);
            assertThat(scenario.geometry().serverZones()).hasSize(3);

            assertThat(scenario.geometry().locations())
                    .allMatch(l -> l.blockName().equals("GONDOLA"));
            assertThat(scenario.geometry().serverZones())
                    .allMatch(s -> s.baseName().equals("CAJA"));
            assertThat(scenario.geometry().serverZones())
                    .allMatch(s -> s.queues().size() == 1);

            assertThat(scenario.planTemplates()).containsKey("COMPRA");
            assertThat(scenario.planTemplates().get("COMPRA").steps()).isNotEmpty();
        }).doesNotThrowAnyException();
    }
}
