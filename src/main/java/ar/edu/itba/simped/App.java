package ar.edu.itba.simped;

import ar.edu.itba.simped.core.AgentProfile;
import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Distribution;
import ar.edu.itba.simped.core.Deterministic;
import ar.edu.itba.simped.core.Gaussian;
import ar.edu.itba.simped.core.GeneratorRawParams;
import ar.edu.itba.simped.core.Uniform;
import ar.edu.itba.simped.core.GeneratorZone;
import ar.edu.itba.simped.core.LoadedScenario;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.Rectangle;
import ar.edu.itba.simped.core.SimulationParameters;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.Environment;
import ar.edu.itba.simped.core.ports.Geometry;
import ar.edu.itba.simped.core.ports.Graph;
import ar.edu.itba.simped.core.ports.LocationOccupancy;
import ar.edu.itba.simped.core.ports.NeighborsIndex;
import ar.edu.itba.simped.core.ports.OperationalModel;
import ar.edu.itba.simped.core.ports.OutputSink;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;
import ar.edu.itba.simped.core.ports.ScenarioLoader;
import ar.edu.itba.simped.core.ports.Server;
import ar.edu.itba.simped.core.ports.SimulationDriver;
import ar.edu.itba.simped.agent.om.CpmOperationalModel;
// SFM eliminado (D7): se usa siempre CPM.
import ar.edu.itba.simped.agent.om.CpmParameters;
import ar.edu.itba.simped.environment.EnvironmentImpl;
import ar.edu.itba.simped.environment.LocationOccupancyImpl;
import ar.edu.itba.simped.environment.servers.engine.ServersModule;
import ar.edu.itba.simped.environment.generator.ConfigurablePedestrianGenerator;
import ar.edu.itba.simped.environment.generator.GenerationMode;
import ar.edu.itba.simped.environment.graph.StubGraph;
import ar.edu.itba.simped.environment.neighbors.FloorAwareNeighborsIndex;
import ar.edu.itba.simped.scenario.AgentAssembler;
import ar.edu.itba.simped.scenario.CompositePedestrianGenerator;
import ar.edu.itba.simped.scenario.ServersWiring;
import ar.edu.itba.simped.scenario.WiredPedestrianGenerator;
import ar.edu.itba.simped.simulation.OutputSinkImpl;
import ar.edu.itba.simped.simulation.SimulationDriverImpl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Entry point del simulador.
 *
 * <p>Uso: {@code mvn exec:java -Dexec.mainClass=ar.edu.itba.simped.App
 * -Dexec.args="<scenarioDir> <outputFile> [om]"}.</p>
 *
 * <p>Args:
 * <ul>
 *   <li>{@code scenarioDir} — directorio del escenario (default {@code scenarios/example}).</li>
 *   <li>{@code outputFile} — archivo CSV de salida (default {@code out/output.csv}).</li>
 *   <li>{@code om} — modelo de movimiento: siempre {@code cpm} (el SFM fue eliminado, D7;
 *       el argumento se conserva por compatibilidad de CLI). También vía {@code SIMPED_OM}.</li>
 * </ul>
 * </p>
 */
public final class App {

    private static final String DEFAULT_SCENARIO_DIR = "scenarios/example";
    private static final String DEFAULT_OUTPUT_FILE = "out/output.csv";

    private App() {
    }

