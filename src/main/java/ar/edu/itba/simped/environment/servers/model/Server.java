package ar.edu.itba.simped.environment.servers.model;

import ar.edu.itba.simped.core.Vec2;

import ar.edu.itba.simped.environment.servers.queue.QueueLine;

import java.util.Objects;

/**
 * A service point of the environment (§ 5.5). Immutable configuration: its
 * runtime occupancy is tracked by the {@code ServersModule}, not here.
 */
public final class Server {

    private final int id;
    private final String name;
    private final String group;
    private final ServerType type;
    private final ServerConfig config;
    private final Rectangle serviceRegion;
    private final QueueLine queueLine;       // nullable: only QUEUE servers have one
    private final Vec2 servicePosition;

    /** Stand-alone server whose logical group is its own name. */
    public Server(int id, String name, ServerType type, ServerConfig config,
                  Rectangle serviceRegion, QueueLine queueLine, Vec2 servicePosition) {
        this(id, name, name, type, config, serviceRegion, queueLine, servicePosition);
    }

    /**
     * @param group  logical group this server belongs to; several servers may
     *               share it, and the SM delegates to the group (not the server).
     * @param config timing parameters of this server; its variant must match
     *               {@code type} (e.g. a QUEUE server requires a {@code Queue} config).
     */
    public Server(int id, String name, String group, ServerType type, ServerConfig config,
                  Rectangle serviceRegion, QueueLine queueLine, Vec2 servicePosition) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.serviceRegion = Objects.requireNonNull(serviceRegion, "serviceRegion must not be null");
        this.servicePosition = Objects.requireNonNull(servicePosition, "servicePosition must not be null");
        if (type == ServerType.QUEUE && queueLine == null) {
            throw new IllegalArgumentException(
                    "QUEUE server '" + name + "' requires a queue line");
        }
        requireMatchingConfig(name, type, config);
        this.id = id;
        this.queueLine = queueLine;
    }

    private static void requireMatchingConfig(String name, ServerType type, ServerConfig config) {
        boolean ok = switch (type) {
            case QUEUE     -> config instanceof ServerConfig.Queue;
            case SEMAPHORE -> config instanceof ServerConfig.Semaphore;
            case CLASSROOM -> config instanceof ServerConfig.Classroom;
        };
        if (!ok) {
            throw new IllegalArgumentException(
                    "server '" + name + "' (type=" + type + ") got config "
                            + config.getClass().getSimpleName() + " — mismatch");
        }
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Logical group this server belongs to (several servers may share it). */
    public String group() {
        return group;
    }

    public ServerType type() {
        return type;
    }

    public ServerConfig config() {
        return config;
    }

    public Rectangle serviceRegion() {
        return serviceRegion;
    }

    /** Queue line for {@code QUEUE} servers, or {@code null} otherwise. */
    public QueueLine queueLine() {
        return queueLine;
    }

    public boolean hasQueue() {
        return queueLine != null;
    }

    public Vec2 servicePosition() {
        return servicePosition;
    }

    @Override
    public String toString() {
        return "Server[id=" + id + ", name=" + name + ", type=" + type
                + ", service=" + servicePosition + (hasQueue() ? ", queued" : "") + "]";
    }
}
