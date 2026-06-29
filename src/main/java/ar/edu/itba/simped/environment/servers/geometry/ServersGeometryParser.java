package ar.edu.itba.simped.environment.servers.geometry;

import ar.edu.itba.simped.environment.servers.model.Rectangle;
import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.environment.servers.model.ServerConfig;
import ar.edu.itba.simped.environment.servers.model.ServerType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.environment.servers.queue.QueueLine;

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
import java.util.Map;

/**
 * Parses {@code SERVERS.csv} (interface I20) into a list of {@link Server}.
 *
 * <p>Format (2D — the {@code z} columns were dropped):</p>
 * <pre>block_name, x1, y1, x2, y2 [, group [, qdx, qdy]]</pre>
 *
 * <p>Blocks are grouped by prefix ({@link ServerBlock#groupKey()}); each group
 * yields one server:</p>
 * <ul>
 *   <li>the {@code *_SERVER} block gives the service region (centroid = default
 *       service position), the optional {@code group}, and the optional queue
 *       direction vector {@code (qdx, qdy)};</li>
 *   <li>if the {@code *_SERVER} row carries {@code (qdx, qdy)}, the server is a
 *       {@link ServerType#QUEUE} whose queue starts at the service position and
 *       grows along that vector (capacity {@code maxQueueSlots});</li>
 *   <li>otherwise, a legacy {@code *_QUEUE} segment block, if present, defines
 *       the queue (also {@link ServerType#QUEUE});</li>
 *   <li>a queue-less server defaults to {@link ServerType#CLASSROOM}.</li>
 * </ul>
 */
public final class ServersGeometryParser {

    /**
     * Default queue capacity for the directed-queue form. The real maximum
     * (and what happens when the queue overflows / collides with geometry) is
     * still TODO — a bounded default keeps things finite until then.
     */
    public static final int DEFAULT_MAX_QUEUE_SLOTS = 20;

    /** Stub config used when no {@link ServerConfig} is supplied for a server. */
    private static final ServerConfig DEFAULT_QUEUE     = new ServerConfig.Queue(3.0);
    private static final ServerConfig DEFAULT_SEMAPHORE = new ServerConfig.Semaphore(10.0, 5.0, 0.0);
    private static final ServerConfig DEFAULT_CLASSROOM =
            new ServerConfig.Classroom(new double[]{0.0}, 10.0);

    private ServersGeometryParser() {
    }

    public static List<Server> parse(Path csv, double slotSpacing) {
        return parse(csv, slotSpacing, DEFAULT_MAX_QUEUE_SLOTS, Map.of());
    }

    public static List<Server> parse(Path csv, double slotSpacing, int maxQueueSlots) {
        return parse(csv, slotSpacing, maxQueueSlots, Map.of());
    }

    public static List<Server> parse(Path csv, double slotSpacing,
                                     Map<String, ServerConfig> configs) {
        return parse(csv, slotSpacing, DEFAULT_MAX_QUEUE_SLOTS, configs);
    }

