package ar.edu.itba.simped.environment.servers.engine;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;

import java.util.Objects;

/**
 * Exposes a logical server <em>group</em> as a {@code core.ports.Server}
 * (§ 5.5, edge I13a).
 *
 * <p>The StateMachine addresses a group (e.g. {@code "checkin"}) by
 * {@link #name()} and delegates an agent to it; {@link #delegate(Agent)} routes
 * the request into {@link ServersModule#delegate(int, String, Vec2)}, which then
 * picks the concrete member server. The SM never chooses a specific server.</p>
 *
 * <p>{@link #position()} is the group's nominal (centroid) position, used by the
 * SM as the coarse foot-target for Sensors (I9); the actual fine targets of the
 * assigned server are pushed by the module via I13b.</p>
 */
public final class ServerAdapter implements ar.edu.itba.simped.core.ports.Server {

    private final String group;
    private final Vec2 position;
    private final ServersModule module;

    public ServerAdapter(String group, Vec2 position, ServersModule module) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.position = Objects.requireNonNull(position, "position must not be null");
        this.module = Objects.requireNonNull(module, "module must not be null");
    }

    @Override
    public String name() {
        return group;
    }

    @Override
    public Vec2 position() {
        return position;
    }

    @Override
    public void delegate(Agent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        AgentState st = agent.state();
        Vec2 pos = (st != null) ? new Vec2(st.x(), st.y()) : position;
        module.delegate(agent.id(), group, pos);
    }
}
