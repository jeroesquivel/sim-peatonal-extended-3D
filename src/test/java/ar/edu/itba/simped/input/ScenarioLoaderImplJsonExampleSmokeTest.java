package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Smoke test E2E del Formato B (JSON heredado): toma los 5 CSV de
 * geometría canónicos de {@code scenarios/example/} y los combina con
 * {@code tools/dxf-parser/parameters_example.json} en un escenario
 * temporal, después invoca el loader y verifica que carga sin errores.
 */
class ScenarioLoaderImplJsonExampleSmokeTest {

    private static final Path EXAMPLE_DIR = Path.of("scenarios/example");
    private static final Path JSON_EXAMPLE = Path.of("tools/dxf-parser/parameters_example.json");
    private static final String[] GEOM_CSVS = {
            "WALLS.csv", "TARGETS.csv", "EXITS.csv", "GENERATORS.csv", "SERVERS.csv"
    };

    static boolean fixturesExist() {
        if (!Files.isDirectory(EXAMPLE_DIR) || !Files.isRegularFile(JSON_EXAMPLE)) {
            return false;
        }
        for (String csv : GEOM_CSVS) {
            if (!Files.isRegularFile(EXAMPLE_DIR.resolve(csv))) {
                return false;
            }
        }
        return true;
    }

    @Test
    @EnabledIf("fixturesExist")
    void loadsFormatBScenarioFromJsonExampleWithoutErrors(@TempDir Path scenarioDir) throws IOException {
        for (String csv : GEOM_CSVS) {
            Files.copy(EXAMPLE_DIR.resolve(csv), scenarioDir.resolve(csv),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(JSON_EXAMPLE, scenarioDir.resolve("parameters.json"),
                StandardCopyOption.REPLACE_EXISTING);

        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(scenarioDir);

            assertThat(scenario.geometry().walls()).isNotEmpty();
            assertThat(scenario.geometry().locations()).isNotEmpty();
            assertThat(scenario.geometry().exits()).isNotEmpty();
            assertThat(scenario.geometry().generatorZones()).isNotEmpty();
            assertThat(scenario.geometry().serverZones()).isNotEmpty();
            assertThat(scenario.planTemplates()).isNotEmpty();

            // legacy presente con datos del JSON
            assertThat(scenario.legacy()).isPresent();
            assertThat(scenario.legacy().get().blueprintName())
                    .isEqualTo("Plano-prueba-simulacion-V05.02");
            assertThat(scenario.legacy().get().evacuateAt()).isPresent();
        }).doesNotThrowAnyException();
    }
}
