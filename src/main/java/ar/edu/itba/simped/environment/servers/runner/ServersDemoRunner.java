package ar.edu.itba.simped.environment.servers.runner;

import ar.edu.itba.simped.environment.servers.engine.ServersModule;
import ar.edu.itba.simped.environment.servers.engine.ServersParameters;
import ar.edu.itba.simped.environment.servers.interfaces.EventSink;
import ar.edu.itba.simped.environment.servers.interfaces.TargetSink;
import ar.edu.itba.simped.environment.servers.io.ConsoleEventSink;
import ar.edu.itba.simped.environment.servers.io.ConsoleTargetSink;
import ar.edu.itba.simped.environment.servers.geometry.ServerParamsParser;
import ar.edu.itba.simped.environment.servers.geometry.ServersGeometryParser;
import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.environment.servers.model.ServerConfig;
import ar.edu.itba.simped.environment.servers.model.ServerType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.environment.servers.service.ServiceTimeSampler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;

/**
 * Standalone demo of the Servers module: it runs without any other module.
 *
 * <p>It parses {@code SERVERS.csv} + {@code SERVER_PARAMS.csv}, spawns a few
 * mock agents on the available server types (queue, semaphore, classroom),
 * walks each agent straight towards its latest target at constant speed
 * (a trivial stand-in for the OM), and prints the timeline of I13b targets and
 * I13c events.</p>
 *
 * <pre>
 *   java -cp target/classes \
 *       ar.edu.itba.simped.environment.servers.runner.ServersDemoRunner \
 *       scenarios/example/SERVERS.csv scenarios/example/SERVER_PARAMS.csv
 * </pre>
 */
public final class ServersDemoRunner {

    private static final double DT = 0.1;
    private static final double T_MAX = 180.0;
    private static final double WALK_SPEED = 1.2;   // m/s, OM stand-in
    private static final long SEED = 42L;

    public static void main(String[] args) {
        String geometryPath = args.length > 0 ? args[0] : "scenarios/example/SERVERS.csv";
        String paramsPath   = args.length > 1 ? args[1] : "scenarios/example/SERVER_PARAMS.csv";
        ServersParameters params = ServersParameters.defaults();
        Map<String, ServerConfig> configs = ServerParamsParser.parse(Path.of(paramsPath));
        List<Server> servers = ServersGeometryParser.parse(
                Path.of(geometryPath), params.slotSpacing(), configs);

        System.out.println("== Servers parsed from " + geometryPath + " + " + paramsPath + " ==");
        servers.forEach(s -> System.out.println("  " + s));
        System.out.println();

        // Clock shared with the console sinks so every line is time-stamped.
        double[] now = {0.0};
        DoubleSupplier clock = () -> now[0];

        // I13b sink: record the latest target per agent (the demo "OM" walks
        // each agent toward it) and log it.
        Map<Integer, Vec2> targets = new HashMap<>();
        TargetSink consoleTargets = new ConsoleTargetSink(clock);
        TargetSink targetSink = (agentId, target) -> {
            targets.put(agentId, target);
            consoleTargets.sendTarget(agentId, target);
        };

        // I13c sink: count completions and log.
        int[] completed = {0};
        EventSink consoleEvents = new ConsoleEventSink(clock);
        EventSink eventSink = agentId -> {
            completed[0]++;
            consoleEvents.serviceComplete(agentId);
        };

        ServersModule module = new ServersModule(servers, targetSink, eventSink,
                new ServiceTimeSampler(new Random(SEED)), params);

        List<Server> queues     = servers.stream().filter(s -> s.type() == ServerType.QUEUE).toList();
        List<Server> semaphores = servers.stream().filter(s -> s.type() == ServerType.SEMAPHORE).toList();
        List<Server> classrooms = servers.stream().filter(s -> s.type() == ServerType.CLASSROOM).toList();

        Map<Integer, Vec2> positions = new HashMap<>();
        int nextAgent = 0;
        if (queues.size() >= 1) {
            nextAgent = spawn(module, positions, nextAgent, 3, queues.get(0).id());
        }
        if (queues.size() >= 2) {
            nextAgent = spawn(module, positions, nextAgent, 2, queues.get(1).id());
        }
        if (semaphores.size() >= 1) {
            nextAgent = spawn(module, positions, nextAgent, 2, semaphores.get(0).id());
        }
        if (classrooms.size() >= 1) {
            nextAgent = spawn(module, positions, nextAgent, 2, classrooms.get(0).id());
        }
        System.out.println("== Delegated " + nextAgent + " mock agents; simulating ==");
        System.out.println();

        int steps = 0;
        for (now[0] = 0.0; now[0] <= T_MAX && module.countDelegated() > 0; now[0] += DT) {
            driveAgents(positions, targets);
            module.step(now[0], DT, positions);
            steps++;
        }

        System.out.println();
        System.out.printf(Locale.US,
                "== Done: %d agents served, %d steps, t=%.2f s, %d still delegated ==%n",
                completed[0], steps, now[0], module.countDelegated());
    }

    /** Spawns {@code count} agents near the top centre and delegates them. */
    private static int spawn(ServersModule module, Map<Integer, Vec2> positions,
                             int firstId, int count, int serverId) {
        for (int i = 0; i < count; i++) {
            int id = firstId + i;
            positions.put(id, new Vec2(23.5 + 0.4 * id, 18.0));
            module.delegate(id, serverId);
        }
        return firstId + count;
    }

    /** Moves every agent straight towards its latest target (OM stand-in). */
    private static void driveAgents(Map<Integer, Vec2> positions, Map<Integer, Vec2> targets) {
        for (Map.Entry<Integer, Vec2> e : new ArrayList<>(positions.entrySet())) {
            Vec2 pos = e.getValue();
            Vec2 tgt = targets.get(e.getKey());
            if (tgt == null) {
                continue;
            }
            double d = pos.distanceTo(tgt);
            if (d <= 1e-9) {
                continue;
            }
            double step = Math.min(WALK_SPEED * DT, d);
            Vec2 dir = tgt.sub(pos).scale(1.0 / d);
            positions.put(e.getKey(), pos.add(dir.scale(step)));
        }
    }

    private ServersDemoRunner() {
    }
}
