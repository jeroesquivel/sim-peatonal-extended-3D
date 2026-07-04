package ar.edu.itba.simped.environment.generator;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre el ciclo de spawn del generador, en particular que la ocupación
 * inicial (BATCH) no se duplique con el primer {@code spawnTick(0)} y que la
 * cantidad en modo BATCH sea literal (no se confunda con un caudal).
 */
class ConfigurablePedestrianGeneratorTest {

    private static final double DT = 0.05;
    // Zona amplia: que el anti-solapamiento no derive spawns a pendientes.
    private static final List<Rectangle> BIG_ZONE =
            List.of(new Rectangle(new Vec2(0, 0), new Vec2(40, 40)));
    private static final List<PlanTemplate> PLANS =
            List.of(new PlanTemplate("p", List.of()));

    private static ConfigurablePedestrianGenerator gen(
            double activeTime, double rateOrCount, GenerationMode mode) {
        return new ConfigurablePedestrianGenerator(
                "test", activeTime, 0.0, BIG_ZONE, rateOrCount, mode, PLANS, List::of, 0.0);
    }

    @Test
    void profileOverrideIsSetOnSpawnedAgents() {
        // D22: un generador con perfil propio (p. ej. crisis en evacuación)
        // debe parir agentes con ese profile ya seteado, para que el assembler
        // no les pise el default.
        AgentProfile crisis = new AgentProfile(2.0, 0.5, 0.15, 0.32, 0.9, 2.0);
        ConfigurablePedestrianGenerator g = new ConfigurablePedestrianGenerator(
                "test", 60.0, 0.0, BIG_ZONE, 5, GenerationMode.BATCH, PLANS, List::of, 0.0, crisis);

        List<Agent> spawned = g.spawnInitial();
        assertEquals(5, spawned.size());
        for (Agent a : spawned) {
            assertEquals(crisis, a.state().profile(),
                    "el agente debe nacer con el perfil override del generador");
        }
    }

    @Test
    void withoutProfileOverrideAgentsAreBornWithNullProfile() {
        // Sin override el profile queda null y lo asigna el assembler (default).
        ConfigurablePedestrianGenerator g = gen(60.0, 3, GenerationMode.BATCH);
        for (Agent a : g.spawnInitial()) {
            assertEquals(null, a.state().profile(),
                    "sin override, el profile lo asigna después el assembler");
        }
    }

    @Test
    void batchSpawnInitialPlacesExactCountAndDoesNotDuplicateOnFirstTick() {
        ConfigurablePedestrianGenerator g = gen(60.0, 12, GenerationMode.BATCH);

        List<Agent> initial = g.spawnInitial();
        assertEquals(12, initial.size(), "BATCH siembra la cantidad literal en t=0");

        List<Agent> firstTick = g.spawnTick(0.0, DT);
        assertEquals(0, firstTick.size(),
                "el spawnTick(0) no debe re-sembrar el lote ya colocado en spawnInitial");
    }

    @Test
    void batchCountIsLiteralNotRate() {
        // Con la fórmula vieja de caudal habría dado round(7*10/60)=1.
        ConfigurablePedestrianGenerator g = gen(10.0, 7, GenerationMode.BATCH);
        assertEquals(7, g.spawnInitial().size(),
                "rate_or_count en BATCH es cantidad literal, no caudal");
    }

    @Test
    void calmDoesNotOccupyOnInitialAndTricklesFromFirstTick() {
        ConfigurablePedestrianGenerator g = gen(60.0, 60, GenerationMode.CALM);

        assertEquals(0, g.spawnInitial().size(),
                "CALM no usa ocupación inicial; arranca el goteo en spawnTick");

        // 60 p/min => interArrival 1s. En t=0 sale 1; en t=0.5 todavía 0.
        assertEquals(1, g.spawnTick(0.0, DT).size(), "primer agente en t=0");
        assertTrue(g.spawnTick(0.5, DT).isEmpty(), "no hay agente antes del intervalo");
        assertEquals(1, g.spawnTick(1.0, DT).size(), "segundo agente al cumplirse el intervalo");
    }

    @Test
    void totalPopulationDoesNotDoubleOverTheRun() {
        // BATCH de 8 en una corrida larga sin ciclos nuevos (activeTime == run):
        // el total debe ser 8, no 16.
        ConfigurablePedestrianGenerator g = gen(30.0, 8, GenerationMode.BATCH);
        int total = g.spawnInitial().size();
        for (double t = 0.0; t < 30.0; t += DT) {
            total += g.spawnTick(t, DT).size();
        }
        assertEquals(8, total, "el lote BATCH se siembra una sola vez en el ciclo");
    }
}
