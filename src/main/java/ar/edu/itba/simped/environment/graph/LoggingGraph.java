package ar.edu.itba.simped.environment.graph;

import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Graph;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Decorador de {@link Graph} para debug/visualización: delega en otro grafo y registra cada
 * consulta {@code nextVisibleHop(pos, target) -> hop} en un CSV.
 *
 * <p>No altera el resultado (devuelve exactamente el hop del grafo decorado). Pensado para
 * inspeccionar qué hops está entregando el grafo durante una corrida real (mismo run que produce
 * {@code output.csv}), de modo que el visualizador pueda superponer target final y hops.</p>
 *
 * <p>Formato del CSV:</p>
 * <pre>seq,px,py,tx,ty,hx,hy,direct,visJava</pre>
 * {@code seq} es el índice de consulta (0-based); el visualizador estima {@code t = (seq / nAgentes) * dt}.
 * {@code visJava} vale 1 si el segmento {@code (px,py)->(hx,hy)} es visible según la MISMA
 * {@link VisibilityUtils} del grafo (verdad de Java, sin error de redondeo del CSV).
 * donde {@code (px,py)} es la posición del agente en el momento de la consulta, {@code (tx,ty)} el
 * target final, {@code (hx,hy)} el hop devuelto y {@code direct} vale 1 si el hop coincide con el
 * target (línea de vista directa) y 0 si no.
 */
public final class LoggingGraph implements Graph {

    private final Graph delegate;
    private final BufferedWriter writer;
    private final List<Wall> walls;
    private int seq;

    public LoggingGraph(Graph delegate, Path logFile) {
        this(delegate, logFile, List.of());
    }

    public LoggingGraph(Graph delegate, Path logFile, List<Wall> walls) {
        this.delegate = delegate;
        this.walls = List.copyOf(walls);
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(logFile);
            writer.write("seq,px,py,tx,ty,hx,hy,direct,visJava");
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el log de hops: " + logFile, e);
        }
        // No hay close() en el ciclo de vida del Graph; se vuelca al terminar la JVM.
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushQuietly));
    }

    @Override
    public Vec2 nextVisibleHop(Vec2 agentPosition, Vec2 target) {
        Vec2 hop = delegate.nextVisibleHop(agentPosition, target);
        log(agentPosition, target, hop);
        return hop;
    }

    private void log(Vec2 pos, Vec2 target, Vec2 hop) {
        boolean direct = target != null && hop != null
                && Math.abs(hop.x() - target.x()) < 1e-9
                && Math.abs(hop.y() - target.y()) < 1e-9;
        boolean visJava = hop != null && VisibilityUtils.isVisible(pos, hop, walls);
        try {
            writer.write(String.format(Locale.US, "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d",
                    seq++,
                    pos.x(), pos.y(),
                    target != null ? target.x() : Double.NaN,
                    target != null ? target.y() : Double.NaN,
                    hop != null ? hop.x() : Double.NaN,
                    hop != null ? hop.y() : Double.NaN,
                    direct ? 1 : 0,
                    visJava ? 1 : 0));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Error escribiendo log de hops", e);
        }
    }

    private void flushQuietly() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException ignored) {
            // best-effort: es un artefacto de debug
        }
    }
}