    public static void main(String[] args) {
        Path scenarioDir = Paths.get(args.length >= 1 ? args[0] : DEFAULT_SCENARIO_DIR);
        Path outputFile = Paths.get(args.length >= 2 ? args[1] : DEFAULT_OUTPUT_FILE);
        String omChoice = pickOmChoice(args);

        System.out.println("[simped] scenario = " + scenarioDir.toAbsolutePath());
        System.out.println("[simped] output   = " + outputFile.toAbsolutePath());
        System.out.println("[simped] om       = " + omChoice);

        ScenarioLoader loader = new ar.edu.itba.simped.input.ScenarioLoaderImpl();
        LoadedScenario scenario = loader.load(scenarioDir);

        SimulationParameters params = scenario.simParams();
        System.out.printf("[simped] params: dt=%s, dtOut=%s, tTotal=%s%n",
                params.dt(), params.dtOut(), params.tTotal());

        Geometry geometry = scenario.geometry();
        List<GeneratorZone> generators = geometry.generatorZones();
        if (generators.isEmpty()) {
            throw new IllegalStateException("El escenario no tiene generator zones");
        }

        // plan_template puede ser un pool 'a|b|c'; para el template default
        // (fallback del assembler) tomamos el primero del pool.
        String templateName = generators.get(0).params().planTemplateName().split("\\|")[0].trim();
        PlanTemplate template = scenario.planTemplates().get(templateName);
        if (template == null) {
            throw new IllegalStateException("Plan template no encontrado: " + templateName);
        }

        // Se usa siempre CPM (D7: SFM eliminado). El omChoice se conserva por
        // compatibilidad de CLI pero cualquier valor resuelve a CPM.
        // OM multiplanta (D9): toma paredes por planta + escaleras desde Geometry.
        // Su lista global de paredes coincide en orden/ids con la del CIM (D8).
        // Carriles de escalera ON en runs reales (D19: bias lateral subida/bajada
        // gateado, sólo activa en tramos anchos; no afecta tests unitarios, que
        // instancian el OM por otras vías con el gate en false por defecto).
        AgentProfile profile = CpmParameters.baglietoParisiSet1();
        OperationalModel om = CpmOperationalModel.fromGeometry(geometry, true);

        // El dt efectivo lo acota el OM: usamos min(dt del escenario, dt que el
        // modelo considera estable para este profile). Así el escenario no fuerza
        // un paso que el OM (p. ej. CPM, por su radio de contacto) no banca.
        double dt = Math.min(params.dt(), om.recommendedDt(profile));
        if (dt < params.dt()) {
            System.out.printf("[simped] dt acotado por el OM: escenario=%s -> efectivo=%.4f%n",
                    params.dt(), dt);
        }

        // Dimensionamos el CIM para el mayor radio de consulta posible: el margen
        // por behavior-state (rmax*2) y el radio que declara el OM (p. ej. CPM
        // consulta a 4 m). El CIM rechaza consultas con rmax mayor al que dimensionó
        // la grilla, así que tomamos el máximo de ambos.
        AgentState cimProbe = new AgentState(0, "cim-probe");
        cimProbe.setProfile(profile);
        double cimRadius = Math.max(
                profile.rmax() * 2.0,
                om.neighborQueryRadius(cimProbe, ar.edu.itba.simped.core.BehaviorState.WALKING));
        // CIM por planta (D8): una grilla por planta + puente por escalera. En
        // escenarios de una sola planta degenera a una grilla 2D (igual que antes).
        NeighborsIndex neighbors = FloorAwareNeighborsIndex.fromGeometry(geometry, cimRadius);
        // Grafo 3D construido desde Geometry (I17, D6): genera la malla por planta
        // y une las plantas por las escaleras. Reemplaza el re-parseo de CSV.
        Graph graph = StubGraph.fromGeometry(geometry);
        // Debug opcional: si se setea -Dsimped.hopLog=<archivo>, se registra cada consulta
        // nextVisibleHop (pos -> target -> hop) para visualizar los hops sobre el GIF.
        String hopLog = System.getProperty("simped.hopLog");
        if (hopLog != null && !hopLog.isBlank()) {
            var logWalls = ar.edu.itba.simped.environment.graph.GraphBuilder.parseWallsCsv(
                    scenarioDir.resolve("WALLS.csv").toString());
            graph = new ar.edu.itba.simped.environment.graph.LoggingGraph(graph, Paths.get(hopLog), logWalls);
            System.out.println("[simped] hopLog  = " + Paths.get(hopLog).toAbsolutePath());
        }

        LocationOccupancy locationOccupancy = new LocationOccupancyImpl();

        // Servers (5.5): instanciamos el ServersModule real de G0 a partir de
        // las server zones que parsea G3. La SM delega a module.ports() (I13a) y
        // el driver lo avanza cada tick (I13b/I13c vía los sinks del wiring).
        Map<Integer, Agent> agentRegistry = new HashMap<>();
        ServersModule serversModule = ServersWiring.build(geometry.serverZones(), agentRegistry);
        List<Server> servers = serversModule.ports();

        AgentAssembler assembler = new AgentAssembler(
                template, neighbors, graph, profile, om, locationOccupancy, servers,
                agentRegistry);

        // Pool de planes del escenario (fallback si una zona referencia un plan
        // inexistente o si no hay ninguno definido).
        List<PlanTemplate> allPlans = new ArrayList<>(scenario.planTemplates().values());
        if (allPlans.isEmpty()) {
            allPlans = List.of(template);
        }
        // Agentes vivos (de ticks previos) para que el generador no spawnee
        // encima de ellos. Se consulta una vez por spawn-tick por zona.
        Supplier<List<AgentState>> existingAgentsSupplier = () -> {
            List<AgentState> live = new ArrayList<>(agentRegistry.size());
            for (Agent a : agentRegistry.values()) {
                live.add(a.state());
            }
            return live;
        };

        // UN generador por zona/entrada: cada uno con su MODO (instant_occupation
        // → BATCH, resto → CALM), su caudal y su PLAN (el que referencia esa
        // entrada). G9 distribuye dentro de la pool de cada generador. Un
        // contador de IDs compartido evita que dos zonas asignen el mismo ID.
        AtomicInteger idSource = new AtomicInteger(0);
        List<PedestrianGenerator> zoneGenerators = new ArrayList<>(generators.size());
        for (GeneratorZone zone : generators) {
            GeneratorRawParams gp = zone.params();
            double activeTime = gp.activeTime().orElse(params.tTotal());
            double inactiveTime = gp.inactiveTime().orElse(0.0);
            double flowRate = effectiveFlowRatePerMin(gp);
            GenerationMode mode = "instant_occupation".equalsIgnoreCase(gp.mode().orElse(""))
                    ? GenerationMode.BATCH
                    : GenerationMode.CALM;

            // Plan por entrada: plan_template puede ser un POOL separado por
            // '|' (el generador asigna uno al azar por agente). Si ninguno
            // resuelve, cae a la pool completa del escenario (robustez).
            List<PlanTemplate> zonePlans = new ArrayList<>();
            for (String nm : gp.planTemplateName().split("\\|")) {
                PlanTemplate pt = scenario.planTemplates().get(nm.trim());
                if (pt != null) {
                    zonePlans.add(pt);
                }
            }
            if (zonePlans.isEmpty()) {
                zonePlans = allPlans;
            }

            PedestrianGenerator base = new ConfigurablePedestrianGenerator(
                    zone.blockName(), activeTime, inactiveTime, List.of(zone.spawnArea()),
                    flowRate, mode, zonePlans, existingAgentsSupplier, zone.z());
            zoneGenerators.add(
                    new WiredPedestrianGenerator(base, assembler, agentRegistry, idSource));
        }
        PedestrianGenerator pg = new CompositePedestrianGenerator(zoneGenerators);

        Environment env = new EnvironmentImpl(geometry, graph, neighbors, pg, servers, locationOccupancy);

        try (OutputSink output = new OutputSinkImpl(outputFile)) {
            SimulationDriver driver = new SimulationDriverImpl(
                    env, output, dt, params.dtOut(), params.tTotal(),
                    serversModule::step,
                    agentRegistry::remove);
            driver.run();
        }

        System.out.println("[simped] simulación terminada.");
    }

