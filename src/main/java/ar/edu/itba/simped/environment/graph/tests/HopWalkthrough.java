package ar.edu.itba.simped.environment.graph.tests;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.environment.graph.HopQueryResult;
import ar.edu.itba.simped.environment.graph.StubGraph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simula consultas repetidas a {@link StubGraph#nextVisibleHop}: cada
 * {@code stepMeters} recorridos se pide un nuevo hop y el agente avanza hacia el hop actual.
 *
 * <p>Exporta {@code tests/output/hop_walkthrough.csv} para el visualizador Python.</p>
 */
public final class HopWalkthrough {

    public static final Path TESTS_OUTPUT = Path.of(
        "src", "main", "java", "ar", "edu", "itba", "simped",
        "environment", "graph", "tests", "output"
    );

    private static final double ARRIVAL_EPS = 0.15;
    private static final int MAX_STEPS = 500;

    private HopWalkthrough() {
    }

    public static void run(StubGraph graph, Vec2 start, Vec2 target, double stepMeters, Path outCsv)
            throws IOException {
        Files.createDirectories(outCsv.getParent());

        try (BufferedWriter w = Files.newBufferedWriter(outCsv)) {
            w.write("seq,kind,x,y,astar_path");
            w.newLine();
            write(w, 0, "START", start, "");

            Vec2 agent = start;
            Vec2 currentHop = null;
            double walkedSinceQuery = stepMeters;
            int seq = 1;

            for (int i = 0; i < MAX_STEPS && agent.distanceTo(target) > ARRIVAL_EPS; i++) {
                if (currentHop == null || walkedSinceQuery >= stepMeters) {
                    HopQueryResult q = graph.queryNextVisibleHop(agent, target);
                    currentHop = q.hop();
                    write(w, seq++, "HOP", currentHop, q.astarPathCsv());
                    walkedSinceQuery = 0;
                }

                double distToHop = agent.distanceTo(currentHop);
                if (distToHop < 1e-9) {
                    walkedSinceQuery = stepMeters;
                    continue;
                }

                double move = Math.min(stepMeters, distToHop);
                Vec2 dir = currentHop.sub(agent).normalized();
                agent = agent.add(dir.scale(move));
                walkedSinceQuery += move;
                write(w, seq++, "MOVE", agent, "");
            }

            write(w, seq, "TARGET", target, "");
        }

        System.out.println("Walkthrough: " + start + " -> " + target
            + " (step=" + stepMeters + "m), events written to " + outCsv);
    }

    private static void write(BufferedWriter w, int seq, String kind, Vec2 p, String astarPath)
            throws IOException {
        w.write(seq + "," + kind + "," + p.x() + "," + p.y() + "," + astarPath);
        w.newLine();
    }

    /**
     * Uso:
     * <pre>
     *   java -cp target/classes ar.edu.itba.simped.environment.graph.tests.HopWalkthrough
     *     [startX startY targetX targetY] [stepMeters]
     * </pre>
     */
    public static void main(String[] args) throws IOException {
        String walls = "scenarios/example/WALLS.csv";
        String servers = "scenarios/example/SERVERS.csv";

        Vec2 start = new Vec2(5.0, 1.0);
        Vec2 target = new Vec2(40.0, 18.0);
        double stepMeters = 2.0;

        if (args.length >= 4) {
            start = new Vec2(Double.parseDouble(args[0]), Double.parseDouble(args[1]));
            target = new Vec2(Double.parseDouble(args[2]), Double.parseDouble(args[3]));
        }
        if (args.length >= 5) {
            stepMeters = Double.parseDouble(args[4]);
        }

        StubGraph graph = StubGraph.fromScenarioFiles(walls, servers);
        graph.exportToOutputDir();

        Path out = TESTS_OUTPUT.resolve("hop_walkthrough.csv");
        run(graph, start, target, stepMeters, out);
    }
}
