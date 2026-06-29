package ar.edu.itba.simped.environment.servers.engine;

import ar.edu.itba.simped.environment.servers.assignment.SoftmaxServerAssigner;
import ar.edu.itba.simped.environment.servers.interfaces.EventSink;
import ar.edu.itba.simped.environment.servers.interfaces.TargetSink;
import ar.edu.itba.simped.environment.servers.model.Rectangle;
import ar.edu.itba.simped.environment.servers.model.Server;
import ar.edu.itba.simped.environment.servers.model.ServerConfig;
import ar.edu.itba.simped.environment.servers.model.ServerType;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.environment.servers.queue.QueueLine;
import ar.edu.itba.simped.environment.servers.service.ServiceTimeSampler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServersModuleTest {

    /** Records the latest target per agent (I13b). */
    private static final class RecordingTargetSink implements TargetSink {
        final Map<Integer, Vec2> last = new HashMap<>();

        @Override
        public void sendTarget(int agentId, Vec2 target) {
            last.put(agentId, target);
        }
    }

    /** Records the order in which agents complete service (I13c). */
    private static final class RecordingEventSink implements EventSink {
        final List<Integer> completedOrder = new ArrayList<>();
        final List<Integer> arrivedOrder = new ArrayList<>();

        @Override
        public void arrivedAtPost(int agentId) {
            arrivedOrder.add(agentId);
        }

        @Override
        public void serviceComplete(int agentId) {
            completedOrder.add(agentId);
        }
    }

    /**
     * Re-delegates each released agent synchronously from inside the callback,
     * like the SM does when {@code serviceComplete} advances the agent's plan
     * to another (or the same) server (bug reported by G7, 2026-06-04).
     */
    private static final class RedelegatingEventSink implements EventSink {
        ServersModule module; // set after construction (mutual dependency)
        final int targetServerId;
        final List<Integer> completedOrder = new ArrayList<>();

        RedelegatingEventSink(int targetServerId) {
            this.targetServerId = targetServerId;
        }

        @Override
        public void serviceComplete(int agentId) {
            completedOrder.add(agentId);
            module.delegate(agentId, targetServerId);
        }
    }

    private static ServersParameters params(double meanService) {
        return new ServersParameters(1.0, 0.5, meanService);
    }

    private static Server queueServer() {
        return queueServerWith(1.0);
    }

    private static Server queueServerWith(double tMean) {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2)); // service (1,1)
        QueueLine ql = new QueueLine(new Vec2(3, 0), new Vec2(3, 9), 1.0);       // slots (3,0),(3,1),...
        return new Server(0, "S", ServerType.QUEUE, new ServerConfig.Queue(tMean),
                region, ql, region.centroid());
    }

    /** Two QUEUE servers sharing one logical {@code group}, ids 0 and 1. */
    private static List<Server> twoQueueServersInGroup(String group) {
        Rectangle r0 = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));    // service (1,1)
        QueueLine q0 = new QueueLine(new Vec2(3, 0), new Vec2(3, 9), 1.0);
        Rectangle r1 = Rectangle.ofCorners(new Vec2(20, 0), new Vec2(22, 2));  // service (21,1)
        QueueLine q1 = new QueueLine(new Vec2(23, 0), new Vec2(23, 9), 1.0);
        ServerConfig.Queue cfg = new ServerConfig.Queue(1.0);
        return List.of(
                new Server(0, "S0", group, ServerType.QUEUE, cfg, r0, q0, r0.centroid()),
                new Server(1, "S1", group, ServerType.QUEUE, cfg, r1, q1, r1.centroid()));
    }

    /** Walks every agent towards its latest target and steps the module. */
    private static void runUntilDone(ServersModule module, Map<Integer, Vec2> positions,
                                     RecordingTargetSink targets) {
        double dt = 0.1;
        double speed = 1.0;
        for (double t = 0.0; t < 500.0 && module.countDelegated() > 0; t += dt) {
            for (int id : new ArrayList<>(positions.keySet())) {
                Vec2 pos = positions.get(id);
                Vec2 tgt = targets.last.get(id);
                if (tgt == null) {
                    continue;
                }
                double d = pos.distanceTo(tgt);
                if (d <= 1e-9) {
                    continue;
                }
                double stepLen = Math.min(speed * dt, d);
                positions.put(id, pos.add(tgt.sub(pos).scale(stepLen / d)));
            }
            module.step(t, dt, positions);
        }
    }

    @Test
    void queueServesAgentsInFifoOrder() {
        RecordingTargetSink targets = new RecordingTargetSink();
        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(queueServerWith(0.5)), targets, events,
                new ServiceTimeSampler(new Random(1)), params(0.5));

        module.delegate(10, 0);
        module.delegate(11, 0);
        module.delegate(12, 0);

        // initial slot targets, in delegation order
        assertEquals(new Vec2(3, 0), targets.last.get(10));
        assertEquals(new Vec2(3, 1), targets.last.get(11));
        assertEquals(new Vec2(3, 2), targets.last.get(12));

        Map<Integer, Vec2> positions = new HashMap<>();
        positions.put(10, new Vec2(3, 5));
        positions.put(11, new Vec2(3, 5));
        positions.put(12, new Vec2(3, 5));

        runUntilDone(module, positions, targets);

        assertEquals(List.of(10, 11, 12), events.completedOrder);
        assertEquals(0, module.countDelegated());
        // the head was sent to the service position at some point
        assertEquals(new Vec2(1, 1), targets.last.get(10));
    }

    @Test
    void delegatingTwiceIsRejected() {
        ServersModule module = new ServersModule(List.of(queueServer()), new RecordingTargetSink(),
                new RecordingEventSink(), new ServiceTimeSampler(new Random(4)), params(1.0));
        module.delegate(40, 0);
        assertThrows(IllegalStateException.class, () -> module.delegate(40, 0));
        assertTrue(module.isDelegated(40));
        assertFalse(module.isDelegated(99));
    }

    @Test
    void unknownServerIsRejected() {
        ServersModule module = new ServersModule(List.of(queueServer()), new RecordingTargetSink(),
                new RecordingEventSink(), new ServiceTimeSampler(new Random(5)), params(1.0));
        assertThrows(IllegalArgumentException.class, () -> module.delegate(50, 999));
    }

    @Test
    void singletonGroupDelegatesToTheOnlyMember() {
        RecordingTargetSink targets = new RecordingTargetSink();
        ServersModule module = new ServersModule(List.of(queueServer()), targets,
                new RecordingEventSink(), new ServiceTimeSampler(new Random(6)), params(1.0));

        int chosen = module.delegate(70, "S", new Vec2(0, 0));

        assertEquals(0, chosen);
        assertTrue(module.isDelegated(70));
        assertEquals(new Vec2(3, 0), targets.last.get(70)); // first slot target emitted (I13b)
    }

    @Test
    void groupDelegationPicksLeastLoadedMember() {
        // tau tiny -> softmax ~ argmin cost; alpha 0 -> load-only (distance ignored).
        ServersModule module = new ServersModule(twoQueueServersInGroup("checkin"),
                new RecordingTargetSink(), new RecordingEventSink(),
                new ServiceTimeSampler(new Random(1)), params(1.0),
                new SoftmaxServerAssigner(new Random(7), 0.01), 1.0, 0.0);

        // Pre-load server 0 directly; server 1 stays empty.
        module.delegate(1, 0);
        module.delegate(2, 0);
        module.delegate(3, 0);

        int chosen = module.delegate(99, "checkin", new Vec2(0, 0));

        assertEquals(1, chosen);
        assertTrue(module.isDelegated(99));
    }

    @Test
    void unknownGroupIsRejected() {
        ServersModule module = new ServersModule(List.of(queueServer()), new RecordingTargetSink(),
                new RecordingEventSink(), new ServiceTimeSampler(new Random(8)), params(1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.delegate(60, "nope", new Vec2(0, 0)));
    }

    @Test
    void classroomReleasesEveryoneAtSessionEnd() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        Server classroom = new Server(0, "P", ServerType.CLASSROOM,
                new ServerConfig.Classroom(new double[]{10.0}, 5.0),
                region, null, region.centroid());

        RecordingTargetSink targets = new RecordingTargetSink();
        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(classroom), targets, events,
                new ServiceTimeSampler(new Random(2)), params(1.0));

        // step before t=15 (the session end): nobody is released, even if delegated long ago.
        module.delegate(20, 0);
        module.step(1.0, 1.0, Map.of());
        module.delegate(21, 0);
        module.step(12.0, 1.0, Map.of());
        assertTrue(events.completedOrder.isEmpty());

        // step at t >= 15: both agents released in one batch.
        module.step(15.0, 1.0, Map.of());
        assertEquals(2, events.completedOrder.size());
        assertTrue(events.completedOrder.contains(20));
        assertTrue(events.completedOrder.contains(21));
        assertEquals(0, module.countDelegated());
    }

    @Test
    void semaphoreHoldsOnRedAndReleasesEveryoneOnGreen() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // period 10, green 4, offset 0 → green en fase [0,4), rojo en [4,10).
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 4.0, 0.0),
                region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(3)), params(1.0));

        // Ambos parados dentro de la región de espera.
        Map<Integer, Vec2> positions = Map.of(40, new Vec2(1, 1), 41, new Vec2(1.5, 1.5));

        // En rojo nadie cruza, aunque estén adentro hace rato.
        module.delegate(40, 0);
        module.step(5.0, 1.0, positions);
        module.delegate(41, 0);
        module.step(9.9, 1.0, positions);
        assertTrue(events.completedOrder.isEmpty());

        // En verde (fase 12 mod 10 = 2 < 4) cruzan todos los que esperaban adentro.
        module.step(12.0, 1.0, positions);
        assertEquals(2, events.completedOrder.size());
        assertTrue(events.completedOrder.contains(40));
        assertTrue(events.completedOrder.contains(41));
        assertEquals(0, module.countDelegated());
    }

    @Test
    void queueSupportsSynchronousRedelegationOnServiceComplete() {
        RecordingTargetSink targets = new RecordingTargetSink();
        RedelegatingEventSink events = new RedelegatingEventSink(0);
        ServersModule module = new ServersModule(List.of(queueServerWith(0.5)), targets, events,
                new ServiceTimeSampler(new Random(9)), params(0.5));
        events.module = module;

        module.delegate(10, 0);
        Map<Integer, Vec2> positions = new HashMap<>();

        // Walk to the first slot -> engaged towards the service position.
        positions.put(10, new Vec2(3, 0));
        module.step(0.0, 0.1, positions);
        assertEquals(new Vec2(1, 1), targets.last.get(10));

        // Arrive at the service position -> the service timer starts.
        positions.put(10, new Vec2(1, 1));
        module.step(0.1, 0.1, positions);

        // Timer expired: the sink re-delegates the agent to the same server
        // from inside serviceComplete. Used to throw "already delegated".
        module.step(1000.0, 0.1, positions);

        assertEquals(List.of(10), events.completedOrder);
        assertTrue(module.isDelegated(10));
        assertEquals(new Vec2(3, 0), targets.last.get(10)); // back at the first slot
    }

    @Test
    void semaphoreSupportsSynchronousRedelegationOnRelease() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // Always green: releases on every step.
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 10.0, 0.0),
                region, null, region.centroid());

        RedelegatingEventSink events = new RedelegatingEventSink(0);
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(10)), params(1.0));
        events.module = module;

        module.delegate(40, 0);
        module.delegate(41, 0);

        // On green, both (standing inside the region) are released and
        // re-delegated back into the room from inside the callback. Used to
        // throw "already delegated" (and mutated insideRoom while iterating it).
        module.step(0.0, 1.0, Map.of(40, new Vec2(1, 1), 41, new Vec2(1.5, 1.5)));

        assertEquals(2, events.completedOrder.size());
        assertTrue(events.completedOrder.contains(40));
        assertTrue(events.completedOrder.contains(41));
        assertTrue(module.isDelegated(40));
        assertTrue(module.isDelegated(41));
        assertEquals(2, module.countDelegated());
    }

    @Test
    void defaultArrivalThresholdMatchesG7RequestedTolerance() {
        // G7 (CPM) asked for a 0.3-0.5 m tolerance when attending the queue
        // head (mail 2026-06-04, approved by chair).
        assertEquals(0.5, ServersParameters.defaults().arrivalThreshold());
    }

    @Test
    void defaultSlotSpacingExceedsTwoAgentRadii() {
        // La catedra exige que la separacion entre slots supere la suma de los
        // radios de dos agentes (mail 2026-06-04). Peor caso hoy: CPM con
        // rmax 0.37 x 1.10 (multiplicador QUEUEING) por agente ~= 0.81 m.
        double worstCaseTwoRadii = 2 * 0.37 * 1.10;
        assertTrue(ServersParameters.defaults().slotSpacing() > worstCaseTwoRadii);
        // El frente de la fila tampoco puede pisarse con el atendido: el gap
        // del wiring (1.0 m) cumple la misma regla.
        assertEquals(1.0, ServersParameters.defaults().slotSpacing());
    }

    @Test
    void semaphoreOffsetStaggersGreenWindows() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // A: verde [0,5); B: mismo ciclo con offset 5 → verde [5,10).
        Server a = new Server(0, "A", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 5.0, 0.0), region, null, region.centroid());
        Server b = new Server(1, "B", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 5.0, 5.0), region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(a, b), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(3)), params(1.0));

        module.delegate(1, 0);   // espera en A
        module.delegate(2, 1);   // espera en B
        Map<Integer, Vec2> positions = Map.of(1, new Vec2(1, 1), 2, new Vec2(1.5, 1.5));

        // t=2: A verde, B rojo → solo cruza el de A.
        module.step(2.0, 1.0, positions);
        assertEquals(List.of(1), events.completedOrder);

        // t=7: A rojo, B verde → ahora cruza el de B.
        module.step(7.0, 1.0, positions);
        assertTrue(events.completedOrder.contains(2));
        assertEquals(0, module.countDelegated());
    }

    @Test
    void semaphoreReleasesOnlyAgentsInsideRegionOnGreen() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // Siempre verde: lo único que regula el cruce es estar adentro.
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 10.0, 0.0),
                region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(11)), params(1.0));

        module.delegate(50, 0);  // adentro
        module.delegate(51, 0);  // afuera, todavía caminando
        module.delegate(52, 0);  // sin posición conocida

        module.step(0.0, 1.0, Map.of(50, new Vec2(1, 1), 51, new Vec2(5, 5)));

        assertEquals(List.of(50), events.completedOrder);
        assertFalse(module.isDelegated(50));
        assertTrue(module.isDelegated(51));
        assertTrue(module.isDelegated(52));
    }

    @Test
    void semaphoreReleasesLateArrivalWhileStillGreen() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // Verde en fase [0,6), rojo en [6,10).
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 6.0, 0.0),
                region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(12)), params(1.0));

        module.delegate(60, 0);

        // Verde pero todavía afuera: sigue retenido.
        module.step(1.0, 1.0, Map.of(60, new Vec2(8, 8)));
        assertTrue(events.completedOrder.isEmpty());
        assertTrue(module.isDelegated(60));

        // Pisa la región mientras sigue verde: cruza.
        module.step(3.0, 1.0, Map.of(60, new Vec2(1, 1)));
        assertEquals(List.of(60), events.completedOrder);
        assertEquals(0, module.countDelegated());
    }

    @Test
    void semaphoreReleasesWithinArrivalThresholdMargin() {
        // Región 1×1 (chica, como en los escenarios reales). En multitud los
        // agentes se agolpan en el borde y no quedan estrictamente adentro.
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(1, 1));
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 10.0, 0.0),  // siempre verde
                region, null, region.centroid());
        RecordingEventSink events = new RecordingEventSink();
        // params(): arrivalThreshold = 0.5 → tolerancia de medio metro.
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(20)), params(1.0));

        module.delegate(80, 0);  // justo afuera del borde, dentro del margen
        module.delegate(81, 0);  // lejos, fuera del margen

        module.step(0.0, 1.0, Map.of(80, new Vec2(1.3, 0.5), 81, new Vec2(3.0, 3.0)));

        // 80 (a 0.3 m del borde ≤ 0.5) cruza; 81 (a >2 m) no.
        assertEquals(List.of(80), events.completedOrder);
        assertTrue(module.isDelegated(81));
    }

    @Test
    void semaphoreArrivalDuringRedWaitsForNextGreen() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // Verde en fase [0,3), rojo en [3,10).
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 3.0, 0.0),
                region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(13)), params(1.0));

        module.delegate(70, 0);
        Map<Integer, Vec2> inside = Map.of(70, new Vec2(1, 1));

        // Llega a la región en rojo: retenido todos los steps del rojo.
        module.step(4.0, 1.0, inside);
        module.step(7.0, 1.0, inside);
        module.step(9.9, 1.0, inside);
        assertTrue(events.completedOrder.isEmpty());
        assertTrue(module.isDelegated(70));

        // Próximo verde (fase 11 mod 10 = 1 < 3): cruza.
        module.step(11.0, 1.0, inside);
        assertEquals(List.of(70), events.completedOrder);
        assertEquals(0, module.countDelegated());
    }

    @Test
    void queueEmitsArrivedAtPostOnceWhenReachingTheSlot() {
        RecordingTargetSink targets = new RecordingTargetSink();
        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(queueServerWith(5.0)), targets, events,
                new ServiceTimeSampler(new Random(14)), params(5.0));

        module.delegate(10, 0);
        module.delegate(11, 0);
        Map<Integer, Vec2> positions = new HashMap<>();

        // Lejos de los slots: nadie se asienta todavia.
        positions.put(10, new Vec2(3, 5));
        positions.put(11, new Vec2(3, 5));
        module.step(0.0, 0.1, positions);
        assertTrue(events.arrivedOrder.isEmpty());

        // 10 pisa su slot (3,0): arrivedAtPost una sola vez.
        positions.put(10, new Vec2(3, 0));
        module.step(0.1, 0.1, positions);
        assertEquals(List.of(10), events.arrivedOrder);

        // El head se engancha y 11 avanza al slot 0: NO se re-emite para 10,
        // y 11 recien emite cuando pisa su slot.
        positions.put(11, new Vec2(3, 0));
        module.step(0.2, 0.1, positions);
        module.step(0.3, 0.1, positions);
        assertEquals(List.of(10, 11), events.arrivedOrder);
    }

    @Test
    void semaphoreEmitsArrivedAtPostWhenSteppingOnRegionEvenInRed() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        // Verde en fase [0,3), rojo en [3,10).
        Server semaphore = new Server(0, "TL", ServerType.SEMAPHORE,
                new ServerConfig.Semaphore(10.0, 3.0, 0.0),
                region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(semaphore), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(15)), params(1.0));

        module.delegate(80, 0);

        // Afuera de la región: sin señal, esté como esté el semáforo.
        module.step(4.0, 1.0, Map.of(80, new Vec2(5, 5)));
        assertTrue(events.arrivedOrder.isEmpty());

        // Pisa la región en rojo: arrivedAtPost (pero sin liberación).
        Map<Integer, Vec2> inside = Map.of(80, new Vec2(1, 1));
        module.step(5.0, 1.0, inside);
        assertEquals(List.of(80), events.arrivedOrder);
        assertTrue(events.completedOrder.isEmpty());

        // Sigue adentro: no se re-emite. En verde, primero llegó la señal de
        // puesto y después la de fin de servicio.
        module.step(6.0, 1.0, inside);
        module.step(11.0, 1.0, inside);
        assertEquals(List.of(80), events.arrivedOrder);
        assertEquals(List.of(80), events.completedOrder);
    }

    @Test
    void classroomEmitsArrivedAtPostWhenEnteringTheRoom() {
        Rectangle region = Rectangle.ofCorners(new Vec2(0, 0), new Vec2(2, 2));
        Server classroom = new Server(0, "P", ServerType.CLASSROOM,
                new ServerConfig.Classroom(new double[]{10.0}, 5.0),
                region, null, region.centroid());

        RecordingEventSink events = new RecordingEventSink();
        ServersModule module = new ServersModule(List.of(classroom), new RecordingTargetSink(),
                events, new ServiceTimeSampler(new Random(16)), params(1.0));

        module.delegate(90, 0);

        // Caminando hacia el aula: sin señal.
        module.step(1.0, 1.0, Map.of(90, new Vec2(8, 8)));
        assertTrue(events.arrivedOrder.isEmpty());

        // Entra al aula: señal una sola vez; al fin de sesión se libera igual
        // que siempre (release por schedule, estén donde estén).
        module.step(2.0, 1.0, Map.of(90, new Vec2(1, 1)));
        module.step(3.0, 1.0, Map.of(90, new Vec2(1, 1)));
        assertEquals(List.of(90), events.arrivedOrder);
        module.step(15.0, 1.0, Map.of(90, new Vec2(1, 1)));
        assertEquals(List.of(90), events.completedOrder);
    }
}
