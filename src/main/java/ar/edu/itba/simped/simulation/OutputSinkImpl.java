package ar.edu.itba.simped.simulation;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.ports.OutputSink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Implementación del módulo 3 (Output). Escribe una fila por agente por
 * output step con formato {@code tout; x; y; vx; vy; state; id}.
 * Sin header. Coordenadas con punto decimal (Locale.US). El {@code id} va
 * último para no romper a quien lee las columnas 0-5 por índice.
 */
public final class OutputSinkImpl implements OutputSink {

    private static final String SEPARATOR = "; ";
    private final BufferedWriter writer;

    public OutputSinkImpl(Path outputFile) {
        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el archivo de salida: " + outputFile, e);
        }
    }

    @Override
    public void writeStep(double tout, Iterable<AgentState> agents) {
        try {
            for (AgentState agent : agents) {
                writer.write(formatRow(tout, agent));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error escribiendo output step", e);
        }
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Error cerrando el archivo de salida", e);
        }
    }

    private static String formatRow(double tout, AgentState agent) {
        // id al final: permite trazar trayectorias por agente sin romper a los
        // consumidores que leen t;x;y;vx;vy;state por índice (0-5).
        return String.format(Locale.US,
                "%.4f" + SEPARATOR + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR
                        + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR + "%s" + SEPARATOR + "%d",
                tout, agent.x(), agent.y(), agent.vx(), agent.vy(), agent.state().name(), agent.id());
    }
}
