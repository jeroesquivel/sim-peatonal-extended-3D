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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The Servers module (§ 5.5): an autonomous subprocess of the StateMachine.
 *
 * <p>Wiring:</p>
 * <ul>
 *   <li><strong>I13a</strong> in — {@link #delegate(int, String, Vec2)}: the SM
 *       hands an agent to a logical server <em>group</em>; the module picks the
 *       concrete server within the group (softmax). {@link #delegate(int, int)}
 *       is the lower-level form that targets a concrete server directly.</li>
 *   <li><strong>I13b</strong> out — {@link TargetSink}: fine targets — successive
 *       slots and service position for QUEUE; a random point inside the region
 *       for SEMAPHORE/CLASSROOM.</li>
 *   <li><strong>I13c</strong> out — {@link EventSink}: {@code serviceComplete}
 *       (released agent).</li>
 * </ul>
 *
 * <p>{@link #step(double, double, Map)} is meant to be driven by the
 * SimulationLoop once per timestep, with the current positions of the agents
 * (the module never moves agents itself). QUEUE servers detect arrivals by
 * distance; SEMAPHORE releases, while green, only the agents physically inside
 * its region (late arrivals wait for the next green); CLASSROOM releases on
 * schedule regardless of arrival.</p>
 */
public final class ServersModule {

    private final Map<Integer, Server> serversById = new LinkedHashMap<>();
    private final TargetSink targetSink;
    private final EventSink eventSink;
    private final ServiceTimeSampler sampler;
    private final ServersParameters params;

    /** All currently-delegated agents (across types), keyed by agent id. */
    private final Map<Integer, DelegatedAgent> agents = new HashMap<>();
    /** Per QUEUE server: FIFO list of waiting agent ids (index == slot index). */
    private final Map<Integer, List<Integer>> waiting = new HashMap<>();
    /** Per QUEUE server: the agent currently engaged (walking-to/in service), if any. */
    private final Map<Integer, Integer> serving = new HashMap<>();
    /** Per SEMAPHORE / CLASSROOM server: ids of agents inside the region. */
    private final Map<Integer, List<Integer>> insideRoom = new HashMap<>();
    /** Per CLASSROOM server: index of the next pending session in its {@code tInit[]}. */
    private final Map<Integer, Integer> nextSessionIdx = new HashMap<>();
    /** Logical group -> ordered list of member server ids (I13a group form). */
    private final Map<String, List<Integer>> serverIdsByGroup = new LinkedHashMap<>();

    private final SoftmaxServerAssigner assigner;
    private final double assignV0;
    private final double assignAlpha;

    /**
     * Convenience constructor with a default, seeded softmax assigner. Suitable
     * when groups are singletons (the assigner is never consulted) or when
     * reproducibility of the assignment is not critical.
     */
    public ServersModule(List<Server> servers, TargetSink targetSink, EventSink eventSink,
                         ServiceTimeSampler sampler, ServersParameters params) {
        this(servers, targetSink, eventSink, sampler, params,
                new SoftmaxServerAssigner(new Random(0), 1.0), 1.3, 1.0);
    }

    /**
     * @param assigner    policy that chooses a server within a group (I13a)
     * @param assignV0    desired walking speed used by the assigner's distance term
     * @param assignAlpha distance weight used by the assigner
     */
    public ServersModule(List<Server> servers, TargetSink targetSink, EventSink eventSink,
                         ServiceTimeSampler sampler, ServersParameters params,
                         SoftmaxServerAssigner assigner, double assignV0, double assignAlpha) {
        Objects.requireNonNull(servers, "servers must not be null");
        this.targetSink = Objects.requireNonNull(targetSink, "targetSink must not be null");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink must not be null");
        this.sampler = Objects.requireNonNull(sampler, "sampler must not be null");
        this.params = Objects.requireNonNull(params, "params must not be null");
        this.assigner = Objects.requireNonNull(assigner, "assigner must not be null");
        this.assignV0 = assignV0;
        this.assignAlpha = assignAlpha;
        for (Server s : servers) {
            if (serversById.put(s.id(), s) != null) {
                throw new IllegalArgumentException("duplicate server id " + s.id());
            }
            switch (s.type()) {
                case QUEUE -> waiting.put(s.id(), new ArrayList<>());
                case SEMAPHORE -> insideRoom.put(s.id(), new ArrayList<>());
                case CLASSROOM -> {
                    insideRoom.put(s.id(), new ArrayList<>());
                    nextSessionIdx.put(s.id(), 0);
                }
            }
            serverIdsByGroup.computeIfAbsent(s.group(), k -> new ArrayList<>()).add(s.id());
        }
    }

    // ----------------------------------------------------------------- I13a

    /**
     * I13a (group form): the SM delegates {@code agentId} to a logical server
     * {@code group} and we pick the concrete server inside it. With a single
     * member the choice is trivial; otherwise the softmax assigner weighs each
     * member's current load and its distance from {@code agentPos}. Once chosen,
     * the agent stays in that server (no jockeying between members).
     *
     * @param agentPos the agent's current position (for the distance term)
     * @return the id of the server the agent was delegated to
     * @throws IllegalArgumentException if the group is unknown
     * @throws IllegalStateException if the agent is already delegated
     */
    public int delegate(int agentId, String group, Vec2 agentPos) {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(agentPos, "agentPos must not be null");
        List<Integer> ids = serverIdsByGroup.get(group);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("unknown server group '" + group + "'");
        }
        int serverId;
        if (ids.size() == 1) {
            serverId = ids.get(0);
        } else {
            List<Server> candidates = new ArrayList<>(ids.size());
            for (int id : ids) {
                candidates.add(serversById.get(id));
            }
            serverId = assigner.assign(agentPos, candidates, this::occupancy,
                    params.meanServiceTime(), assignV0, assignAlpha);
        }
        delegate(agentId, serverId);
        return serverId;
    }

    /** Current load of a server: queue + in-service (QUEUE) or headcount (SEMAPHORE/CLASSROOM). */
    private int occupancy(Server s) {
        int id = s.id();
        if (s.type() == ServerType.QUEUE) {
            int q = waiting.getOrDefault(id, List.of()).size();
            return q + (serving.containsKey(id) ? 1 : 0);
        }
        return insideRoom.getOrDefault(id, List.of()).size();
    }

    /**
     * Delegates {@code agentId} to {@code serverId} (edge I13a, concrete form).
     * The agent immediately receives its first fine target via the
     * {@link TargetSink}.
     *
     * @throws IllegalArgumentException if the server is unknown
     * @throws IllegalStateException if the agent is already delegated
     */
    public void delegate(int agentId, int serverId) {
        Server s = serversById.get(serverId);
        if (s == null) {
            throw new IllegalArgumentException("unknown server id " + serverId);
        }
        if (agents.containsKey(agentId)) {
            throw new IllegalStateException("agent " + agentId + " is already delegated");
        }
        switch (s.type()) {
            case QUEUE -> {
                List<Integer> q = waiting.get(serverId);
                int slot = q.size();
                q.add(agentId);
                DelegatedAgent da =
                        new DelegatedAgent(agentId, serverId, ServerType.QUEUE, ServicePhase.WALKING_TO_SLOT);
                da.slotIdx = slot;
                agents.put(agentId, da);
                targetSink.sendTarget(agentId, slotPosition(s.queueLine(), slot));
            }
            case SEMAPHORE, CLASSROOM -> {
                insideRoom.get(serverId).add(agentId);
                agents.put(agentId, new DelegatedAgent(
                        agentId, serverId, s.type(), ServicePhase.WALKING_TO_SERVICE));
                targetSink.sendTarget(agentId, randomPointIn(s.serviceRegion()));
            }
        }
    }

    // ----------------------------------------------------------------- step

    /**
     * Advances every delegated agent, using the agents' current positions to
     * detect queue arrivals and semaphore region crossings (positions are
     * unused by CLASSROOM, whose releases are driven by simulation time alone).
     *
     * @param now       current simulation time [s]
     * @param dt        timestep [s] (reserved; the module is driven by {@code now})
     * @param positions current position of each agent (by id)
     */
    public void step(double now, double dt, Map<Integer, Vec2> positions) {
        Objects.requireNonNull(positions, "positions must not be null");
        for (Server s : serversById.values()) {
            switch (s.type()) {
                case QUEUE     -> stepQueueServer(s, now, positions);
                case SEMAPHORE -> stepSemaphoreServer(s, now, positions);
                case CLASSROOM -> stepClassroomServer(s, now, positions);
            }
        }
    }

    private void stepQueueServer(Server s, double now, Map<Integer, Vec2> positions) {
        int serverId = s.id();
        QueueLine ql = s.queueLine();
        List<Integer> q = waiting.get(serverId);
        double serviceMean = ((ServerConfig.Queue) s.config()).tMean();

        // 1. Service completion: release the engaged agent if its timer expired.
        Integer servingId = serving.get(serverId);
        if (servingId != null) {
            DelegatedAgent da = agents.get(servingId);
            if (da.phase == ServicePhase.IN_SERVICE && now >= da.serviceEndTime) {
                // Remove before notifying: the sink may synchronously re-delegate
                // the agent (the SM advances its plan inside serviceComplete), and
                // delegate() rejects agents still present in the maps.
                agents.remove(servingId);
                serving.remove(serverId);
                eventSink.serviceComplete(servingId);
            }
        }

        // 2. Geometric refresh: APPROACHING vs standing at the slot. The first
        // time an agent stands at its slot it "settles" into the line and we
        // emit arrivedAtPost (once per delegation: advancing slots later keeps
        // it settled). Notify after the loop: the sink must not see the queue
        // mid-mutation.
        List<Integer> settledNow = null;
        for (int i = 0; i < q.size(); i++) {
            int aid = q.get(i);
            DelegatedAgent da = agents.get(aid);
            da.slotIdx = i;
            Vec2 pos = positions.get(aid);
            Vec2 slot = slotPosition(ql, i);
            da.phase = (pos != null && pos.distanceTo(slot) <= params.arrivalThreshold())
                    ? ServicePhase.IN_QUEUE : ServicePhase.WALKING_TO_SLOT;
            if (da.phase == ServicePhase.IN_QUEUE && !da.settled) {
                da.settled = true;
                if (settledNow == null) {
                    settledNow = new ArrayList<>();
                }
                settledNow.add(aid);
            }
        }
        if (settledNow != null) {
            for (int aid : settledNow) {
                eventSink.arrivedAtPost(aid);
            }
        }

        // 3. Engage the head if the server is free and the head is standing.
        if (!serving.containsKey(serverId) && !q.isEmpty()) {
            int headId = q.get(0);
            DelegatedAgent head = agents.get(headId);
            if (head.phase == ServicePhase.IN_QUEUE) {
                q.remove(0);
                serving.put(serverId, headId);
                head.phase = ServicePhase.WALKING_TO_SERVICE;
                targetSink.sendTarget(headId, s.servicePosition());
                // The rest advanced one slot: emit their new targets.
                for (int i = 0; i < q.size(); i++) {
                    int aid = q.get(i);
                    agents.get(aid).slotIdx = i;
                    targetSink.sendTarget(aid, slotPosition(ql, i));
                }
            }
        }

        // 4. Service arrival: start the service timer once at the service spot.
        Integer sv = serving.get(serverId);
        if (sv != null) {
            DelegatedAgent da = agents.get(sv);
            if (da.phase == ServicePhase.WALKING_TO_SERVICE) {
                Vec2 pos = positions.get(sv);
                if (pos != null && pos.distanceTo(s.servicePosition()) <= params.arrivalThreshold()) {
                    da.phase = ServicePhase.IN_SERVICE;
                    da.serviceEndTime = now + sampler.sampleExponential(serviceMean);
                }
            }
        }
    }

    private void stepSemaphoreServer(Server s, double now, Map<Integer, Vec2> positions) {
        // Whoever steps on the waiting region for the first time settles
        // (arrivedAtPost), red or green alike.
        settleRoomArrivals(s.id(), s.serviceRegion(), positions);
        // Green: release only the agents physically standing inside the waiting
        // region (they crossed on green). Late arrivals enter as the region
        // frees up and are released on a later step if it is still green.
        // Red: hold everyone (no-op); they keep targeting the waiting area and
        // pile up around it (saturation is emergent from the OMs' repulsion).
        if (((ServerConfig.Semaphore) s.config()).isGreen(now)) {
            releaseInside(s.id(), s.serviceRegion(), positions);
        }
    }

    private void stepClassroomServer(Server s, double now, Map<Integer, Vec2> positions) {
        settleRoomArrivals(s.id(), s.serviceRegion(), positions);
        ServerConfig.Classroom cfg = (ServerConfig.Classroom) s.config();
        double[] tInit = cfg.tInit();
        double tMean = cfg.tMean();
        int idx = nextSessionIdx.get(s.id());
        while (idx < tInit.length && now >= tInit[idx] + tMean) {
            releaseAll(s.id());
            idx++;
        }
        nextSessionIdx.put(s.id(), idx);
    }

    /**
     * "Pisar la región" con tolerancia: dentro del rectángulo o hasta
     * {@code arrivalThreshold} de su borde. Las regiones de servicio que vienen
     * del escenario pueden ser chicas (p.ej. 1×1 m); con multitud, los agentes
     * se agolpan en el borde por la repulsión de los OMs y nunca quedan
     * estrictamente adentro. El margen (el mismo que usa la llegada a slot/
     * servicio en QUEUE) absorbe eso sin perder el sentido de "cruzar por la
     * senda" (la tolerancia es del orden del radio de un peatón).
     */
    private boolean steppedOn(Rectangle region, Vec2 p) {
        double m = params.arrivalThreshold();
        return p.x() >= region.minX() - m && p.x() <= region.maxX() + m
                && p.y() >= region.minY() - m && p.y() <= region.maxY() + m;
    }

    /**
     * Emits {@code arrivedAtPost} (once per delegation) for every agent of
     * {@code serverId} standing on {@code region} (with tolerance) that had not
     * settled yet. Used by SEMAPHORE/CLASSROOM; QUEUE settles per slot in
     * {@link #stepQueueServer}.
     */
    private void settleRoomArrivals(int serverId, Rectangle region, Map<Integer, Vec2> positions) {
        List<Integer> settledNow = null;
        for (int aid : insideRoom.get(serverId)) {
            DelegatedAgent da = agents.get(aid);
            Vec2 pos = positions.get(aid);
            if (!da.settled && pos != null && steppedOn(region, pos)) {
                da.settled = true;
                if (settledNow == null) {
                    settledNow = new ArrayList<>();
                }
                settledNow.add(aid);
            }
        }
        if (settledNow != null) {
            for (int aid : settledNow) {
                eventSink.arrivedAtPost(aid);
            }
        }
    }

    /**
     * Releases only the agents of {@code serverId} standing on {@code region}
     * (with {@code arrivalThreshold} tolerance — ver {@link #steppedOn}). The
     * rest stay delegated, keeping their current target. Used by SEMAPHORE: un
     * agente tiene que estar pisando el cruce mientras el semáforo está verde.
     */
    private void releaseInside(int serverId, Rectangle region, Map<Integer, Vec2> positions) {
        List<Integer> room = insideRoom.get(serverId);
        if (room.isEmpty()) {
            return;
        }
        // Same drain-before-notify discipline as releaseAll: collect the agents
        // to release, take them out of the live list and the maps first, and
        // only then notify (the sink may synchronously re-delegate them).
        List<Integer> released = new ArrayList<>();
        for (int aid : room) {
            Vec2 pos = positions.get(aid);
            if (pos != null && steppedOn(region, pos)) {
                released.add(aid);
            }
        }
        if (released.isEmpty()) {
            return;
        }
        room.removeAll(released);
        for (int aid : released) {
            agents.remove(aid);
        }
        for (int aid : released) {
            eventSink.serviceComplete(aid);
        }
    }

    /** Releases every agent delegated to {@code serverId}, wherever they are. */
    private void releaseAll(int serverId) {
        List<Integer> room = insideRoom.get(serverId);
        if (room.isEmpty()) {
            return;
        }
        // Drain before notifying: the sink may synchronously re-delegate an
        // agent (even back into this room), so the live list must not be
        // iterated nor the agents left in the maps while notifying.
        List<Integer> released = new ArrayList<>(room);
        room.clear();
        for (int aid : released) {
            agents.remove(aid);
        }
        for (int aid : released) {
            eventSink.serviceComplete(aid);
        }
    }

    private static Vec2 slotPosition(QueueLine ql, int slotIdx) {
        // Clamp overflow agents onto the last slot (queue beyond capacity).
        int clamped = Math.min(slotIdx, ql.maxSlots() - 1);
        return ql.slotPosition(clamped);
    }

    private Vec2 randomPointIn(Rectangle r) {
        return new Vec2(
                sampler.sampleUniform(r.minX(), r.maxX()),
                sampler.sampleUniform(r.minY(), r.maxY()));
    }

    // ------------------------------------------------------------- queries

    public boolean isDelegated(int agentId) {
        return agents.containsKey(agentId);
    }

    /**
     * Id del server (miembro concreto) al que está delegado {@code agentId}, o
     * {@code -1} si no está delegado. Lo usa el wiring (I13b) para ubicar el
     * target fino del server en la PLANTA de ese server (el módulo es planar en
     * {@code xy}; la {@code z} la agrega el wiring según el server delegado).
     */
    public int delegatedServerId(int agentId) {
        DelegatedAgent da = agents.get(agentId);
        return da == null ? -1 : da.serverId;
    }

    public int countDelegated() {
        return agents.size();
    }

    public List<Server> servers() {
        return Collections.unmodifiableList(new ArrayList<>(serversById.values()));
    }

    /**
     * Exposes every logical server <em>group</em> as a {@code core.ports.Server}
     * (I13a), for the {@link ar.edu.itba.simped.core.ports.Environment} assembly.
     * The SM addresses a group by {@link ar.edu.itba.simped.core.ports.Server#name()}
     * and each view's {@code delegate} routes back into this module, which then
     * picks the concrete member server.
     */
    public List<ar.edu.itba.simped.core.ports.Server> ports() {
        List<ar.edu.itba.simped.core.ports.Server> out = new ArrayList<>(serverIdsByGroup.size());
        for (Map.Entry<String, List<Integer>> e : serverIdsByGroup.entrySet()) {
            out.add(new ServerAdapter(e.getKey(), groupCentroid(e.getValue()), this));
        }
        return Collections.unmodifiableList(out);
    }

    /** Nominal position of a group: the centroid of its members' service positions. */
    private Vec2 groupCentroid(List<Integer> ids) {
        double sx = 0.0;
        double sy = 0.0;
        for (int id : ids) {
            Vec2 p = serversById.get(id).servicePosition();
            sx += p.x();
            sy += p.y();
        }
        return new Vec2(sx / ids.size(), sy / ids.size());
    }

    public Server getServer(int serverId) {
        return serversById.get(serverId);
    }
}
