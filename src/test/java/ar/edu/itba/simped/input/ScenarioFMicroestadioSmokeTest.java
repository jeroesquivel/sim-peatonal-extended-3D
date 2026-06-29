package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Smoke E2E del escenario f — Microestadio (consumido por G2). */
class ScenarioFMicroestadioSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/f-microestadio");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"))
                && Files.isRegularFile(SCENARIO_DIR.resolve("WALLS.csv"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsFMicroestadioEndToEnd() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

            assertThat(scenario.geometry().walls()).hasSize(8);
            // CAMPO_RECITAL + HALL_RECITAL gridificados con spacing 1.0 → 415 puntos.
            assertThat(scenario.geometry().locations()).hasSize(415);
            assertThat(scenario.geometry().exits()).hasSize(4);
            assertThat(scenario.geometry().generatorZones()).hasSize(1);
            assertThat(scenario.geometry().serverZones()).hasSize(6);

            assertThat(scenario.geometry().serverZones())
                    .filteredOn(s -> s.baseName().equals("ESCENARIO"))
                    .hasSize(1)
                    .allMatch(s -> s.queues().isEmpty());

            assertThat(scenario.planTemplates()).containsKey("RECITAL");
        }).doesNotThrowAnyException();
    }
}
