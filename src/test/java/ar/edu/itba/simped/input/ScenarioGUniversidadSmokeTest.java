package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Smoke E2E del escenario g — Universidad (consumido por G4). */
class ScenarioGUniversidadSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/g-universidad");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"))
                && Files.isRegularFile(SCENARIO_DIR.resolve("WALLS.csv"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsGUniversidadEndToEnd() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

            assertThat(scenario.geometry().walls()).hasSize(6);
            // KIOSCO_UNI 3×2 m² → gridificado con spacing 1.0 a 3×2=6 puntos.
            assertThat(scenario.geometry().locations()).hasSize(6);
            assertThat(scenario.geometry().exits()).hasSize(2);
            assertThat(scenario.geometry().generatorZones()).hasSize(1);
            assertThat(scenario.geometry().serverZones()).hasSize(4);

            assertThat(scenario.geometry().serverZones())
                    .filteredOn(s -> s.baseName().equals("AULA"))
                    .hasSize(2)
                    .allMatch(s -> s.queues().isEmpty());

            assertThat(scenario.planTemplates()).containsKey("DIA_UNIVERSITARIO");
        }).doesNotThrowAnyException();
    }
}