    public static List<Server> parse(Path csv, double slotSpacing, int maxQueueSlots,
                                     Map<String, ServerConfig> configs) {
        try (Reader r = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return parse(r, slotSpacing, maxQueueSlots, configs);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + csv, e);
        }
    }

    public static List<Server> parse(Reader reader, double slotSpacing) {
        return parse(reader, slotSpacing, DEFAULT_MAX_QUEUE_SLOTS, Map.of());
    }

    public static List<Server> parse(Reader reader, double slotSpacing, int maxQueueSlots) {
        return parse(reader, slotSpacing, maxQueueSlots, Map.of());
    }

    public static List<Server> parse(Reader reader, double slotSpacing,
                                     Map<String, ServerConfig> configs) {
        return parse(reader, slotSpacing, DEFAULT_MAX_QUEUE_SLOTS, configs);
    }

    /**
     * @param maxQueueSlots capacity of queues declared with {@code (qdx, qdy)}
     * @param configs       per-server timing configuration keyed by server name
     *                      (e.g. {@code CASHIER_1}). Missing entries fall back
     *                      to a type-appropriate default — convenient for
     *                      tests; production callers should supply the full map
     *                      from {@code SERVER_PARAMS.csv}.
     */
    public static List<Server> parse(Reader reader, double slotSpacing, int maxQueueSlots,
                                     Map<String, ServerConfig> configs) {
        List<ServerBlock> blocks = readBlocks(reader);
        // Preserve first-appearance order of groups for stable server ids.
        Map<String, List<ServerBlock>> byGroup = new LinkedHashMap<>();
        for (ServerBlock b : blocks) {
            byGroup.computeIfAbsent(b.groupKey(), k -> new ArrayList<>()).add(b);
        }

        List<Server> servers = new ArrayList<>(byGroup.size());
        int id = 0;
        for (Map.Entry<String, List<ServerBlock>> e : byGroup.entrySet()) {
            String serverName = e.getKey();
            ServerBlock serverBlock = null;
            ServerBlock queueBlock = null;
            String logicalGroup = null;
            for (ServerBlock b : e.getValue()) {
                if (b.isServerBlock()) {
                    serverBlock = b;
                } else if (b.isQueueBlock()) {
                    queueBlock = b;
                }
                if (logicalGroup == null && b.group() != null) {
                    logicalGroup = b.group();
                }
            }
            if (serverBlock == null) {
                throw new IllegalArgumentException(
                        "server '" + serverName + "' has no *_SERVER block");
            }
            // No explicit group column: each server is its own singleton group.
            if (logicalGroup == null) {
                logicalGroup = serverName;
            }

            Rectangle region = Rectangle.ofCorners(serverBlock.corner1(), serverBlock.corner2());
            Vec2 servicePos = region.centroid();

            QueueLine queueLine = null;
            final ServerType type;
            if (serverBlock.queueDirection() != null) {
                // Directed form: queue grows out of the service point along the vector.
                queueLine = buildDirectedQueue(servicePos, serverBlock.queueDirection(),
                        slotSpacing, maxQueueSlots);
                type = ServerType.QUEUE;
            } else if (queueBlock != null) {
                // Legacy form: queue laid out along the *_QUEUE segment.
                queueLine = buildQueueLine(queueBlock, servicePos, slotSpacing);
                type = ServerType.QUEUE;
            } else {
                // Queue-less servers default to CLASSROOM; an explicit SEMAPHORE
                // config in {@code configs} overrides the type below.
                type = ServerType.CLASSROOM;
            }

            ServerConfig config = configs.get(serverName);
            ServerType finalType = type;
            if (config == null) {
                config = switch (type) {
                    case QUEUE     -> DEFAULT_QUEUE;
                    case SEMAPHORE -> DEFAULT_SEMAPHORE;
                    case CLASSROOM -> DEFAULT_CLASSROOM;
                };
            } else if (config instanceof ServerConfig.Semaphore && type == ServerType.CLASSROOM) {
                finalType = ServerType.SEMAPHORE;
            } else if (config instanceof ServerConfig.Classroom && type == ServerType.SEMAPHORE) {
                finalType = ServerType.CLASSROOM;
            }

            servers.add(new Server(id++, serverName, logicalGroup, finalType, config,
                    region, queueLine, servicePos));
        }
        return servers;
    }

    /**
     * Directed queue: slot 0 sits one {@code slotSpacing} away from the service
     * position along {@code direction}, and the queue grows further along it.
     */
    private static QueueLine buildDirectedQueue(Vec2 servicePos, Vec2 direction,
                                                double slotSpacing, int maxQueueSlots) {
        double len = direction.norm();
        if (!(len > 0.0)) {
            throw new IllegalArgumentException("queue direction must not be the zero vector");
        }
        Vec2 unit = direction.scale(1.0 / len);
        Vec2 front = servicePos.add(unit.scale(slotSpacing));
        return QueueLine.directed(front, direction, slotSpacing, maxQueueSlots);
    }

    private static QueueLine buildQueueLine(ServerBlock queueBlock, Vec2 servicePos,
                                            double slotSpacing) {
        Vec2 c1 = queueBlock.corner1();
        Vec2 c2 = queueBlock.corner2();
        // Front = endpoint nearest the service position; tie -> first endpoint.
        boolean c1IsFront = servicePos.distanceTo(c1) <= servicePos.distanceTo(c2);
        Vec2 front = c1IsFront ? c1 : c2;
        Vec2 back = c1IsFront ? c2 : c1;
        return new QueueLine(front, back, slotSpacing);
    }

    private static List<ServerBlock> readBlocks(Reader reader) {
        List<ServerBlock> blocks = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] f = trimmed.split(",");
                String name = f[0].trim();
                if (name.isEmpty() || name.equalsIgnoreCase("block_name")) {
                    continue; // header or blank name
                }
                if (f.length < 5) {
                    throw new IllegalArgumentException(
                            "malformed SERVERS row (need block_name + 4 coords): " + line);
                }
                // block_name, x1, y1, x2, y2 [, group [, qdx, qdy]]  -> 2D (no z)
                Vec2 c1 = new Vec2(parse(f[1]), parse(f[2]));
                Vec2 c2 = new Vec2(parse(f[3]), parse(f[4]));
                String group = (f.length >= 6 && !f[5].trim().isEmpty()) ? f[5].trim() : null;
                Vec2 queueDir = null;
                if (f.length >= 8 && !f[6].trim().isEmpty() && !f[7].trim().isEmpty()) {
                    queueDir = new Vec2(parse(f[6]), parse(f[7]));
                }
                blocks.add(new ServerBlock(name, c1, c2, group, queueDir));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("error reading SERVERS data", e);
        }
        return blocks;
    }

    private static double parse(String s) {
        return Double.parseDouble(s.trim());
    }
}
