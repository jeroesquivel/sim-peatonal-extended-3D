package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Smoke test E2E del escenario c — Terminal de Tren (consumido por G9).
 */
class ScenarioCTerminalTrenSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/c-terminal-tren");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"))
                && Files.isRegularFile(SCENARIO_DIR.resolve("WALLS.csv"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsCTerminalTrenEndToEnd() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

            assertThat(scenario.geometry().walls()).hasSize(6);
            // 3 KIOSCOs de 4×1 m² > 2 m² → gridificados a 4×1=4 pts c/u = 12.
            assertThat(scenario.geometry().locations()).hasSize(12);
            assertThat(scenario.geometry().exits()).hasSize(1);
            assertThat(scenario.geometry().generatorZones()).hasSize(1);
            assertThat(scenario.geometry().serverZones()).hasSize(3);

            assertThat(scenario.geometry().locations())
                    .allMatch(l -> l.blockName().equals("KIOSCO"));
            assertThat(scenario.geometry().exits())
                    .allMatch(e -> e.blockName().equals("ANDEN"));

            assertThat(scenario.planTemplates()).containsKey("VIAJE_TERMINAL");
        }).doesNotThrowAnyException();
    }
}
