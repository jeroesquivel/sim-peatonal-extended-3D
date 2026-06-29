package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Junta varios {@link PedestrianGenerator} (uno por zona/entrada del escenario)
 * en uno solo. {@link #spawnInitial()} y {@link #spawnTick} concatenan los
 * agentes que produce cada sub-generador.
 *
 * <p>Permite que cada entrada tenga su propio modo (CALM/BATCH), su propio
 * caudal y su propio plan, en vez de un único generador global. Los sub-
 * generadores deben repartir IDs únicos entre sí (lo resuelve el
 * {@link WiredPedestrianGenerator} con un contador compartido).</p>
 */
public final class CompositePedestrianGenerator implements PedestrianGenerator {

    private final List<PedestrianGenerator> generators;

    public CompositePedestrianGenerator(List<PedestrianGenerator> generators) {
        this.generators = List.copyOf(generators);
    }

    @Override
    public List<Agent> spawnInitial() {
        List<Agent> all = new ArrayList<>();
        for (PedestrianGenerator g : generators) {
            all.addAll(g.spawnInitial());
        }
        return all;
    }

    @Override
    public List<Agent> spawnTick(double currentTime, double dt) {
        List<Agent> all = new ArrayList<>();
        for (PedestrianGenerator g : generators) {
            all.addAll(g.spawnTick(currentTime, dt));
        }
        return all;
    }
}
