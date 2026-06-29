package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Smoke E2E del escenario e — Discoteca (consumido por G6). */
class ScenarioEDiscotecaSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/e-discoteca");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"))
                && Files.isRegularFile(SCENARIO_DIR.resolve("WALLS.csv"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsEDiscotecaEndToEnd() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

            assertThat(scenario.geometry().walls()).hasSize(6);
            // BARRA 6×1.5 → 6 pts; PISTA 15×10 → 150 pts. Total 156.
            assertThat(scenario.geometry().locations()).hasSize(156);
            assertThat(scenario.geometry().exits()).hasSize(2);
            assertThat(scenario.geometry().generatorZones()).hasSize(1);
            assertThat(scenario.geometry().serverZones()).hasSize(3);

            assertThat(scenario.geometry().serverZones())
                    .filteredOn(s -> s.baseName().equals("BANO_DISCO"))
                    .hasSize(2);
            assertThat(scenario.geometry().serverZones())
                    .filteredOn(s -> s.baseName().equals("CONTROL_ACCESO"))
                    .hasSize(1);

            assertThat(scenario.planTemplates()).containsKey("NOCHE_DISCO");
        }).doesNotThrowAnyException();
    }
}
