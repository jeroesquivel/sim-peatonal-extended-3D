package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.Server;
import ar.edu.itba.simped.environment.servers.engine.ServersModule;
import ar.edu.itba.simped.input.ScenarioLoaderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link ServersWiring} instancia el {@code ServersModule} de G0
 * desde las server zones del escenario y expone un {@code core.ports.Server}
 * por GRUPO lógico (baseName, p.ej. {@code CASHIER}), no uno por miembro. La SM
 * delega al grupo (I13a) y el módulo reparte entre los miembros (CASHIER_1,
 * CASHIER_2) con softmax carga+distancia.
 */
class ServersWiringTest {

    private static final Path EXAMPLE_DIR = Paths.get("scenarios/example");

    static boolean fixtureExists() {
        return Files.isDirectory(EXAMPLE_DIR);
    }

    @Test
    @EnabledIf("fixtureExists")
    void instantiatesServersGroupedByBaseName() {
        LoadedScenario scenario = new ScenarioLoaderImpl().load(EXAMPLE_DIR);
        Map<Integer, Agent> registry = new HashMap<>();

        ServersModule module = ServersWiring.build(scenario.geometry().serverZones(), registry);
        List<Server> ports = module.ports();

        List<String> names = ports.stream().map(Server::name).toList();
        // Un puerto por GRUPO lógico (baseName), no por miembro: CASHIER_1/2 ->
        // un único "CASHIER"; PRESENTATION_1/2 -> "PRESENTATION".
        assertThat(names).contains("CASHIER", "PRESENTATION");
        assertThat(names).doesNotContain("CASHIER_1_SERVER", "PRESENTATION_1_SERVER");
        // Cada grupo expone una posición de servicio usable como foot-target.
        ports.forEach(s -> assertThat(s.position()).isNotNull());
    }

    @Test
    void queueLineFollowsScenarioSegmentWhenPresent() {
        // Zona QUEUE con segmento *_QUEUE000 explícito: la fila debe correr por
        // el segmento (frente en a, capacidad según el largo), no por el
        // default hacia el sur. Así el escenario controla que la fila no
        // atraviese paredes.
        var area = new ar.edu.itba.simped.core.Rectangle(
                new ar.edu.itba.simped.core.Vec2(1, 19),
                new ar.edu.itba.simped.core.Vec2(4, 10));
        var queueSeg = new ar.edu.itba.simped.core.Segment(
                new ar.edu.itba.simped.core.Vec2(4.0, 13.5),
                new ar.edu.itba.simped.core.Vec2(14.0, 13.5));
        var zone = new ar.edu.itba.simped.core.ServerZone(
                "CASHIER", 1, area, List.of(queueSeg),
                ar.edu.itba.simped.core.ServerType.QUEUE,
                ar.edu.itba.simped.core.ServerParams.empty());

        ServersModule module = ServersWiring.build(List.of(zone), new HashMap<>());

        var line = module.getServer(0).queueLine();
        assertThat(line.front().x()).isEqualTo(4.0);
        assertThat(line.front().y()).isEqualTo(13.5);
        assertThat(line.back().x()).isEqualTo(14.0);
        // Largo 10 m con slotSpacing 1.0 → 11 slots.
        assertThat(line.maxSlots()).isEqualTo(11);
    }
}
