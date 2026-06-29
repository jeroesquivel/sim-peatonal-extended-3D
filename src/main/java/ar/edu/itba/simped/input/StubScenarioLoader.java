package ar.edu.itba.simped.input;

import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.ports.ScenarioLoader;

import java.nio.file.Path;

/**
 * STUB — implementación real en {@link ScenarioLoaderImpl}. Este stub
 * mantiene el contrato del port para no romper el wiring transitorio
 * de quien lo construya por reflexión o nombre.
 */
public final class StubScenarioLoader implements ScenarioLoader {

    @Override
    public LoadedScenario load(Path scenarioDir) {
        throw new UnsupportedOperationException(
                "Stub — use ar.edu.itba.simped.input.ScenarioLoaderImpl");
    }
}
