package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.ServerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test E2E del escenario i — vía pública (cruce con semáforos).
 * Verifica que el {@code type: "semaphore"} declarado en {@code parameters.json}
 * se respete (A5): sin campo {@code type} estos servers se inferían como
 * QUEUE/CLASSROOM y los peatones quedaban trabados.
 */
class ScenarioIViaPublicaSmokeTest {

    private static final Path SCENARIO_DIR = Path.of("scenarios/i-via-publica");

    static boolean fixtureExists() {
        return Files.isDirectory(SCENARIO_DIR)
                && Files.isRegularFile(SCENARIO_DIR.resolve("parameters.json"));
    }

    @Test
    @EnabledIf("fixtureExists")
    void semaphoreTypeIsRespectedOverInference() {
        LoadedScenario scenario = new ScenarioLoaderImpl().load(SCENARIO_DIR);

        assertThat(scenario.geometry().serverZones()).isNotEmpty();
        assertThat(scenario.geometry().serverZones())
                .allMatch(s -> s.baseName().startsWith("SEMAFORO"));
        assertThat(scenario.geometry().serverZones())
                .as("type declarado 'semaphore' debe respetarse, no inferirse")
                .allMatch(s -> s.type() == ServerType.SEMAPHORE);
    }
}