    /**
     * Caudal efectivo [personas/min por puerta] que se le pasa al generador.
     *
     * <p>Formato A pobla {@code rateOrCount} (caudal, o cantidad en
     * instant_occupation) y se usa tal cual. Formato B no trae caudal: lo deriva
     * de {@code quantity_distribution} agentes cada {@code period} segundos
     * (media de la cantidad / período × 60), así el ingreso sale del JSON y no de
     * un default fijo de 10/min.</p>
     */
    private static double effectiveFlowRatePerMin(GeneratorRawParams gp) {
        if (gp.rateOrCount().isPresent()) {
            return gp.rateOrCount().getAsDouble();
        }
        if (gp.period().isPresent() && gp.period().getAsDouble() > 0.0
                && gp.quantityDistribution().isPresent()) {
            double meanQuantity = representativeValue(gp.quantityDistribution().get());
            double perMinute = meanQuantity / gp.period().getAsDouble() * 60.0;
            if (perMinute > 0.0) {
                return perMinute;
            }
        }
        // Sin caudal ni período declarados: fallback conservador.
        return 10.0;
    }

    /** Valor central de una distribución (media / punto medio / valor fijo). */
    private static double representativeValue(Distribution d) {
        return switch (d) {
            case Deterministic det -> det.value();
            case Gaussian g -> g.mean();
            case Uniform u -> (u.min() + u.max()) / 2.0;
        };
    }

    /**
     * Resuelve qué OM usar. Prioridad: arg posicional > env var > default "cpm".
     * Tras D7 solo existe CPM; el valor se conserva por compat de CLI pero
     * App siempre construye CPM.
     */
    private static String pickOmChoice(String[] args) {
        if (args.length >= 3 && !args[2].isBlank()) {
            return args[2].toLowerCase();
        }
        String env = System.getenv("SIMPED_OM");
        if (env != null && !env.isBlank()) {
            return env.toLowerCase();
        }
        return "cpm";
    }
}
