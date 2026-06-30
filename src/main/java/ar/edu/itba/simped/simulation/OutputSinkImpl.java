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
 * output step con formato {@code tout; x; y; z; vx; vy; state; id} (D10).
 * Sin header. Coordenadas con punto decimal (Locale.US). La {@code z} (planta /
 * altura en escalera) va junto a {@code x, y} para representar la posición 3D;
 * el {@code id} va último para trazar trayectorias por agente.
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
        // Formato D10: tout; x; y; z; vx; vy; state; id. La z agrupada con x,y
        // (posición 3D); id al final para trazar trayectorias por agente.
        return String.format(Locale.US,
                "%.4f" + SEPARATOR + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR
                        + "%.6f" + SEPARATOR + "%.6f" + SEPARATOR + "%s" + SEPARATOR + "%d",
                tout, agent.x(), agent.y(), agent.z(), agent.vx(), agent.vy(),
                agent.state().name(), agent.id());
    }
}
