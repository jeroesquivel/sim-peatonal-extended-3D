package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.ServerType;
import ar.edu.itba.simped.core.ServerZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el loader entiende el formato canónico de SERVER_PARAMS.csv
 * {@code block_name, type, server_time_param, t_init}: tipos explícitos
 * (queue/semaphore/classroom) y sesiones de classroom (una fila por sesión,
 * t_init poblado) capturadas en {@code ServerParams.sessionStarts}.
 */
class ServerParamsFormatTest {

    private static final Path EXAMPLE_DIR = Paths.get("scenarios/example");

    static boolean fixtureExists() {
        return Files.isDirectory(EXAMPLE_DIR);
    }

    @Test
    @EnabledIf("fixtureExists")
    void parsesExplicitTypesAndClassroomSessions() {
        LoadedScenario scenario = new ScenarioLoaderImpl().load(EXAMPLE_DIR);

        ServerZone cashier = serverByBase(scenario, "CASHIER");
        assertThat(cashier.type()).isEqualTo(ServerType.QUEUE);
        assertThat(cashier.params().serviceTime()).isPresent();
        // queue no tiene t_init: sin sesiones.
        assertThat(cashier.params().sessionStarts()).isEmpty();

        ServerZone presentation = serverByBase(scenario, "PRESENTATION");
        assertThat(presentation.type()).isEqualTo(ServerType.CLASSROOM);
        assertThat(presentation.params().serviceTime()).isPresent();
        // classroom: una fila por sesión, t_init poblado.
        assertThat(presentation.params().sessionStarts()).contains(0.0, 45.0, 60.0);
    }

    private static ServerZone serverByBase(LoadedScenario scenario, String base) {
        return scenario.geometry().serverZones().stream()
                .filter(z -> z.baseName().equals(base))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ServerZone con base " + base));
    }
}
