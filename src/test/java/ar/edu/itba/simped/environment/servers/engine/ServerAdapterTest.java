package ar.edu.itba.simped.environment.servers.engine;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.environment.servers.interfaces.EventSink;
import ar.edu.itba.simped.environment.servers.interfaces.TargetSink;
import ar.edu.itba.simped.environment.servers.model.Rectangle;
import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.environment.servers.model.ServerConfig;
import ar.edu.itba.simped.environment.servers.model.ServerType;
import ar.edu.itba.simped.environment.servers.queue.QueueLine;
import ar.edu.itba.simped.environment.servers.service.ServiceTimeSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that {@link ServerAdapter} bridges {@code core.ports.Server} onto the module (I13a). */
class ServerAdapterTest {

    private static final class NoopTargetSink implements TargetSink {
        @Override public void sendTarget(int agentId, Vec2 target) { }
    }

    private static final class NoopEventSink implements EventSink {
        @Override public void serviceComplete(int agentId) { }
    }

    /** Minimal Agent carrying only an id (the only field delegate() needs). */
    private static final class FakeAgent implements Agent {
        private final int id;
        FakeAgent(int id) { this.id = id; }
        @Override public void step(double dt) { }
        @Override public AgentState state() { return null; }
        @Override public int id() { return id; }
    }

    private static ServersParameters params() {
        return new ServersParameters(1.0, 0.5, 1.0);
    }

    private static Server queueServer() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2)); // service (1,1)
        QueueLine ql = new QueueLine(new Vec2(3, 0), new Vec2(3, 9), 1.0);
        return new Server(0, "S", ServerType.QUEUE, new ServerConfig.Queue(1.0),
                region, ql, region.centroid());
    }

    @Test
    void exposesNameAndServicePosition() {
        ServersModule module = new ServersModule(List.of(queueServer()), new NoopTargetSink(),
                new NoopEventSink(), new ServiceTimeSampler(new Random(1)), params());

        List<ar.edu.itba.simped.core.ports.Server> ports = module.ports();
        assertEquals(1, ports.size());
        ar.edu.itba.simped.core.ports.Server port = ports.get(0);
        assertEquals("S", port.name());
        assertEquals(new Vec2(1, 1), port.position());
    }

    @Test
    void delegateRoutesIntoTheModule() {
        ServersModule module = new ServersModule(List.of(queueServer()), new NoopTargetSink(),
                new NoopEventSink(), new ServiceTimeSampler(new Random(2)), params());
        ar.edu.itba.simped.core.ports.Server port = module.ports().get(0);

        port.delegate(new FakeAgent(7));

        assertTrue(module.isDelegated(7));
        assertEquals(1, module.countDelegated());
    }

}
