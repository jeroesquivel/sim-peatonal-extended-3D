package ar.edu.itba.simped.environment.servers.geometry;

import ar.edu.itba.simped.environment.servers.model.ServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code SERVER_PARAMS.csv} into a {@code Map<serverName, ServerConfig>}
 * consumable by {@link ServersGeometryParser#parse(Path, double, Map)}.
 *
 * <p>Expected format (header row mandatory):</p>
 *
 * <pre>
 * block_name, type, server_time_param, t_init
 * CASHIER_1_SERVER,      queue,     8.0,
 * SEMAPHORE_1_SERVER,    semaphore, 5.0,
 * PRESENTATION_1_SERVER, classroom, 30.0, 0.0
 * PRESENTATION_1_SERVER, classroom, 30.0, 45.0
 * </pre>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>{@code block_name} must end with {@code _SERVER}; the trailing suffix is
 *       stripped to obtain the server's logical name (e.g. {@code CASHIER_1}).</li>
 *   <li>{@code type} ∈ {{@code queue}, {@code semaphore}, {@code classroom}}
 *       (case-insensitive).</li>
 *   <li>For {@code queue} and {@code semaphore} a single row per server is
 *       expected; {@code t_init} must be empty.</li>
 *   <li>For {@code classroom} one row <em>per session</em> is expected;
 *       {@code server_time_param} must be identical across rows of the same
 *       server, and the {@code t_init} values must be strictly increasing.</li>
 * </ul>
 */
public final class ServerParamsParser {

    private static final String SERVER_SUFFIX = "_SERVER";

    private ServerParamsParser() {
    }

    public static Map<String, ServerConfig> parse(Path csv) {
        try (Reader r = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return parse(r);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + csv, e);
        }
    }

    public static Map<String, ServerConfig> parse(Reader reader) {
        // serverName -> ordered rows for that server.
        Map<String, List<Row>> rowsByServer = readRows(reader);

        Map<String, ServerConfig> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Row>> e : rowsByServer.entrySet()) {
            String name = e.getKey();
            List<Row> rows = e.getValue();
            String type = rows.get(0).type;
            for (Row r : rows) {
                if (!r.type.equalsIgnoreCase(type)) {
                    throw new IllegalArgumentException(
                            "server '" + name + "' has rows with mixed types: '"
                                    + type + "' and '" + r.type + "'");
                }
            }
            out.put(name, switch (type.toLowerCase(Locale.ROOT)) {
                case "queue"     -> buildQueue(name, rows);
                case "semaphore" -> buildSemaphore(name, rows);
                case "classroom" -> buildClassroom(name, rows);
                default -> throw new IllegalArgumentException(
                        "server '" + name + "': unknown type '" + type + "'");
            });
        }
        return out;
    }

    private static ServerConfig buildQueue(String name, List<Row> rows) {
        Row r = singleRow(name, "queue", rows);
        if (r.tInit != null) {
            throw new IllegalArgumentException(
                    "server '" + name + "' (queue): t_init must be empty, got " + r.tInit);
        }
        return new ServerConfig.Queue(r.timeParam);
    }

    private static ServerConfig buildSemaphore(String name, List<Row> rows) {
        Row r = singleRow(name, "semaphore", rows);
        if (r.tInit != null) {
            throw new IllegalArgumentException(
                    "server '" + name + "' (semaphore): t_init must be empty, got " + r.tInit);
        }
        // Formato standalone: un solo time param → lo tomamos como período,
        // verde = mitad del ciclo y sin desfasaje. (El camino integrado arma
        // el semáforo en ServersWiring con offset/green reales.)
        return new ServerConfig.Semaphore(r.timeParam, r.timeParam / 2.0, 0.0);
    }

    private static ServerConfig buildClassroom(String name, List<Row> rows) {
        double tMean = rows.get(0).timeParam;
        double[] tInit = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.timeParam != tMean) {
                throw new IllegalArgumentException(
                        "server '" + name + "' (classroom): server_time_param differs across rows ("
                                + tMean + " vs " + r.timeParam + ")");
            }
            if (r.tInit == null) {
                throw new IllegalArgumentException(
                        "server '" + name + "' (classroom): every row must supply t_init");
            }
            tInit[i] = r.tInit;
        }
        return new ServerConfig.Classroom(tInit, tMean);
    }

    private static Row singleRow(String name, String type, List<Row> rows) {
        if (rows.size() != 1) {
            throw new IllegalArgumentException(
                    "server '" + name + "' (" + type + ") expects exactly one row, got " + rows.size());
        }
        return rows.get(0);
    }

    private static Map<String, List<Row>> readRows(Reader reader) {
        Map<String, List<Row>> rowsByServer = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] f = trimmed.split(",", -1);
                String first = f[0].trim();
                if (first.isEmpty() || first.equalsIgnoreCase("block_name")) {
                    continue;
                }
                if (f.length < 3) {
                    throw new IllegalArgumentException(
                            "malformed SERVER_PARAMS row at line " + lineNo
                                    + " (need at least block_name, type, server_time_param): " + line);
                }
                if (!first.endsWith(SERVER_SUFFIX)) {
                    throw new IllegalArgumentException(
                            "block_name must end with '_SERVER' at line " + lineNo + ": " + first);
                }
                String serverName = first.substring(0, first.length() - SERVER_SUFFIX.length());
                String type = f[1].trim();
                double timeParam = parseDouble(f[2], "server_time_param", lineNo);
                Double tInit = (f.length >= 4 && !f[3].trim().isEmpty())
                        ? parseDouble(f[3], "t_init", lineNo)
                        : null;
                rowsByServer.computeIfAbsent(serverName, k -> new ArrayList<>())
                        .add(new Row(type, timeParam, tInit));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("error reading SERVER_PARAMS", e);
        }
        return rowsByServer;
    }

    private static double parseDouble(String s, String field, int lineNo) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "could not parse " + field + " at line " + lineNo + ": '" + s + "'", ex);
        }
    }

    private record Row(String type, double timeParam, Double tInit) { }
}
