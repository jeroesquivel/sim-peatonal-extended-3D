package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Smoke test contra el fixture canónico de G6 en {@code scenarios/example/}.
 * Verifica que el loader puede cargar el escenario completo sin
 * excepciones. Si el fixture no está (otro entorno), el test se skipea.
 */
class ScenarioLoaderImplExampleSmokeTest {

    private static final Path EXAMPLE_DIR = Paths.get("scenarios/example");

    static boolean fixtureExists() {
        return Files.isDirectory(EXAMPLE_DIR);
    }

    @Test
    @EnabledIf("fixtureExists")
    void loadsCanonicalExampleScenarioWithoutErrors() {
        assertThatCode(() -> {
            LoadedScenario scenario = new ScenarioLoaderImpl().load(EXAMPLE_DIR);

            assertThat(scenario.geometry().walls()).isNotEmpty();
            assertThat(scenario.geometry().locations()).isNotEmpty();
            assertThat(scenario.geometry().exits()).isNotEmpty();
            assertThat(scenario.geometry().generatorZones()).isNotEmpty();
            assertThat(scenario.geometry().serverZones()).isNotEmpty();
            assertThat(scenario.planTemplates()).isNotEmpty();
            assertThat(scenario.simParams().dt()).isPositive();
            assertThat(scenario.simParams().tTotal()).isPositive();
        }).doesNotThrowAnyException();
    }
}
