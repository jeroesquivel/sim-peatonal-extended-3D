package ar.edu.itba.simped.simulation;

import ar.edu.itba.simped.core.AgentState;
import ar.edu.itba.simped.core.BehaviorState;
import ar.edu.itba.simped.core.Vec2;
import ar.edu.itba.simped.core.ports.Agent;
import ar.edu.itba.simped.core.ports.Environment;
import ar.edu.itba.simped.core.ports.OutputSink;
import ar.edu.itba.simped.core.ports.PedestrianGenerator;
import ar.edu.itba.simped.core.ports.SimulationDriver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Implementación del módulo 2 (SimulationLoop). Driver maestro que avanza
 * timesteps discretos de tamaño {@code dt} hasta {@code tTotal},
 * inicializa el {@link Environment} en t=0 (I4), dispara cada {@link Agent}
 * en cada dt (I3), y emite al {@link OutputSink} cada {@code dtOut} (I5).
 */
public final class SimulationDriverImpl implements SimulationDriver {

    private static final double TIME_EPSILON = 1e-9;

    private final Environment environment;
    private final OutputSink output;
    private final double dt;
    private final double dtOut;
    private final double tTotal;
    private final List<Agent> agents;
    private final ServerStep serverStep;
    private final IntConsumer onAgentRemoved;

    public SimulationDriverImpl(
            Environment environment,
            OutputSink output,
            double dt,
            double dtOut,
            double tTotal
    ) {
        this(environment, output, dt, dtOut, tTotal, null, null);
    }

    public SimulationDriverImpl(
            Environment environment,
            OutputSink output,
            double dt,
            double dtOut,
            double tTotal,
            ServerStep serverStep
    ) {
        this(environment, output, dt, dtOut, tTotal, serverStep, null);
    }

    public SimulationDriverImpl(
            Environment environment,
            OutputSink output,
            double dt,
            double dtOut,
            double tTotal,
            ServerStep serverStep,
            IntConsumer onAgentRemoved
    ) {
        if (dt <= 0.0) throw new IllegalArgumentException("dt must be positive");
        if (dtOut <= 0.0) throw new IllegalArgumentException("dtOut must be positive");
        if (tTotal <= 0.0) throw new IllegalArgumentException("tTotal must be positive");

        this.environment = environment;
        this.output = output;
        this.dt = dt;
        this.dtOut = dtOut;
        this.tTotal = tTotal;
        this.agents = new ArrayList<>();
        this.serverStep = serverStep;
        this.onAgentRemoved = onAgentRemoved != null ? onAgentRemoved : id -> {};
    }

    @Override
    public void run() {
        environment.init();                                 // I4
        PedestrianGenerator pg = environment.pedestrianGenerator();

        agents.addAll(pg.spawnInitial());
        registerInNeighborsIndex(agents);

        double t = 0.0;
        double nextOutputTime = 0.0;

        writeOutput(t);
        nextOutputTime += dtOut;

        while (t < tTotal - TIME_EPSILON) {
            List<Agent> spawned = pg.spawnTick(t, dt);
            if (!spawned.isEmpty()) {
                agents.addAll(spawned);
                registerInNeighborsIndex(spawned);
            }

            for (Agent agent : agents) {
                environment.neighbors().update(agent.state());   // I16
            }

            for (Agent agent : agents) {
                agent.step(dt);                                  // I3
            }

            if (serverStep != null) {
                serverStep.step(t, dt, agentPositions());        // 5.5: avanza Servers (I13b/I13c)
            }

            harvestFinished();

            t += dt;

            if (t + TIME_EPSILON >= nextOutputTime) {
                writeOutput(t);
                nextOutputTime += dtOut;
            }
        }
    }

    private Map<Integer, Vec2> agentPositions() {
        Map<Integer, Vec2> positions = new LinkedHashMap<>(agents.size());
        for (Agent agent : agents) {
            AgentState s = agent.state();
            positions.put(s.id(), new Vec2(s.x(), s.y()));
        }
        return positions;
    }

    private void registerInNeighborsIndex(List<Agent> newAgents) {
        for (Agent agent : newAgents) {
            environment.neighbors().update(agent.state());
        }
    }

    /**
     * Saca de la simulación los agentes que ya completaron su plan
     * (behavior == DEAD; ver {@link BehaviorState#DEAD} y la transición en
     * {@code StateMachineImpl.completeCurrentTask}).
     *
     * <p>Cleanup en tres pasos: (1) los removemos del loop para que no se
     * vuelvan a iterar, (2) los sacamos del CIM vía
     * {@code NeighborsIndex.remove(int)} para que no aparezcan como vecinos
     * fantasma, y (3) avisamos al wiring de App vía {@code onAgentRemoved}
     * para que limpie su {@code agentRegistry}; si no, los sinks de Servers
     * seguirían encontrando agentes "muertos".</p>
     */
    private void harvestFinished() {
        Iterator<Agent> it = agents.iterator();
        while (it.hasNext()) {
            Agent agent = it.next();
            if (agent.state().state() == BehaviorState.DEAD) {
                int id = agent.id();
                it.remove();
                environment.neighbors().remove(id);
                onAgentRemoved.accept(id);
            }
        }
    }

    private void writeOutput(double t) {
        List<AgentState> states = new ArrayList<>(agents.size());
        for (Agent agent : agents) {
            states.add(agent.state());
        }
        output.writeStep(t, states);                             // I5
    }
}
