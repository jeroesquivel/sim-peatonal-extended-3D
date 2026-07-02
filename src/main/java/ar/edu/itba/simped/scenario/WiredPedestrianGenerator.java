package ar.edu.itba.simped.scenario;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.PlanTemplate;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;
import ar.edu.itba.simped.environment.generator.PlanAwarePedestrianGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decorador sobre el {@link PedestrianGenerator} de g9: usa su lógica de
 * spawn (modos GROUP / CALM / BATCH_PULSES + grid positioning) pero envuelve
 * cada agente spawneado en un {@link ar.edu.itba.simped.agent.AgentImpl}
 * con el pipeline completo (Sensors → SM → PreOM → OM).
 *
 * <p>Mientras g9 no incorpore directamente la asambladura, este wrapper
 * actúa como bisagra entre el spawn y el container.</p>
 */
public final class WiredPedestrianGenerator implements PedestrianGenerator {

    private final PedestrianGenerator inner;
    private final AgentAssembler assembler;
    private final Map<Integer, Agent> registry;
    /** Contador de IDs compartido entre todos los generadores (zonas) para que
     *  no se pisen: cada ConfigurablePedestrianGenerator cuenta desde 0, así que
     *  remapeamos a un ID global único al wirear. */
    private final AtomicInteger idSource;

    public WiredPedestrianGenerator(
            PedestrianGenerator inner,
            AgentAssembler assembler,
            Map<Integer, Agent> registry,
            AtomicInteger idSource) {
        this.inner = inner;
        this.assembler = assembler;
        this.registry = registry;
        this.idSource = idSource;
    }

    @Override
    public List<Agent> spawnInitial() {
        return wire(inner.spawnInitial());
    }

    @Override
    public List<Agent> spawnTick(double currentTime, double dt) {
        return wire(inner.spawnTick(currentTime, dt));
    }

    private List<Agent> wire(List<Agent> raw) {
        List<Agent> wired = new ArrayList<>(raw.size());
        for (Agent a : raw) {
            Agent w = wireOne(a);
            registry.put(w.id(), w);   // para que los sinks de Servers (I13b/I13c) lo encuentren
            wired.add(w);
        }
        return wired;
    }

    private Agent wireOne(Agent a) {
        // El plan se busca con el ID LOCAL del sub-generador (planTemplateFor),
        // antes de remapear; después le damos un ID global único.
        PlanTemplate pt = null;
        if (inner instanceof PlanAwarePedestrianGenerator pag) {
            pt = pag.planTemplateFor(a.id());
        }
        AgentState remapped = remapId(a.state(), idSource.getAndIncrement());
        return pt != null ? assembler.wireAgent(remapped, pt) : assembler.wireAgent(remapped);
    }

    /** Copia el estado recién spawneado a uno con ID global único. El ID de
     *  {@link AgentState} es final, así que se construye uno nuevo y se copian
     *  los campos cinemáticos. */
    private static AgentState remapId(AgentState src, int newId) {
        AgentState s = new AgentState(newId, src.agentType());
        s.setPosition(src.x(), src.y(), src.z());
        s.setVelocity(src.vx(), src.vy());
        s.setRadius(src.radius());
        s.setState(src.state());
        if (src.profile() != null) {
            s.setProfile(src.profile());
        }
        return s;
    }
}
