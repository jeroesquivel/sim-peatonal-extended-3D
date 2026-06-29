package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Smoke E2E del escenario b — Paseo de compras (consumido por G8). */
class ScenarioBPaseoComprasSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/b-paseo-compras");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"))
                && Files.isRegularFile(SCENARIO_DIR.resolve("WALLS.csv"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsBPaseoComprasEndToEnd() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

            assertThat(scenario.geometry().walls()).hasSize(6);
            // 8 VITRINAs + 12 mesas de PATIO_COMIDAS (ahora target con dwell, ya no server) = 20.
            assertThat(scenario.geometry().locations()).hasSize(20);
            assertThat(scenario.geometry().locations())
                    .filteredOn(l -> l.blockName().equals("PATIO_COMIDAS"))
                    .hasSize(12);
            assertThat(scenario.geometry().exits()).hasSize(1);
            assertThat(scenario.geometry().generatorZones()).hasSize(1);
            assertThat(scenario.geometry().serverZones()).hasSize(2);

            assertThat(scenario.geometry().serverZones())
                    .filteredOn(s -> s.baseName().equals("BANO"))
                    .hasSize(2);

            assertThat(scenario.planTemplates()).containsKey("PASEO");
        }).doesNotThrowAnyException();
    }
}
